package com.xinwenwang.hetcons;

import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.Config;
import com.xinwenwang.hetcons.config.HetconsConfig;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HetconsParticipantService extends CharlotteNodeService {

    private static final Logger logger = Logger.getLogger(HetconsParticipantService.class.getName());


    private HashMap<String, HetconsStatus> proposalStatusHashMap;
    private HashMap<String, HetconsObserverGroup> observerGroupHashMap;
    private HetconsConfig hetconsConfig;

    public  HetconsParticipantService(Config config, HetconsConfig hetconsConfig) {
        super(config);
        this.hetconsConfig = hetconsConfig;
        proposalStatusHashMap = new HashMap<>();
        observerGroupHashMap = new HashMap<>();
    }

    public HashMap<String, HetconsStatus> getProposalStatusHashMap() {
        return proposalStatusHashMap;
    }

    public HashMap<String, HetconsObserverGroup> getObserverGroupHashMap() {
        return observerGroupHashMap;
    }

    @Override
    public Iterable<SendBlocksResponse> onSendBlocksInput(SendBlocksInput input) {
        if (!input.hasBlock()) {
            //TODO: handle error
            return super.onSendBlocksInput(input);
        }

        Block block = input.getBlock();
        if (!block.hasHetconsMessage()) {
            //TODO: handle error
            return super.onSendBlocksInput(input);
        }

        HetconsMessage hetconsMessage = block.getHetconsMessage();
        HetconsObserverGroup group = hetconsMessage.getObserverGroup();

        try {
            switch (hetconsMessage.getType()) {
                case M1a:
                    if (hetconsMessage.hasM1A())
                        handle1a(hetconsMessage.getM1A(), group);
                    else
                        throw new HetconsException(HetconsErrorCode.NO_1A_MESSAGES);
                    break;
                case M1b:
                    if (hetconsMessage.hasM1B())
                        handle1b(hetconsMessage.getM1B(), hetconsMessage.getIdentity());
                    else
                        throw new HetconsException(HetconsErrorCode.NO_1B_MESSAGES);
                    break;
                case M2b:
                    if (hetconsMessage.hasM2B())
                        handle2b(hetconsMessage.getM2B(), hetconsMessage.getIdentity());
                    else
                        throw new HetconsException(HetconsErrorCode.NO_2B_MESSAGES);
                    break;
                case PROPOSAL:
//                    if (hetconsMessage.hasProposal())
//                        handleProposal(hetconsMessage.getProposal());
//                    else
//                        throw new HetconsException(HetconsErrorCode.NO_PROPOSAL);
//                    break;
                case UNRECOGNIZED:
                    throw new HetconsException(HetconsErrorCode.EMPTY_MESSAGE);
            }
        } catch (HetconsException ex) {
            ex.printStackTrace();
            return new ArrayList<>();
        }
        storeNewBlock(block);
        return new ArrayList<>();
    }

    /**
     * Handle 1a message
     * Set status
     * @param message1a
     */
    private void handle1a(HetconsMessage1a message1a, HetconsObserverGroup observerGroup) {
        if (!message1a.hasProposal())
            return;

        HetconsProposal proposal = message1a.getProposal();

        // echo 1As
        if (!handleProposal(proposal, observerGroup))
            return;

        HetconsStatus status = proposalStatusHashMap.get(buildAccountsInfoString(proposal.getSlotsList()));
        if (!status.hasMessage2a())
            send1bs(message1a, status);
        else
            send1bs(message1a, status);
    }

    private void handle1b(HetconsMessage1b message1b, CryptoId id) {

        logger.info("Got M1B:\n");

        // validate 1bs
        String statusKey = buildAccountsInfoString(message1b.getM1A().getProposal().getSlotsList());
        HetconsStatus status = proposalStatusHashMap.get(statusKey);

        if (!validateStatus(status, message1b.getM1A().getProposal(), false))
            return;

        // add this message to the 1b map
        ArrayList<HetconsObserverQuorum> quora = status.receive1b(id, message1b);

        if (quora.isEmpty())
            return;

        // prepare for 2a 2b
        HashMap<CryptoId, HetconsMessage2ab> message2abs = new HashMap<>();

        quora.forEach(quorum -> {
            message2abs.putIfAbsent(
                    quorum.getOwner(),
                    HetconsMessage2ab.newBuilder()
                            .setProposal(message1b.getM1A().getProposal())
                            .setValue(message1b.getValue())
                            .setQuorumOf1Bs(status.get1bQuorumRef(quorum))
                            .build());
        });

        // save a 2a
        // send out 2bs to observes and broadcast to all participants
        message2abs.forEach((cryptoId, message2ab) -> {
            status.addM2A(cryptoId, message2ab);
            Block block =  Block.newBuilder()
                    .setHetconsMessage(HetconsMessage.newBuilder()
                            .setIdentity(this.getConfig().getCryptoId())
                            .setSig(SignatureUtil.signBytes(this.getConfig().getKeyPair(), message2ab.toByteString()))
                            .setType(HetconsMessageType.M2b)
                            .setM2B(message2ab)
                            .build())
                    .build();

//            sendBlock(cryptoId,block);
            broadcastHetconsMessageBlocks(status, block);
        });

        status.setStage(HetconsConsensusStage.M2BSent);
        logger.info("Sent M2B:\n");
    }

    private void handle2b(HetconsMessage2ab message2b, CryptoId id) {
        logger.info(String.format("Server %s Got M2B\n", this.getConfig().getMe()));
        String statusKey = buildAccountsInfoString(message2b.getProposal().getSlotsList());
        HetconsStatus status = proposalStatusHashMap.get(statusKey);

        if (!validateStatus(status, message2b.getProposal(), false))
            return;

        ArrayList<HetconsObserverQuorum> quora = status.receive2b(id, message2b);

        if (quora.isEmpty())
            return;

        //TODO: Is handle 2b run in linear manner or parallel?

        logger.info(String.format("Server %s finished consensus\n", this.getConfig().getMe()));
        logger.info(String.format("Consensus decided on\nvalue: %d\n", message2b.getValue().getNum()));
        logger.info(String.format("Ballot Number: %d\n", message2b.getProposal().getBallot().getBallotNumber()));
        printConsensus(quora);
    }

    private void printConsensus(ArrayList<HetconsObserverQuorum> quorum) {
        for (int i = 0; i < quorum.size(); i++) {
            HetconsObserverQuorum q = quorum.get(i);
            System.out.printf("Quorum %d has receive message from %d members\n", i, q.getMemebersCount());
            q.getMemebersList().forEach(m -> {
                System.out.printf("\t%s\n", HetconsStatus.cryptoIdToString(m));
            });
        };
    }

    private void send1bs(HetconsMessage1a message1a, HetconsStatus status) {
        HetconsProposal proposal;

        for (HetconsObserver observer : status.getObserverGroup().getObserversList()) {
            HetconsMessage2ab message2a = status.getMessage2A(observer.getId());
            HetconsMessage1b message1b = HetconsMessage1b.newBuilder().setM1A(message1a)
                    .setM2A(message2a)
                    .setValue(message2a.hasProposal() ? message2a.getValue() : message1a.getProposal().getValue())
                    .build();
            Block block = Block.newBuilder().setHetconsMessage(
                    HetconsMessage.newBuilder()
                            .setType(HetconsMessageType.M1b)
                            .setM1B(message1b)
                            .setIdentity(this.getConfig().getCryptoId())
                            .setObserverGroup(status.getObserverGroup())
                            .setSig(SignatureUtil.signBytes(this.getConfig().getKeyPair(), message1b.toByteString()))
                            .build()
            ).build();
            broadcastHetconsMessageBlocks(status, block);
            //TODO: send 1bs
        }
        status.setStage(HetconsConsensusStage.M1BSent);
    }

    private boolean handleProposal(HetconsProposal proposal, HetconsObserverGroup observerGroup) {
        // validate proposal
        String proposalStatusID = buildAccountsInfoString(proposal.getSlotsList());
        String chainID = buildAccountsInfoString(proposal.getSlotsList(), false);
        HetconsStatus status = proposalStatusHashMap.get(proposalStatusID);
        if (status == null) {
            status = new HetconsStatus(HetconsConsensusStage.ConsensusIdile);
            status.setObserverGroup(observerGroup);
            proposalStatusHashMap.put(proposalStatusID, status);
//            if (!hetconsConfig.loadChain(chainID)) {
//                // TODO: init new chain config file if this is init message(ROOT BLOCK)
//                return false;
//            }
        }

        // TODO: LOCK?
        if (!validateStatus(status, proposal)) {
            //TODO: return error
            return false;
        }

        // setup & update status data
        HetconsMessage1a message1a = HetconsMessage1a.newBuilder()
                .setProposal(proposal).build();

        HetconsMessage message = HetconsMessage.newBuilder()
                .setM1A(message1a).setType(HetconsMessageType.M1a)
                .setIdentity(this.getConfig().getCryptoId())
                .setObserverGroup(status.getObserverGroup())
                .setSig(SignatureUtil.signBytes(this.getConfig().getKeyPair(), message1a.toByteString()))
                .build();
        Block block = Block.newBuilder().setHetconsMessage(message).build();

        status.updateProposal(proposal);
        status.setStage(HetconsConsensusStage.Proposed);

        // TODO: Look up quorums for this slots from the config data.

        // echo 1As to all participants
        broadcastHetconsMessageBlocks(status, block);

        status.setStage(HetconsConsensusStage.M1ASent);
        return true;
    }

    private String buildAccountsInfoString(List<IntegrityAttestation.ChainSlot> slots, boolean withSlot) {
        StringBuilder builder = new StringBuilder();
        for (IntegrityAttestation.ChainSlot slot: slots) {
            builder.append(slot.getRoot().getHash().getSha3().toStringUtf8());
            if (withSlot)
                builder.append("|" +Long.toString(slot.getSlot()));
        }
        return builder.toString();
    }

    private String buildAccountsInfoString(List<IntegrityAttestation.ChainSlot> slots) {
        return buildAccountsInfoString(slots, true);
    }

    private boolean validateStatus(HetconsStatus status, HetconsProposal proposal) {
        return validateStatus(status, proposal, true);
    }

    /**
     * Compare current proposal and new proposal  for the slot.
     *  1. current ballot number should be less than the new one.
     *  2. if time stamp is a future based on server time, then wait until that time to send.
     * Validate block data.
     * @param status
     * @param proposal
     * @return true if the proposal is valid and false otherwise.
     */
    private boolean validateStatus(HetconsStatus status, HetconsProposal proposal, boolean isM1A) {

        if (status == null)
            return false;

        HetconsProposal currentProposal = status.getCurrentProposal();

        //Consensus is not completed
        if (status.getStage() == HetconsConsensusStage.ConsensusDecided)
            return false;

        if (currentProposal != null && isM1A && (currentProposal.getBallot().getBallotNumber() >= proposal.getBallot().getBallotNumber()))
            return false;

        //TODO: validate block (Application specific)

        //TODO: validate timestamp

        return true;
    }

    private void sendHetconsMessageBlocks(HetconsObserverGroup observerGroup, Block block) {
        observerGroup.getObserversList().iterator().forEachRemaining(o -> {
            o.getQuorumsList().iterator().forEachRemaining(q -> {
                q.getMemebersList().iterator().forEachRemaining(m -> {
                    // TODO: Remove duplicated blocks by hashmap.
                    sendBlock(m, block);
                });
            });
        });
    }

    private void broadcastHetconsMessageBlocks(HetconsStatus status, Block block) {
        status.getParticipants().forEach((k, v) -> {
            sendBlock(status.getParticipantIds().get(k), block);
        });
    }

}
