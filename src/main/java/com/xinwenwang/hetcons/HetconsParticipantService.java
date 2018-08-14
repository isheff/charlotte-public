package com.xinwenwang.hetcons;

import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.Config;
import com.xinwenwang.hetcons.config.HetconsConfig;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class HetconsParticipantService extends CharlotteNodeService {


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
                        handle1b(hetconsMessage.getM1B(), group);
                    else
                        throw new HetconsException(HetconsErrorCode.NO_1B_MESSAGES);
                    break;
                case M2b:
                    if (hetconsMessage.hasM2B())
                        handle2b(hetconsMessage.getM2B(), group);
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
        }

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

        HetconsStatus status = proposalStatusHashMap.get(buildAccountsInfoString(proposal.getAccountsList()));
        if (!status.hasMessage2a())
            send1bs(message1a, HetconsMessage2ab.newBuilder().build());
        else
            send1bs(message1a, status.getLatestMessage2a());
    }

    private void handle1b(HetconsMessage1b message1b, HetconsObserverGroup observerGroup) {
        System.out.println("Got M1B:\n" + message1b.toString());

    }

    private void handle2b(HetconsMessage2ab message2ab, HetconsObserverGroup observerGroup) {
        System.out.println("Got M2B:\n" + message2ab.toString());

    }

    private void send1bs(HetconsMessage1a message1a, HetconsMessage2ab message2a) {
        HetconsProposal proposal = message1a.getProposal();
        HetconsStatus status = proposalStatusHashMap.get(buildAccountsInfoString(proposal.getAccountsList()));

        HetconsMessage1b message1b = HetconsMessage1b.newBuilder().setM1A(message1a)
                .setM2A(message2a)
                .build();
        Block block = Block.newBuilder().setHetconsMessage(
                HetconsMessage.newBuilder()
                        .setType(HetconsMessageType.M1b)
                        .setM1B(message1b)
                        .setObserverGroup(observerGroupHashMap.get(buildAccountsInfoString(proposal.getAccountsList(), false)))
                        .setSig(SignatureUtil.signBytes(this.getConfig().getKeyPair(), message1b.toByteString()))
                .build()
        ).build();
        sendHetconsMessageBlocks(block.getHetconsMessage().getObserverGroup(), block);
        status.setStage(HetconsConsensusStage.M1BSent);
    }

    private boolean handleProposal(HetconsProposal proposal, HetconsObserverGroup observerGroup) {
        // validate proposal
        String proposalStatusID = buildAccountsInfoString(proposal.getAccountsList());
        String chainID = buildAccountsInfoString(proposal.getAccountsList(), false);
        HetconsStatus status = proposalStatusHashMap.get(proposalStatusID);
        if (status == null) {
            status = new HetconsStatus(HetconsConsensusStage.ConsensusIdile);
            proposalStatusHashMap.put(proposalStatusID, status);
//            if (!hetconsConfig.loadChain(chainID)) {
//                // TODO: init new chain config file if this is init message(ROOT BLOCK)
//                return false;
//            }
        }

        if (!validateStatus(status, proposal)) {
            //TODO: return error
            return false;
        }

        // setup & update status data
        if (observerGroupHashMap.get(chainID) == null)
            observerGroupHashMap.put(chainID, observerGroup);

        HetconsMessage1a message1a = HetconsMessage1a.newBuilder()
                .setProposal(proposal).build();

        HetconsMessage message = HetconsMessage.newBuilder()
                .setM1A(message1a).setType(HetconsMessageType.M1a)
                .setObserverGroup(observerGroupHashMap
                        .get(buildAccountsInfoString(proposal.getAccountsList(), false)))
                .setSig(SignatureUtil.signBytes(this.getConfig().getKeyPair(), message1a.toByteString()))
                .build();
        Block block = Block.newBuilder().setHetconsMessage(message).build();

        status.updateProposal(proposal);
        status.setStage(HetconsConsensusStage.Proposed);

        // TODO: Look up quorums for this slots from the config data.

        // echo 1As to all participants
//        broadcastBlock(block);
        sendHetconsMessageBlocks(message.getObserverGroup(), block);

        status.setStage(HetconsConsensusStage.M1ASent);
        return true;
    }


    private String buildAccountsInfoString(List<HetconsParticipatedAccountsInfo> accountsInfos, boolean withSlot) {
        StringBuilder builder = new StringBuilder();
        for (HetconsParticipatedAccountsInfo info: accountsInfos) {
            builder.append(info.getChainHash().getSha3().toStringUtf8());
            if (withSlot)
                builder.append("|" +Long.toString(info.getSlot().getBlockSlotNumber()));
        }
        return builder.toString();
    }

    private String buildAccountsInfoString(List<HetconsParticipatedAccountsInfo> accountsInfos) {
        return buildAccountsInfoString(accountsInfos, true);
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
    private boolean validateStatus(HetconsStatus status, HetconsProposal proposal) {
        HetconsProposal currentProposal = status.getCurrentProposal();

        //Consensus is not completed
        if (status.getStage() == HetconsConsensusStage.ConsensusDecided)
            return false;

        if (currentProposal != null && (currentProposal.getBallotNumber() >= proposal.getBallotNumber()))
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

}
