package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.*;
import io.netty.util.Timeout;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class HetconsObserverStatus {

    /*for which this observer */
    private HetconsObserver observer;

    /* map from proposal's consensus id to HetconsProposalStatus object. If a set of proposals are conflicting, then they should point to the same status */
    private HashMap<String, HetconsProposalStatus> proposalStatus;

    /* map from a chain slot to a status object which is shared by all proposals related to that slot. */
    private HashMap<String, HetconsSlotStatus> slotStatus;

    /* The service used to send and receive blocks */
    private HetconsParticipantService service;

    /* The number of decided proposals for logging purposes */
    private Integer numOfDecidedProposals = 0;

    /* this stores the quorum configuration for chains, the key is the name of the chain */
    private Map<String, HetconsQuorumStatus> quorums;

    /* Observer's name */
    private String name;

    private static final Logger logger = Logger.getLogger(CharlotteNodeService.class.getName());


    public HetconsObserverStatus(HetconsObserver observer, HetconsParticipantService service, String name) {
        this.name = name;
        this.observer = observer;
        this.service = service;
        proposalStatus = new HashMap<>();
        slotStatus = new HashMap<>();
        quorums = new HashMap<>();
    }


    public boolean receive1a(Block block,
                             long timeout,
                             List<HetconsObserverQuorum> observerQuorums,
                             String chainName) {

        HetconsMessage1a m1a = block.getHetconsMessage().getM1A();

        HetconsProposal proposal = m1a.getProposal();

        String proposalStatusID = HetconsUtil.buildConsensusId(proposal.getSlotsList());

        ArrayList<String> chainIDs = new ArrayList<>();

        for (IntegrityAttestation.ChainSlot slot : proposal.getSlotsList()) {
            chainIDs.add(HetconsUtil.buildChainSlotID(slot));
        }

        quorums.put(chainName, new HetconsQuorumStatus(observerQuorums, chainName));

        HetconsProposalStatus incomingStatus = new HetconsProposalStatus(HetconsConsensusStage.Proposed,
                proposal,
                quorums.get(chainName),
                block.getHetconsMessage().getObserverGroupReferecne(),
                quorums);
        incomingStatus.setChainIDs(chainIDs);
        boolean hasPrev = null != proposalStatus.putIfAbsent(proposalStatusID, incomingStatus);
        HetconsProposalStatus currentStatus = proposalStatus.get(proposalStatusID);
        if (!hasPrev) {
            currentStatus.setProposer(timeout != 0);
            currentStatus.setConsensuTimeout(timeout);
        }


        if (incomingStatus != currentStatus) {
            /* proposal with larger ballot number should be saved and use that number in the future */
            if (!(incomingStatus.getCurrentProposal().getBallot().getBallotSequence().compareTo(
                    currentStatus.getCurrentProposal().getBallot().getBallotSequence()) >= 0)) {
                return false;
            }
        }


        synchronized (currentStatus.getGeneralLock()) {
            if (currentStatus.getHasDecided())
                logger.info("Duplicated Request: Slot " + proposal.getSlotsList() + " has been decided\nvalue is ");
        }

        List<HetconsSlotStatus> slotStatuses = new ArrayList<>();
        synchronized (slotStatus) {
            // Init status objects for chain ids
            for (String slot : chainIDs) {
                slotStatus.putIfAbsent(slot, new HetconsSlotStatus());
                HetconsSlotStatus status = slotStatus.get(slot);
                slotStatuses.add(status);
            }
        }

        /* See if we already have 2as from another independent proposals */
        // FIXME: update ballot number instead of discarding the proposal
        for (HetconsSlotStatus status : slotStatuses) {
            synchronized (status) {
                if (status.has2aFromOtherProposal(proposalStatusID, this)) {
                    status.updateBallot(proposal.getBallot());
                    return false;
                }
            }
        }

        // See if all slots have smaller ballot number
        for (HetconsSlotStatus status : slotStatuses) {
            synchronized (status) {
                if (status.hasLargerBallot(proposal.getBallot()))
                    return false;
            }
        }

        // Update ballot number for all slots
        for (HetconsSlotStatus status : slotStatuses) {
            synchronized (status) {
                status.updateBallot(proposal.getBallot());
            }
        }
        currentStatus.updateProposal(incomingStatus.getCurrentProposal());

        // Save valid block
        // Echo 1a to all participants
        service.storeNewBlock(block);
        broadcastToParticipants(block, currentStatus.getParticipants());


        logger.info(name + ": Echo 1as value is " + proposal.getValue());

        // Send 1b
        // FIXME: use reference
        Reference m1aRef = Reference.newBuilder().setHash(
                HashUtil.sha3Hash(block)
        ).build();

        HetconsMessage1b m1b = prepareM1b(m1a, m1aRef, proposalStatusID);

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

        broadcastToParticipants(Block.newBuilder().setHetconsMessage(m).build(), currentStatus.getParticipants());
        currentStatus.setStage(HetconsConsensusStage.M1BSent);

        logger.info("Sent 1Bs value is " + HetconsUtil.get1bValue(m1b, service) + " " + proposalStatusID);
        logger.info("ballot is " + proposal.getBallot().getBallotSequence());

        if (!currentStatus.getProposer())
            return true;

        // set timer for 1b, if we didn't receive enough 1bs after the timeout, we restart the consensus.
        synchronized (currentStatus.getGeneralLock()) {

            if (currentStatus.getTimer() == null)
                currentStatus.setTimer(Executors.newSingleThreadExecutor());

            if (currentStatus.getM1bTimer() != null)
                currentStatus.getM1bTimer().cancel(true);

            if (currentStatus.getM2bTimer() != null)
                currentStatus.getM2bTimer().cancel(true);

            Future<?> m1bTimer = currentStatus.getTimer().submit(() -> {
                logger.info(name + ": M1B TIMER: Will sleep for " + currentStatus.getConsensuTimeout() + " milliseconds for timeout");
                try {
                    TimeUnit.MILLISECONDS.sleep(currentStatus.getConsensuTimeout());
                } catch (InterruptedException ex) {
                    logger.info(name + ": M1B Timer Cancelled");
                    return;
                }
                if (Thread.interrupted())
                    return;
                synchronized (currentStatus.getGeneralLock()) {
                    if (currentStatus.getHasDecided())
                        return;
                    if (currentStatus.getStage().equals(HetconsConsensusStage.M1BSent) ||
                            currentStatus.getStage().equals(HetconsConsensusStage.M1ASent)) {
                        logger.info("Timer 1: Restart consensus on " + currentStatus.getStage().toString() +
                                " for value " + currentStatus.getCurrentProposal().getValue());
                        currentStatus.setStage(HetconsConsensusStage.HetconsTimeout);
                        restartProposal(proposalStatusID,
                                currentStatus.getRecent1b());
                    }
                }
            });
            currentStatus.setM1bTimer(m1bTimer);
        }
        return true;
    }


    public void receive1b(Block block) {
        HetconsMessage1a message1a = getM1aFromReference(block.getHetconsMessage().getM1B().getM1ARef());
        if (message1a == null) {
            return;
        }
        HetconsProposal proposal = message1a.getProposal();
        String proposalID = HetconsUtil.buildConsensusId(proposal.getSlotsList());
        HetconsProposalStatus status = proposalStatus.get(proposalID);

        if (status == null) {
            logger.info("No proposal status available for proposalID: "+proposalID);
            return;
        }

        if (status.getCurrentProposal().getBallot().getBallotSequence().compareTo(
                proposal.getBallot().getBallotSequence()
        ) > 0) {
            logger.info(name + ":" + "M1B discard due to lower ballot for value "+HetconsUtil.get1bValue(block.getHetconsMessage().getM1B(), service));
            return;
        }
//        service.storeNewBlock(block);
        Reference refm1b = Reference.newBuilder().setHash(HashUtil.sha3Hash(block)).build();
        List<Reference> q = status.receive1b(block.getHetconsMessage().getIdentity(), refm1b);
        status.updateRecent1b(block.getHetconsMessage().getM1B().hasM2A(), HetconsUtil.get1bValue(block.getHetconsMessage().getM1B(), service), message1a.getProposal().getBallot());

        logger.info(name + ":" + "M1B value is " + HetconsUtil.get1bValue(block.getHetconsMessage().getM1B(), service));

        if (q == null) {
            logger.info("M1B: No quorum is satisfied");
            return;
        }

        HetconsBallot ballot = null;
        HetconsValue value = null;
        HetconsMessage1a m1a = null;
        Reference m1aRef = null;

        // CHeck if the return quorum is valid: same value, same ballot number
        for (Reference r: q) {
            Block b1b = service.getBlock(r);
            HetconsMessage1b m1b = b1b.getHetconsMessage().getM1B();
            m1a = getM1aFromReference(m1b.getM1ARef());
            m1aRef = m1b.getM1ARef();
            if (ballot == null && value == null) {
                ballot = m1a.getProposal().getBallot();
                value = get1bValue(m1b);
            } else {
                if (HetconsUtil.ballotCompare(
                        m1a.getProposal().getBallot(),
                        ballot) != 0 || !value.equals(get1bValue(m1b)))
                    return;
            }
        }

        logger.info(name + ": found a quorum");

        // Save 2a to slot

        HetconsQuorumRefs refs = HetconsQuorumRefs.newBuilder().addAllBlockHashes(q).build();
        HetconsMessage2ab m2a = HetconsMessage2ab.newBuilder()
                .setM1ARef(m1aRef)
                .setQuorumOf1Bs(refs)
                .build();


        int decidedCount = 0;
        for (String slot: status.getChainIDs()) {
            HetconsSlotStatus sstatus = this.slotStatus.get(slot);
            synchronized (sstatus) {
                if (sstatus.isDecided() && !sstatus.getActiveProposal().equals(proposalID))
                    return;
                if (sstatus.isDecided())
                    decidedCount++;
            }
        }

        // If there is any decided slot but not all of them, then return
        if (decidedCount > 0 && decidedCount != status.getChainIDs().size())
            return;

        for (String slot: status.getChainIDs()) {
            HetconsSlotStatus sstatus = this.slotStatus.get(slot);
            synchronized (sstatus) {
                HetconsMessage2ab slot2a = sstatus.getM2a();
                if (slot2a != null && HetconsUtil.ballotCompare(getM1aFromReference(slot2a.getM1ARef()).getProposal().getBallot(),
                                getM1aFromReference(m2a.getM1ARef()).getProposal().getBallot()) > 0) {
                    return;
                }
            }
        }

        for (String slot: status.getChainIDs()) {
            HetconsSlotStatus slotStatus = this.slotStatus.get(slot);
            // only do updates if the slot has not decided yet.
            synchronized (slotStatus) {
                if (!slotStatus.isDecided()) {
                    slotStatus.setM2a(m2a, this);
                }
            }
        }

        logger.info(name + "Wrote to m2a");

        // broadcast 2a
        HetconsMessage m2b = HetconsMessage.newBuilder()
                .setType(HetconsMessageType.M2b)
                .setM2B(m2a)
                .setObserverGroupReferecne(block.getHetconsMessage().getObserverGroupReferecne())
                .setSig(SignatureUtil.signBytes(service.getConfig().getKeyPair(), m2a))
                .setIdentity(service.getConfig().getCryptoId())
                .build();

        broadcastToParticipants(Block.newBuilder().setHetconsMessage(m2b).build(), status.getParticipants());
        status.setStage(HetconsConsensusStage.M2BSent);

        if (!status.getProposer())
            return;

        /** -------------------- Timer for Restart ----------------------------- */
        // set timer for 2b, if we didn't receive enough 1bs after the timeout, we restart the consensus.

        logger.info(name + ":" + "Sent M2B value is "+ HetconsUtil.get2bValue(m2a, service));

        synchronized (status.getGeneralLock()) {

            if (status.getTimer() == null)
                status.setTimer(Executors.newSingleThreadExecutor());

            if (status.getM1bTimer() != null) {
                status.getM1bTimer().cancel(true);
            }

            if (status.getM2bTimer() != null) {
                status.getM2bTimer().cancel(true);
            }

            if (status.getHasDecided() || !status.getProposer())
                return;

            status.setM2bTimer(status.getTimer().submit(() -> {
                logger.info("M2B TIMER: Will sleep for " + status.getConsensuTimeout() + " milliseconds for timeout");
                try {
                    TimeUnit.MILLISECONDS.sleep(status.getConsensuTimeout());
                } catch (InterruptedException ex) {
                    logger.info(name + ": M2B Timer Cancelled");
                    return;
                }
                if (Thread.interrupted())
                    return;
                synchronized (status.getGeneralLock()) {
                    if (status.getHasDecided())
                        return;
                    if (status.getStage().equals(HetconsConsensusStage.M2BSent)) {
                        logger.info("Timer 2: Restart consensus on " + status.getStage().toString() + " for value "
                                + status.getCurrentProposal().getValue());
                        status.setStage(HetconsConsensusStage.HetconsTimeout);
                        restartProposal(proposalID, status.getRecent2b());
                        status.setM2bTimer(null);
                    }
                }
            }));
        }
    }

    public void receive2b(Block block) {
        HetconsMessage1a message1a = getM1aFromReference(block.getHetconsMessage().getM2B().getM1ARef());
        if (message1a == null)
            return;
        HetconsProposal proposal = message1a.getProposal();
        String proposalID = HetconsUtil.buildConsensusId(proposal.getSlotsList());
        HetconsProposalStatus status = proposalStatus.get(proposalID);

        if (status == null) {
            return;
        }
//        if (status == null || status.getStage() == HetconsConsensusStage.ConsensusDecided)

        for (Reference reference : block.getHetconsMessage().getM2B().getQuorumOf1Bs().getBlockHashesList()) {
            service.getBlock(reference);
        }

        logger.info(name + ":"+ "Got M2B: value is " + HetconsUtil.get2bValue(block.getHetconsMessage().getM2B(), service));
        if (status.getCurrentProposal().getBallot().getBallotSequence().compareTo(
                proposal.getBallot().getBallotSequence()
        ) > 0) {
            logger.info(name + ":" + "M2B discard because of lower ballot number value is " + HetconsUtil.get2bValue(block.getHetconsMessage().getM2B(), service));
            return;
        }

        Reference refm2b = Reference.newBuilder().setHash(HashUtil.sha3Hash(block)).build();
        HashMap m = status.receive2b(block.getHetconsMessage().getIdentity(), refm2b);
        status.updateRecent2b(HetconsUtil.get2bValue(block.getHetconsMessage().getM2B(), service), message1a.getProposal().getBallot());

        if (m == null) {
            logger.info("No quorum is satisfied");
            return;
        }

        List<Reference> q = (List<Reference>)m.get("references");
        List<CryptoId> p = (List<CryptoId>) m.get("participants");
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
                ballot = getM1aFromReference(m2b.getM1ARef()).getProposal().getBallot();
                value = get2bValue(m2b);
            } else {
                if (HetconsUtil.ballotCompare(
                        getM1aFromReference(m2b.getM1ARef()).getProposal().getBallot(),
                        ballot) != 0 || (temp != null && !value.equals(temp)))
                    return;
            }
        }

        // Now we can decided on this 2b value for that slot.
        synchronized (status.getGeneralLock()) {

            if (status.getTimer() != null) {
                status.getTimer().shutdownNow();
                try {
                    status.getTimer().awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                } finally {
                    status.setTimer(null);
                }
            }

            for (String slotid: status.getChainIDs()) {
                HetconsSlotStatus slot = slotStatus.get(slotid);
                if (slot.isDecided()) {
                    logger.info("Slot has been decided on value ");
                    return;
                }
                if (HetconsUtil.ballotCompare(ballot, slot.getBallot()) < 0) {
                    logger.info("Ballot number is smaller than the one slot has");
                    return;
                }
            }
            for (String slotid: status.getChainIDs()) {
                slotStatus.get(slotid).decide(ballot, q, proposalID);
            }
            numOfDecidedProposals ++;

            status.setStage(HetconsConsensusStage.ConsensusDecided);
            status.setHasDecided(true);
            status.setDecidedQuorum(q);
            status.setDecidedValue(HetconsUtil.get2bValue(block.getHetconsMessage().getM2B(), service));
        }


        logger.info(formatConsensus(q));

        logger.info(name + ":" + "Thread: " + Thread.currentThread().getId() + " There are " + numOfDecidedProposals + " proposals have been decided");



        HetconsObserverQuorum observerQuorum = HetconsObserverQuorum.newBuilder().setOwner(observer.getId())
                .addAllMembers(p)
                .build();
        service.onDecision(observerQuorum, q);
    }

    public List<CryptoId> getParticipants() {
        ArrayList<CryptoId> participants = new ArrayList<>();
//        this.quorum.forEach(participants::addAll);
        return participants;
    }

    /**
     * Given a 1a message, if there is no 2a messages from other proposal then generate a 1b messages.
     * @param m1a
     * @param proposalID
     * @return
     */
    public HetconsMessage1b prepareM1b(HetconsMessage1a m1a, Reference m1aRef, String proposalID) {

        HetconsMessage2ab max2a = null;
        for (String slotID : proposalStatus.get(proposalID).getChainIDs()) {
            HetconsSlotStatus status = this.slotStatus.get(slotID);
            HetconsMessage2ab message2ab = status.getM2a();
            if (message2ab != null && // If m2a exists then m1a must exist
                    !proposalID.equals(HetconsUtil.buildConsensusId(getM1aFromReference(message2ab.getM1ARef()).getProposal().getSlotsList()))) {
                return null;
            } else {
                if (max2a == null)
                    max2a = message2ab;
                else {
                    if (message2ab != null) {
                        max2a = getM1aFromReference(max2a.getM1ARef()).getProposal().getBallot().getBallotSequence().compareTo(
                                getM1aFromReference(message2ab.getM1ARef()).getProposal().getBallot().getBallotSequence()
                        ) >= 0 ? max2a : message2ab;
                    }
                }
            }
        }

        HetconsMessage1b.Builder m1bBuilder = HetconsMessage1b.newBuilder()
                .setM1ARef(m1aRef);

        if (max2a != null)
            m1bBuilder.setM2A(max2a);

        return m1bBuilder.build();
    }

    private HetconsValue get1bValue(HetconsMessage1b m1b) {
        return m1b.hasM2A() ? getM1aFromReference(m1b.getM2A().getM1ARef()).getProposal().getValue() : getM1aFromReference(m1b.getM1ARef()).getProposal().getValue();
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
                0);

//        status.updateProposal(proposal);

        HetconsMessage1a message1a = HetconsMessage1a.newBuilder()
                .setProposal(proposal).build();

        HetconsMessage message = HetconsMessage.newBuilder()
                .setM1A(message1a).setType(HetconsMessageType.M1a)
                .setIdentity(service.getConfig().getCryptoId())
                .setSig(SignatureUtil.signBytes(service.getConfig().getKeyPair(), message1a.toByteString()))
                .setObserverGroupReferecne(status.getObserverGroupReference())
                .build();

        Block block = Block.newBuilder().setHetconsMessage(message).build();
        broadcastToParticipants(block, status.getParticipants());
//        status.setStage(HetconsConsensusStage.M1ASent);

    }

    private void broadcastToParticipants(Block block, Set<CryptoId> participants) {
        new HashSet<>(participants).forEach(p -> {
            service.sendBlock(p, block);
            logger.info("Sent " + block.getHetconsMessage().getType() + " to " + HetconsUtil.cryptoIdToString(p));
        });
    }


    public HetconsObserver getObserver() {
        return observer;
    }

    public HashMap<String, HetconsProposalStatus> getProposalStatus() {
        return proposalStatus;
    }

    private String formatConsensus(List<Reference> m2bs) {
        StringBuilder stringBuilder = new StringBuilder();
        HetconsMessage2ab m2b = service.getBlock(m2bs.get(0)).getHetconsMessage().getM2B();
        stringBuilder.append(String.format("A quorum of %d messages for Observer %s have been received\n\nDecided on value: %s\nForChain: %s\n\nBallot:%s\n\n",
                m2bs.size(), HetconsUtil.cryptoIdToString(observer.getId()),
                get2bValue(m2b), HetconsUtil.buildConsensusId(getM1aFromReference(m2b.getM1ARef()).getProposal().getSlotsList()),getM1aFromReference(m2b.getM1ARef()).getProposal().getBallot().getBallotSequence()));
        for (int i = 0; i < m2bs.size(); i++) {
            Reference r = m2bs.get(i);
            stringBuilder.append(String.format("\t%s\n", HetconsUtil.bytes2Hex(r.getHash().getSha3().toStringUtf8().getBytes())));
        }
        return stringBuilder.toString();
    }

    public HetconsMessage1a getM1aFromReference(Reference m1aRef) {
        try {
            Block block = service.getBlock(m1aRef);
            return block.getHetconsMessage().getM1A();
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }


}
