package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.collections.BlockingMap;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.Config;

import java.nio.file.Path;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.List;

public class HetconsParticipantService extends CharlotteNodeService {


    private HashMap<String, HetconsStatus> proposalHashMap;
    private HashMap<String, HetconsObserverGroup> observerGroupHashMap;

    public HetconsParticipantService(Config config) {
        super(config);
    }

    public HetconsParticipantService(Path path) {
        super(path);
    }

    public HetconsParticipantService(String filename) {
        super(filename);
    }

    public HetconsParticipantService(BlockingMap<Hash, Block> blockMap, Config config) {
        super(blockMap, config);
    }


    @Override
    public Iterable<SendBlocksResponse> onSendBlocksInput(SendBlocksInput input) {
        if (!input.hasBlock()) {
            //TODO: handle error
        }

        Block block = input.getBlock();
        if (!block.hasHetconsMessage()) {
            //TODO: handle error
        }

        HetconsMessage hetconsMessage = block.getHetconsMessage();

        try {
            switch (hetconsMessage.getType()) {
                case M1a:
                    if (hetconsMessage.hasM1A())
                        handle1a(hetconsMessage.getM1A());
                    else
                        throw new HetconsException(HetconsErrorCode.NO_1A_MESSAGES);
                    break;
                case M1b:
                    if (hetconsMessage.hasM1B())
                        handle1b(hetconsMessage.getM1B());
                    else
                        throw new HetconsException(HetconsErrorCode.NO_1B_MESSAGES);
                    break;
                case M2b:
                    if (hetconsMessage.hasM2B())
                        handle2b(hetconsMessage.getM2B());
                    else
                        throw new HetconsException(HetconsErrorCode.NO_2B_MESSAGES);
                    break;
                case PROPOSAL:
                    if (hetconsMessage.hasProposal())
                        handleProposal(hetconsMessage.getProposal());
                    else
                        throw new HetconsException(HetconsErrorCode.NO_PROPOSAL);
                    break;
                case UNRECOGNIZED:
                    throw new HetconsException(HetconsErrorCode.EMPTY_MESSAGE);
            }
        } catch (HetconsException ex) {

        }


        return super.onSendBlocksInput(input);
    }

    /**
     * Handle 1a message
     * Set status
     * @param message1a
     */
    private void handle1a(HetconsMessage1a message1a) {

    }

    private void handle1b(HetconsMessage1b message1b) {

    }

    private void handle2b(HetconsMessage2ab message2ab) {}

    private void handleProposal(HetconsProposal proposal) {
        // validate proposal
        HetconsStatus status = proposalHashMap.get(buildAccountsInfoString(proposal.getAccountsList()));

        if (!validateStatus(status, proposal)) {
            //TODO: return error
        }

        // setup & update status data

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
        message.getObserverGroup().getObserversList().iterator().forEachRemaining(o -> {
            o.getQuorumsList().iterator().forEachRemaining(q -> {
                q.getMemebersList().iterator().forEachRemaining(m -> {
                    // TODO: Remove duplicated blocks by hashmap.
                    sendBlock(m, block);
                });
            });
        });
        status.setStage(HetconsConsensusStage.M1ASent);
    }

//    private byte[] prepareSign(byte[] messageByteString, List<HetconsObserverGroup> observerGroups, HetconsMessageType type) {
//
//    }

    private String buildAccountsInfoString(List<HetconsParticipatedAccountsInfo> accountsInfos, boolean withSlot) {
        StringBuilder builder = new StringBuilder();
        for (HetconsParticipatedAccountsInfo info: accountsInfos) {
            builder.append(info.getChainHash().getSha3().toString());
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

        if (currentProposal.getBallotNumber() > proposal.getBallotNumber())
            return false;

        //TODO: validate block (Application specific)

        //TODO: validate timestamp

        return true;
    }

}
