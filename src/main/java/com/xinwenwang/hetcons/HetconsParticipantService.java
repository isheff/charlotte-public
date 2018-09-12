package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.Config;
import com.xinwenwang.hetcons.config.HetconsConfig;

import java.util.*;
import java.util.logging.Logger;

public class HetconsParticipantService extends CharlotteNodeService {

    private static final Logger logger = Logger.getLogger(HetconsParticipantService.class.getName());

    private HashMap<String, HetconsObserverStatus> observerStatusMap;

    private HashMap<String, HetconsProposalStatus> proposalStatusHashMap;
    private HashMap<String, HetconsObserverGroup> observerGroupHashMap;
    private HashMap<String, HashMap<String, HetconsProposalStatus>> observerGroupEntry;
    private HetconsConfig hetconsConfig;

    public  HetconsParticipantService(Config config, HetconsConfig hetconsConfig) {
        super(config);
        this.hetconsConfig = hetconsConfig;
        proposalStatusHashMap = new HashMap<>();
        observerGroupHashMap = new HashMap<>();
        observerStatusMap = new HashMap<>();
    }

    public HashMap<String, HetconsProposalStatus> getProposalStatusHashMap() {
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

        if (this.getBlockMap().containsKey(HashUtil.sha3Hash(block)))
            return super.onSendBlocksInput(input);

        if (!block.hasHetconsMessage()) {
            //TODO: handle error
            return super.onSendBlocksInput(input);
        }

        HetconsMessage hetconsMessage = block.getHetconsMessage();

        try {
            switch (hetconsMessage.getType()) {
                case M1a:
                    if (hetconsMessage.hasM1A())
                        handle1a(hetconsMessage.getM1A(), block);
                    else
                        throw new HetconsException(HetconsErrorCode.NO_1A_MESSAGES);
                    break;
                case M1b:
                    if (hetconsMessage.hasM1B())
                        handle1b(hetconsMessage.getM1B(), hetconsMessage.getIdentity(), block);
                    else
                        throw new HetconsException(HetconsErrorCode.NO_1B_MESSAGES);
                    break;
                case M2b:
                    if (hetconsMessage.hasM2B())
                        handle2b(hetconsMessage.getM2B(), hetconsMessage.getIdentity(), block);
                    else
                        throw new HetconsException(HetconsErrorCode.NO_2B_MESSAGES);
                    break;
                case OBSERVERGROUP:
                    storeNewBlock(block);
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
        //storeNewBlock(block);
        return new ArrayList<>();
    }

    private void propose(String consensusId, HetconsValue value) {
        HetconsProposalStatus status = getStatus(consensusId);

        if (!status.getStage().equals(HetconsConsensusStage.HetconsTimeout)) {
            logger.warning("Called propose but the consensus is not timeout");
            return;
        }
        status.setStage(HetconsConsensusStage.ConsensusRestart);

        HetconsProposal current = status.getCurrentProposal();
        HetconsProposal proposal = HetconsUtil.buildProposal(current.getSlotsList(),
                value,
                HetconsUtil.buildBallot(value),
                current.getTimeout());

        status.updateStatus(proposal, status.getObserverGroup());

        HetconsMessage1a message1a = HetconsMessage1a.newBuilder()
                .setProposal(proposal).build();

        HetconsMessage message = HetconsMessage.newBuilder()
                .setM1A(message1a).setType(HetconsMessageType.M1a)
                .setSig(SignatureUtil.signBytes(this.getConfig().getKeyPair(), message1a.toByteString()))
                .setObserverGroupReferecne(status.getObserverGroupReference())
                .build();

        Block block = Block.newBuilder().setHetconsMessage(message).build();
        broadcastHetconsMessageBlocks(status, block);
        status.setStage(HetconsConsensusStage.M1ASent);

    }

    /**
     * Handle 1a message
     * Set status
     * @param message1a
     */
    private void handle1a(HetconsMessage1a message1a, Block block) {
        if (!message1a.hasProposal())
            return;

        HetconsProposal proposal = message1a.getProposal();
        HetconsObserverGroup observerGroup = block.getHetconsMessage().getObserverGroup();

//        if (!message1a.hasObserverGroup()) {
//            observerGroup = message1a.getObserverGroup();
//        } else if (!message1a.hasObserverGroupReferecne()) {
//            //FIXME: check pre-condition before assign
//            observerGroup = this.getBlock(message1a.getObserverGroupReferecne()).getHetconsMessage().getObserverGroup();
//        } else {
//            return;
//        }

        // echo 1As
        if (!handleProposal(proposal, observerGroup))
            return;

        HetconsProposalStatus status = getStatus(buildConsensusId(proposal.getSlotsList()));

        storeNewBlock(block);
        status.setObserverGroupReference(Reference.newBuilder().setHash(HashUtil.sha3Hash(block)).build());

        if (!status.hasMessage2a())
            send1bs(message1a, status);
        else
            send1bs(message1a, status);
        status.setStage(HetconsConsensusStage.M1BSent);

        // set timer for 1b, if we didn't receive enough 1bs after the timeout, we restart the consesus.
        if (status.getM1bTimer() != null)
            return;

        status.setM1bTimer(new Timer());
        status.getM1bTimer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (status.getStage().equals(HetconsConsensusStage.M1ASent)) {
                    logger.info("Restart consensus on " + status.getStage().toString());
                    status.setStage(HetconsConsensusStage.HetconsTimeout);
                    propose(buildConsensusId(status.getCurrentProposal().getSlotsList()),
                            status.getCurrentProposal().getValue());
                    status.setM1bTimer(null);
                }
            }
        },  status.getConsensuTimeout());
    }

    private void restartConsensus(String consensusId) {

    }

    private void handle1b(HetconsMessage1b message1b, CryptoId id, Block block) {

        logger.info("Got M1B:\n");

        // validate 1bs
        String statusKey = buildConsensusId(message1b.getM1A().getProposal().getSlotsList());
        HetconsProposalStatus status = getStatus(statusKey);

        if (!validateStatus(status, message1b.getM1A().getProposal(), false))
            return;

        if (!storeNewBlock(block))
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
            Block m2abblock =  Block.newBuilder()
                    .setHetconsMessage(HetconsMessage.newBuilder()
                            .setIdentity(this.getConfig().getCryptoId())
                            .setSig(SignatureUtil.signBytes(this.getConfig().getKeyPair(), message2ab.toByteString()))
                            .setType(HetconsMessageType.M2b)
                            .setM2B(message2ab)
                            .build())
                    .build();

//            sendBlock(cryptoId,block);
            broadcastHetconsMessageBlocks(status, m2abblock);
        });

        status.setStage(HetconsConsensusStage.M2BSent);
        logger.info("Sent M2B:\n");

        if (status.getM2bTimer() != null)
            return;

        status.setM2bTimer(new Timer());
        status.getM2bTimer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (status.getStage().equals(HetconsConsensusStage.M2BSent)) {
                    logger.info("Restart consensus on " + status.getStage().toString());
                    status.setStage(HetconsConsensusStage.HetconsTimeout);
                    propose(buildConsensusId(status.getCurrentProposal().getSlotsList()),
                            status.getHighestBallotM2A().getValue());
                    status.setM2bTimer(null);
                }
            }
        },  status.getConsensuTimeout());
    }

    private void handle2b(HetconsMessage2ab message2b, CryptoId id, Block block) {
        logger.info(String.format("Server %s Got M2B\n", this.getConfig().getMe()));
        String statusKey = buildConsensusId(message2b.getProposal().getSlotsList());
        HetconsProposalStatus status = getStatus(statusKey);

        if (!validateStatus(status, message2b.getProposal(), false))
            return;

        if (!storeNewBlock(block))
            return;
        ArrayList<HetconsObserverQuorum> quora = status.receive2b(id, message2b);

        if (quora.isEmpty())
            return;

        //TODO: Is handle 2b run in linear manner or parallel?

        status.setStage(HetconsConsensusStage.ConsensusDecided);
        String logInfo = "";
        logInfo += (String.format("Server %s finished consensus\n", this.getConfig().getMe()));
        logInfo += (String.format("Consensus decided on\nvalue: %d\n", message2b.getValue().getNum()));
        logInfo += (String.format("Ballot Number: %s\n", message2b.getProposal().getBallot().getBallotSequence()));
        logInfo += formatConsensus(quora);
        logger.info(logInfo);
        status.onDecided(statusKey);
    }

    private String formatConsensus(ArrayList<HetconsObserverQuorum> quorum) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < quorum.size(); i++) {
            HetconsObserverQuorum q = quorum.get(i);
            stringBuilder.append(String.format("Quorum %d has receive message from %d members\n", i, q.getMemebersCount()));
            q.getMemebersList().forEach(m -> {
               stringBuilder.append(String.format("\t%s\n", HetconsUtil.cryptoIdToString(m)));
            });
        };
        return stringBuilder.toString();
    }

    private void send1bs(HetconsMessage1a message1a, HetconsProposalStatus status) {
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
                            .setSig(SignatureUtil.signBytes(this.getConfig().getKeyPair(), message1b.toByteString()))
                            .setObserverGroupReferecne(status.getObserverGroupReference())
                            .build()
            ).build();
            broadcastHetconsMessageBlocks(status, block);
            //TODO: send 1bs
        }
    }

    private HetconsProposalStatus prepareForNewStatus(String prospoalStatusID) {
        HetconsProposalStatus status = new HetconsProposalStatus(HetconsConsensusStage.ConsensusIdile);
        HetconsProposalStatus old;
        if (!prospoalStatusID.contains("|"))
            return status;
        for (String individualConsensusID : prospoalStatusID.split("|")) {
               old = proposalStatusHashMap.get(individualConsensusID);
//               if (old == null) {
//                   old = new HetconsProposalStatus(HetconsConsensusStage.ConsensusIdile);
//                   old.setService(this);
//               }
               status.getChildren().put(individualConsensusID, old);
               old.setParent(status);
               proposalStatusHashMap.put(individualConsensusID, status);
        }
        return status;
    }

    private List<String> getSlotIDs(List<IntegrityAttestation.ChainSlot> slots) {
        ArrayList<String> slotIDs = new ArrayList<>();
        for (IntegrityAttestation.ChainSlot slot : slots) {
            slotIDs.add(slot.getRoot().getHash().getSha3().toStringUtf8()+"|"+slot.getSlot());
        }
        return slotIDs;
    }

    private boolean initialObserverStatus(List<String> slotIDs, HetconsObserverGroup observerGroup) {
        HetconsProposalStatus old;
        for (String slotID : slotIDs) {
            for (HetconsObserver hetconsObserver : observerGroup.getObserversList()) {
                this.observerGroupEntry.putIfAbsent(HetconsUtil.cryptoIdToString(hetconsObserver.getId()), new HashMap<String, HetconsProposalStatus>());
                old = this.observerGroupEntry.get(HetconsUtil.cryptoIdToString(hetconsObserver.getId())).get(slotID);
                if (old != null) {

                }
            }
        }
        return true;
    }

    private boolean handleProposal(HetconsProposal proposal, HetconsObserverGroup observerGroup) {
        // validate proposal
        String proposalStatusID = buildConsensusId(proposal.getSlotsList());
        String chainID = buildConsensusId(proposal.getSlotsList(), false);
        HetconsProposalStatus status = getStatus(proposalStatusID);
        if (status == null) {
            status = prepareForNewStatus(proposalStatusID);
            status.setConsensuTimeout(proposal.getTimeout());
            status.setObserverGroup(observerGroup);
            status.setService(this);
            status.setConsensusID(proposalStatusID);
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
        status.setStage(HetconsConsensusStage.ConsensusRestart);
        HetconsMessage1a message1a = HetconsMessage1a.newBuilder()
                .setProposal(proposal)
                .setObserverGroup(status.getObserverGroup())
                .build();

        HetconsMessage message = HetconsMessage.newBuilder()
                .setM1A(message1a).setType(HetconsMessageType.M1a)
                .setIdentity(this.getConfig().getCryptoId())
                .setSig(SignatureUtil.signBytes(this.getConfig().getKeyPair(), message1a.toByteString()))
                .build();
        Block block = Block.newBuilder().setHetconsMessage(message).build();

        status.updateStatus(proposal, observerGroup);
        status.setStage(HetconsConsensusStage.Proposed);

        // TODO: Look up quorums for this slots from the config data.

        // echo 1As to all participants
        broadcastHetconsMessageBlocks(status, block);

        status.setStage(HetconsConsensusStage.M1ASent);
        return true;
    }

    private String buildConsensusId(List<IntegrityAttestation.ChainSlot> slots, boolean withSlot) {
        StringBuilder builder = new StringBuilder();
        slots.sort(Comparator.comparing(o -> o.getBlock().getHash().getSha3().toStringUtf8()));
        for (IntegrityAttestation.ChainSlot slot: slots) {
            builder.append(slot.getRoot().getHash().getSha3().toStringUtf8());
            if (withSlot)
                builder.append("|").append(Long.toString(slot.getSlot()));
        }
        return builder.toString();
    }

    private String buildConsensusId(List<IntegrityAttestation.ChainSlot> slots) {
        return buildConsensusId(slots, true);
    }

    private boolean validateStatus(HetconsProposalStatus status, HetconsProposal proposal) {
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
    private boolean validateStatus(HetconsProposalStatus status, HetconsProposal proposal, boolean isM1A) {

        if (status == null)
            return false;

        HetconsProposal currentProposal = status.getCurrentProposal();

        //Consensus is not completed
        if (status.getStage() == HetconsConsensusStage.ConsensusDecided || status.getStage() == HetconsConsensusStage.ConsensusRestart)
            return false;

        if (currentProposal != null) {
            if (currentProposal.getBallot().getBallotSequence() == null) {
                if (isM1A && (currentProposal.getBallot().getBallotNumber() >= proposal.getBallot().getBallotNumber()))
                    return false;
            } else {
                if (isM1A && currentProposal.getBallot().getBallotSequence().compareTo(proposal.getBallot().getBallotSequence()) >= 0)
                    return false;
            }
        }




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

    private void broadcastHetconsMessageBlocks(HetconsProposalStatus status, Block block) {
        HetconsProposal proposal = status.getCurrentProposal();
        status.getLock().readLock().lock();
        try {
            HetconsProposal updatedProposal = status.getCurrentProposal();
            if (proposal.getBallot().getBallotSequence().compareTo(updatedProposal.getBallot().getBallotSequence()) < 0)
                return;
            status.getParticipants().forEach((k, v) -> {
                if (status.getParticipantIds().get(k) == null)
                    System.out.printf("k:%s\nsize: %d\n", k, status.getParticipantIds().size());
                sendBlock(status.getParticipantIds().get(k), block);
            });
        } finally {
            status.getLock().readLock().unlock();
        }
    }

    private HetconsProposalStatus getStatus(String proposalID) {
        HetconsProposalStatus status = this.proposalStatusHashMap.get(proposalID);
        return status == null ? null: status.getStatus();
    }
}
