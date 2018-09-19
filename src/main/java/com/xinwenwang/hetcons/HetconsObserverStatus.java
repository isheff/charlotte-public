package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.*;

import java.util.*;
import java.util.logging.Logger;

public class HetconsObserverStatus {

    // for which this observer
    private HetconsObserver observer;

    // map from proposal's consensus id to HetconsProposalStatus object. If a set of proposals are conflicting, then they should point to the same status

    private HashMap<String, HetconsProposalStatus> proposalStatus;

    // Map from a chain slot to a status object which is shared by all proposals related to that slot.
    private HashMap<String, HetconsSlotStatus> slotStatus;

    // What quorum this observer listens to.
    private List<List<CryptoId>> quorum;

    private HetconsParticipantService service;

    private static final Logger logger = Logger.getLogger(HetconsParticipantService.class.getName());

    public HetconsObserverStatus(HetconsObserver observer, HetconsParticipantService service) {
        this.observer = observer;
        this.service = service;
        proposalStatus = new HashMap<>();
        slotStatus = new HashMap<>();
        quorum = new ArrayList<>();
        for( HetconsObserverQuorum q : observer.getQuorumsList()) {
            quorum.add(q.getMemebersList());
        }
    }



    public boolean receive1a(Block block) {

        HetconsMessage1a m1a = block.getHetconsMessage().getM1A();

        HetconsProposal proposal = m1a.getProposal();

        String proposalStatusID = HetconsUtil.buildConsensusId(proposal.getSlotsList());

        ArrayList<String> chainIDs = new ArrayList<>();

        for (IntegrityAttestation.ChainSlot slot : proposal.getSlotsList()) {
            chainIDs.add(HetconsUtil.buildChainSlotID(slot));
        }

        HetconsProposalStatus incomingStatus = new HetconsProposalStatus(HetconsConsensusStage.Proposed,
                proposal,
                quorum,
                block.getHetconsMessage().getObserverGroupReferecne());
        incomingStatus.setChainIDs(chainIDs);
        proposalStatus.putIfAbsent(proposalStatusID, incomingStatus);
        HetconsProposalStatus currentStatus = proposalStatus.get(proposalStatusID);

        if (incomingStatus != currentStatus) {
            /* proposal with larger ballot number should be saved and use that number in the future */
            if (incomingStatus.getCurrentProposal().getBallot().getBallotSequence().compareTo(
                    currentStatus.getCurrentProposal().getBallot().getBallotSequence()) >= 0 &&
                    currentStatus.getStage() != HetconsConsensusStage.ConsensusDecided) {
                currentStatus.updateProposal(incomingStatus.getCurrentProposal());
            } else {
                return false;
            }
        }

        // Init status objects for chain ids
        for (String slot: chainIDs) {
            slotStatus.putIfAbsent(slot, new HetconsSlotStatus());
        }

        // See if we already have 2as
        for (String slot: chainIDs) {
            HetconsSlotStatus status = slotStatus.get(slot);
            if (status.has2aFromOtherProposal(proposalStatusID) || status.isDecided())
                return false;
        }

        // See if all slots have smaller ballot number
        for (String slot: chainIDs) {
            HetconsSlotStatus status = slotStatus.get(slot);
            if (status.hasLargerBallot(proposal.getBallot()))
                return false;
        }

        // Update ballot number for all slots
        for (String slot: chainIDs) {
            HetconsSlotStatus status = slotStatus.get(slot);
            status.updateBallot(proposal.getBallot());
        }

        // Save valid block
        service.storeNewBlock(block);

        // Echo 1a to all participants
        broadcastToParticipants(block);
        logger.info("Echo 1as");


        // Send 1b
        Reference m1aRef = Reference.newBuilder().setHash(
                HashUtil.sha3Hash(block)
        ).build();

        HetconsMessage1b m1b = prepareM1b(m1a, proposalStatusID);

        if (m1b == null)
            return false;

        HetconsMessage m = HetconsMessage.newBuilder()
                .setType(HetconsMessageType.M1b)
                .setM1B(m1b)
                .setObserverGroupReferecne(block.getHetconsMessage().getObserverGroupReferecne())
                .setIdentity(service.getConfig().getCryptoId())
                .setSig(
                        SignatureUtil.signBytes(service.getConfig().getKeyPair(), m1b)
                ).build();

        broadcastToParticipants(Block.newBuilder().setHetconsMessage(m).build());

        logger.info("Sent 1Bs");

        // set timer for 1b, if we didn't receive enough 1bs after the timeout, we restart the consensus.
        if (currentStatus.getM1bTimer() != null)
            return true;

        currentStatus.setM1bTimer(new Timer());
        currentStatus.getM1bTimer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (currentStatus.getStage().equals(HetconsConsensusStage.M1BSent) ||
                        currentStatus.getStage().equals(HetconsConsensusStage.M1ASent)) {
                    logger.info("Restart consensus on " + currentStatus.getStage().toString());
                    currentStatus.setStage(HetconsConsensusStage.HetconsTimeout);
                    restartProposal(proposalStatusID,
                            currentStatus.getCurrentProposal().getValue());
                    currentStatus.setM1bTimer(null);
                }
            }
        }, currentStatus.getConsensuTimeout());

        return true;
    }

    public void receive1b(Block block) {
        HetconsProposal proposal = block.getHetconsMessage().getM1B().getM1A().getProposal();
        String proposalID = HetconsUtil.buildConsensusId(proposal.getSlotsList());
        HetconsProposalStatus status = proposalStatus.get(proposalID);

        if (status == null)
            return;

        if (status.getCurrentProposal().getBallot().getBallotSequence().compareTo(
                proposal.getBallot().getBallotSequence()
        ) > 0) {
           return;
        }
        service.storeNewBlock(block);
        Reference refm1b = Reference.newBuilder().setHash(HashUtil.sha3Hash(block)).build();
        List<Reference> q = status.receive1b(block.getHetconsMessage().getIdentity(), refm1b);

        if (q == null)
            return;

        HetconsBallot ballot = null;
        HetconsValue value = null;
        HetconsMessage1a m1a = null;

        // CHeck if the return quorum is valid: same value, same ballot number
        for (Reference r: q) {
            Block b1b = service.getBlock(r);
            HetconsMessage1b m1b = b1b.getHetconsMessage().getM1B();
            m1a = m1b.getM1A();
            if (ballot == null && value == null) {
                ballot = m1b.getM1A().getProposal().getBallot();
                value = get1bValue(m1b);
            } else {
                if (HetconsUtil.ballotCompare(
                        m1b.getM1A().getProposal().getBallot(),
                        ballot) != 0 || !value.equals(get1bValue(m1b)))
                    return;
            }
        }

        // Save 2a to slot

        HetconsQuorumRefs refs = HetconsQuorumRefs.newBuilder().addAllBlockHashes(q).build();
        HetconsMessage2ab m2a = HetconsMessage2ab.newBuilder()
                .setM1A(m1a)
                .setQuorumOf1Bs(refs)
                .build();

        synchronized (slotStatus) {
            for (String slot: status.getChainIDs()) {
                HetconsSlotStatus slotStatus = this.slotStatus.get(slot);
                HetconsMessage2ab slot2a = slotStatus.getM2a();
                if (slot2a != null &&
                        HetconsUtil.ballotCompare(slot2a.getM1A().getProposal().getBallot(),
                                m2a.getM1A().getProposal().getBallot()) > 0) {
                    return;
                }
            }

            for (String slot: status.getChainIDs()) {
                HetconsSlotStatus slotStatus = this.slotStatus.get(slot);
                slotStatus.setM2a(m2a);
            }
        }


        // broadcast 2a
        HetconsMessage m2b = HetconsMessage.newBuilder()
                .setType(HetconsMessageType.M2b)
                .setM2B(m2a)
                .setObserverGroupReferecne(block.getHetconsMessage().getObserverGroupReferecne())
                .setSig(SignatureUtil.signBytes(service.getConfig().getKeyPair(), m2a))
                .setIdentity(service.getConfig().getCryptoId())
                .build();

        broadcastToParticipants(Block.newBuilder().setHetconsMessage(m2b).build());
        status.setStage(HetconsConsensusStage.M2BSent);


        /** -------------------- Timer for Restart ----------------------------- */
        // set timer for 2b, if we didn't receive enough 1bs after the timeout, we restart the consensus.
        if (status.getM1bTimer() != null)
            return;

        logger.info("Sent M2B:\n");

        status.setM2bTimer(new Timer());
        status.getM2bTimer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (status.getStage().equals(HetconsConsensusStage.M2BSent)) {
                    logger.info("Restart consensus on " + status.getStage().toString());
                    status.setStage(HetconsConsensusStage.HetconsTimeout);
                    restartProposal(proposalID, status.getCurrentProposal().getValue());
                    status.setM2bTimer(null);
                }
            }
        }, status.getConsensuTimeout());
    }

    public void receive2b(Block block) {
        HetconsProposal proposal = block.getHetconsMessage().getM2B().getM1A().getProposal();
        String proposalID = HetconsUtil.buildConsensusId(proposal.getSlotsList());
        HetconsProposalStatus status = proposalStatus.get(proposalID);

        if (status == null)
            return;

        if (status.getCurrentProposal().getBallot().getBallotSequence().compareTo(
                proposal.getBallot().getBallotSequence()
        ) > 0) {
            return;
        }
        service.storeNewBlock(block);
        Reference refm2b = Reference.newBuilder().setHash(HashUtil.sha3Hash(block)).build();
        List<Reference> q = status.receive2b(block.getHetconsMessage().getIdentity(), refm2b);

        if (q == null)
            return;

        HetconsBallot ballot = null;
        HetconsValue value = null;

        // CHeck if the return quorum is valid: same value, same ballot number
        for (Reference r: q) {
            Block b2b = service.getBlock(r);
            HetconsMessage2ab m2b = b2b.getHetconsMessage().getM2B();
            HetconsValue temp = get2bValue(m2b);
            if (ballot == null && value == null) {
                ballot = m2b.getM1A().getProposal().getBallot();
                value = get2bValue(m2b);
            } else {
                if (HetconsUtil.ballotCompare(
                        m2b.getM1A().getProposal().getBallot(),
                        ballot) != 0 || (temp != null && !value.equals(temp)))
                    return;
            }
        }

        // Now we can decided on this 2b value for that slot.
        synchronized (slotStatus) {
            for (String slotid: status.getChainIDs()) {
                HetconsSlotStatus slot = slotStatus.get(slotid);
                if (slot.isDecided() ||
                        HetconsUtil.ballotCompare(ballot, slot.getBallot()) < 0)
                    return;
            }
            for (String slotid: status.getChainIDs()) {
                slotStatus.get(slotid).decide(ballot, q, proposalID);
            }
        }

        status.setStage(HetconsConsensusStage.ConsensusDecided);

        System.out.println(formatConsensus(q));
    }

    public List<CryptoId> getParticipants() {
        ArrayList<CryptoId> participants = new ArrayList<>();
        this.quorum.forEach(participants::addAll);
        return participants;
    }

    /**
     * Given a 1a mesasge, if there is no 2a messages from other proposal then generate a 1b messages.
     * @param m1a
     * @param proposalID
     * @return
     */
    public HetconsMessage1b prepareM1b(HetconsMessage1a m1a, String proposalID) {

        HetconsMessage2ab max2a = null;
        for (String slotID : proposalStatus.get(proposalID).getChainIDs()) {
            HetconsSlotStatus status = this.slotStatus.get(slotID);
            HetconsMessage2ab message2ab = status.getM2a();
            if (message2ab != null &&
                    !proposalID.equals(HetconsUtil.buildConsensusId(message2ab.getM1A().getProposal().getSlotsList()))) {
                return null;
            } else {

                if (max2a == null)
                    max2a = message2ab;
                else {
                    if (message2ab != null) {
                        max2a = max2a.getM1A().getProposal().getBallot().getBallotSequence().compareTo(
                                message2ab.getM1A().getProposal().getBallot().getBallotSequence()
                        ) >= 0 ? max2a : message2ab;
                    }
                }
            }
        }

        HetconsMessage1b.Builder m1bBuilder = HetconsMessage1b.newBuilder()
                .setM1A(m1a);

        if (max2a != null)
            m1bBuilder.setM2A(max2a);

        return m1bBuilder.build();
    }

    private HetconsValue get1bValue(HetconsMessage1b m1b) {
        return m1b.hasM2A() ? m1b.getM2A().getM1A().getProposal().getValue() : m1b.getM1A().getProposal().getValue();
    }

    private HetconsValue get2bValue(HetconsMessage2ab m2b) {
        for (Reference r : m2b.getQuorumOf1Bs().getBlockHashesList()) {
            Block b2b = service.getBlock(r);
            if (b2b != null)
                return get1bValue(b2b.getHetconsMessage().getM1B());
        }
        return null;
    }

    /**
     * Re-submit a new proposal with for given value and proposal id.
     * Called when the previous round was timeout.
     * @param consensusId
     * @param value
     */
    private void restartProposal(String consensusId, HetconsValue value) {
        HetconsProposalStatus status = proposalStatus.get(consensusId);

        if (!status.getStage().equals(HetconsConsensusStage.HetconsTimeout)) {
//            logger.warning("Called propose but the consensus is not timeout");
            return;
        }
        status.setStage(HetconsConsensusStage.ConsensusRestart);

        HetconsProposal current = status.getCurrentProposal();
        HetconsProposal proposal = HetconsUtil.buildProposal(current.getSlotsList(),
                value,
                HetconsUtil.buildBallot(value),
                current.getTimeout());

        status.updateProposal(proposal);

        HetconsMessage1a message1a = HetconsMessage1a.newBuilder()
                .setProposal(proposal).build();

        HetconsMessage message = HetconsMessage.newBuilder()
                .setM1A(message1a).setType(HetconsMessageType.M1a)
                .setIdentity(service.getConfig().getCryptoId())
                .setSig(SignatureUtil.signBytes(service.getConfig().getKeyPair(), message1a.toByteString()))
                .setObserverGroupReferecne(status.getObserverGroupReference())
                .build();

        Block block = Block.newBuilder().setHetconsMessage(message).build();
        broadcastToParticipants(block);
        status.setStage(HetconsConsensusStage.M1ASent);

    }

    private void broadcastToParticipants(Block block) {
        getParticipants().forEach(p -> {
            service.sendBlock(p, block);
        });
    }


    public HetconsObserver getObserver() {
        return observer;
    }

    public HashMap<String, HetconsProposalStatus> getProposalStatus() {
        return proposalStatus;
    }

    public List<List<CryptoId>> getQuorum() {
        return quorum;
    }

    private String formatConsensus(List<Reference> m2bs) {
        StringBuilder stringBuilder = new StringBuilder();
        HetconsMessage2ab m2b = service.getBlock(m2bs.get(0)).getHetconsMessage().getM2B();
        stringBuilder.append(String.format("A quorum of %d messages for Observer %s have been received\n\nDecided on value: %s\nForChain: %s\n\nBallot:%s\n\n",
                m2bs.size(), HetconsUtil.cryptoIdToString(observer.getId()),
                get2bValue(m2b), HetconsUtil.buildConsensusId(m2b.getM1A().getProposal().getSlotsList()),m2b.getM1A().getProposal().getBallot().getBallotSequence()));
        for (int i = 0; i < m2bs.size(); i++) {
            Reference r = m2bs.get(i);
            stringBuilder.append(String.format("\t%s\n", HetconsUtil.bytes2Hex(r.getHash().getSha3().toStringUtf8().getBytes())));
        }
        return stringBuilder.toString();
    }


}
