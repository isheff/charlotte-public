package com.xinwenwang.hetcons;


import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.proto.*;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public class HetconsStatus {

    private HetconsConsensusStage stage;
    private LinkedList<HetconsProposal> proposals;
    private HashMap<String, HetconsMessage2ab> latestMessage2a;
    private HetconsMessage2ab highestBallotM2A;
    private HetconsObserverGroup observerGroup;
    private HashMap<HetconsObserverQuorum, ArrayList<Hash>> quorumOf1bs;
    private HashMap<HetconsObserverQuorum, ArrayList<Hash>> quorumOf2bs;
    private HashMap<HetconsObserverQuorum, ArrayList<HetconsValue>> quorumOf1bsValues;
    private HashMap<HetconsObserverQuorum, ArrayList<HetconsValue>> quorumOf2bsValues;
    private HashMap<String, ArrayList<HetconsObserverQuorum>> participants;
    private HashMap<String, CryptoId> participantIds;
    private HashMap<String, Boolean> participantM1BResponsed;
    private HashMap<String, Boolean> participantM2BResponsed;
    private Timer m1bTimer;
    private Timer m2bTimer;
    private HetconsParticipantService service;
    private long consensuTimeout;

    private ReadWriteLock lock;

    private static final int maxTimeOut = 10 * 1000;

    public HetconsStatus(HetconsConsensusStage stage, HetconsProposal proposal) {
        this.stage = stage;
        this.proposals = new LinkedList<HetconsProposal>();
        if (proposal != null)
            proposals.add(proposal);
        participants = new HashMap<>();
        lock = new ReentrantReadWriteLock();
        reset();
        latestMessage2a = new HashMap<>();
        highestBallotM2A = HetconsMessage2ab.newBuilder().build();
    }

    public HetconsStatus(HetconsConsensusStage stage) {
        this(stage, null);
    }

    public HetconsConsensusStage getStage() {
        return stage;
    }

    public void setStage(HetconsConsensusStage stage) {
        this.stage = stage;
    }

    public HetconsProposal getCurrentProposal() {
        if (proposals.isEmpty())
            return null;
        else
            return proposals.getLast();
    }

    public void updateProposal(HetconsProposal proposal) {
        System.out.printf("Old Proposal:\n" +
                getCurrentProposal() + "\n");
        this.proposals.add(proposal);
        if (proposals.size() > 1) {
            reset();
        }
        System.out.printf("Updated Proposal:\n" +
                proposal + "\n");

    }

    public HetconsStatus(HetconsProposal proposal) {
        this(HetconsConsensusStage.ConsensusIdile, proposal);
    }

    public List<HetconsProposal> getProposalsHistory() {
        return proposals;
    }

    public boolean hasMessage2a() {
        return latestMessage2a != null;
    }

    /**
     * Here we update the highest ballot number 2a message associated with the given observer. At meanwhile, we
     * also update the global highest ballot number 2a message.
     * @param observer the observer cares about this message
     * @param message2ab the 2A messasge to be added into the map
     */
    public void addM2A(CryptoId observer, HetconsMessage2ab message2ab) {
        HetconsMessage2ab oldMessage = latestMessage2a.putIfAbsent(HetconsUtil.cryptoIdToString(observer), message2ab);
        if (oldMessage != null) {
            if (oldMessage.getProposal().getBallot().getBallotNumber() < message2ab.getProposal().getBallot().getBallotNumber()) {
                latestMessage2a.put(HetconsUtil.cryptoIdToString(observer), message2ab);
            }
            message2ab = oldMessage;
        }
        if (highestBallotM2A.hasProposal()) {
            if (highestBallotM2A.getProposal().getBallot().getBallotNumber() <= message2ab.getProposal().getBallot().getBallotNumber())
                highestBallotM2A = message2ab;
        } else {
            highestBallotM2A = message2ab;
        }
    }

    public HetconsMessage2ab getMessage2A(CryptoId observer) {
        HetconsMessage2ab message2ab = latestMessage2a.get(HetconsUtil.cryptoIdToString(observer));
        return message2ab == null ? highestBallotM2A : message2ab;
    }

    public void setObserverGroup(HetconsObserverGroup observerGroup) {
        this.observerGroup = observerGroup;
        // flatten quorums
        for (HetconsObserver observer : observerGroup.getObserversList()) {
            for (HetconsObserverQuorum quorum : observer.getQuorumsList()) {
                quorumOf1bs.putIfAbsent(quorum, new ArrayList<>());
                quorumOf2bs.putIfAbsent(quorum, new ArrayList<>());
                quorumOf1bsValues.putIfAbsent(quorum, new ArrayList<>());
                quorumOf2bsValues.putIfAbsent(quorum, new ArrayList<>());
                for (CryptoId id : quorum.getMemebersList()) {
                    participants.putIfAbsent(HetconsUtil.cryptoIdToString(id), new ArrayList<HetconsObserverQuorum>());
                    participants.get(HetconsUtil.cryptoIdToString(id)).add(quorum);
                    participantM1BResponsed.put(HetconsUtil.cryptoIdToString(id), Boolean.FALSE);
                    participantM2BResponsed.put(HetconsUtil.cryptoIdToString(id), Boolean.FALSE);
                    participantIds.putIfAbsent(HetconsUtil.cryptoIdToString(id), id);
                }
            }
        }
    }

    public HetconsObserverGroup getObserverGroup() {
        return observerGroup;
    }

    public HashMap<String, ArrayList<HetconsObserverQuorum>> getParticipants() {
        return participants;
    }

    public HashMap<String, CryptoId> getParticipantIds() {
        return participantIds;
    }

    public void reset() {
            quorumOf1bs = new HashMap<>();
            quorumOf2bs = new HashMap<>();
            quorumOf1bsValues = new HashMap<>();
            quorumOf2bsValues = new HashMap<>();
            participantM1BResponsed = new HashMap<>();
            participantM2BResponsed = new HashMap<>();
            participantIds = new HashMap<>();
    }

    public void updateStatus(HetconsProposal proposal, HetconsObserverGroup group) {
        lock.writeLock().lock();
        try {
            updateProposal(proposal);
            setObserverGroup(group);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     *
     * @param id
     * @param
     * @return
     */
    public ArrayList<HetconsObserverQuorum> receive1b(CryptoId id, HetconsMessage1b message1b) {
        return receiveX(id, participantM1BResponsed, quorumOf1bsValues,
                message1b.getValue(), quorumOf1bs,
                HashUtil.sha3Hash(message1b));
    }

    public ArrayList<HetconsObserverQuorum> receive2b(CryptoId id, HetconsMessage2ab message2b) {
        return receiveX(id, participantM2BResponsed,
                quorumOf2bsValues, message2b.getValue(),
                quorumOf2bs, HashUtil.sha3Hash(message2b));
    }

    private ArrayList<HetconsObserverQuorum> receiveX(CryptoId id,
                          HashMap<String, Boolean> participantMXBResponsed,
                          HashMap<HetconsObserverQuorum, ArrayList<HetconsValue>> quorumOfXbsValues,
                          HetconsValue valueX,
                          HashMap<HetconsObserverQuorum, ArrayList<Hash>> quorumOfXbs,
                          Hash hash) {
        ArrayList<HetconsObserverQuorum> validQuorums = new ArrayList<>();
        if (participantMXBResponsed.containsKey(HetconsUtil.cryptoIdToString(id)) && !participantMXBResponsed.get(HetconsUtil.cryptoIdToString(id))) {
            participantMXBResponsed.put(HetconsUtil.cryptoIdToString(id), Boolean.TRUE);
            HetconsProposal proposal = getCurrentProposal();
            this.lock.readLock().lock();
            try {
                HetconsProposal updatedProposal = getCurrentProposal();
                if (proposal.getBallot().getBallotSequence().compareTo(updatedProposal.getBallot().getBallotSequence()) < 0)
                    return validQuorums;
                participants.get(HetconsUtil.cryptoIdToString(id)).forEach(quorum -> {
                    quorumOfXbsValues.get(quorum).add(valueX);
                    quorumOfXbs.get(quorum).add(hash);
                    if (quorumOfXbs.get(quorum).size() == quorum.getMemebersCount()) {
                        validQuorums.add(quorum);
                        quorumOfXbsValues.get(quorum).forEach(value -> {
                            if (!value.equals(valueX)) {
                                validQuorums.remove(quorum);
//                                quorumOfXbs.get(quorum).clear();
//                                quorumOfXbsValues.get(quorum).clear();
                            }
                        });
                    }
                });
            } finally {
                this.lock.readLock().unlock();
            }
        }
        return validQuorums;
    }


    /**
     * Put all received hashes of 1b blocks for a given quorum into a quorumRef object.
     * @param quorum  the satisfied quorum
     * @return a quorumRef object which is a list of block hashes
     */
    public HetconsQuorumRefs get1bQuorumRef(HetconsObserverQuorum quorum) {
        return HetconsQuorumRefs.newBuilder()
                .addAllBlockHashes(quorumOf1bs.get(quorum))
                .build();
    }

    public void setConsensuTimeout(long consensuTimeout) {
        if (consensuTimeout > maxTimeOut) {
            Logger.getGlobal().warning("consensus specified timeout value is greater than the max value. Therefore, we use the max value instead.");
            consensuTimeout = maxTimeOut;
        }
        this.consensuTimeout = consensuTimeout;
    }

    public long getConsensuTimeout() {
        return consensuTimeout;
    }

    public Timer getM1bTimer() {
        return m1bTimer;
    }

    public Timer getM2bTimer() {
        return m2bTimer;
    }

    public void setM1bTimer(Timer m1bTimer) {
        this.m1bTimer = m1bTimer;
    }

    public void setM2bTimer(Timer m2bTimer) {
        this.m2bTimer = m2bTimer;
    }

    public HetconsMessage2ab getHighestBallotM2A() {
        return highestBallotM2A;
    }

    public void setService(HetconsParticipantService service) {
        this.service = service;
    }

    public HetconsParticipantService getService() {
        return service;
    }

    private HetconsValue getM1BValue(HetconsMessage1b message1b) {
        HetconsMessage2ab message2a = message1b.getM2A();
         return (message2a.hasProposal() ? message2a.getValue() : message1b.getM1A().getProposal().getValue());
    }

    private HetconsValue getM2BValue(HetconsMessage2ab message2b) {
        List<Hash> hashes = message2b.getQuorumOf1Bs().getBlockHashesList();
        HetconsValue value = null;
        HetconsValue blockValue = null;
        for (Hash i : hashes) {
            Block block = service.getBlock(i);
            if (block.hasHetconsMessage() && block.getHetconsMessage().hasM1B() && block.getHetconsMessage().getM1B().hasValue()) {
                blockValue = block.getHetconsMessage().getM1B().getValue();
                if (value == null)
                    value = blockValue;
                else if (!value.equals(blockValue)) {
                    return null;
                }
            }
        }
        return value;
    }

    public ReadWriteLock getLock() {
        return lock;
    }
}
