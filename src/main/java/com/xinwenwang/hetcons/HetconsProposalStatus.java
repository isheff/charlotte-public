package com.xinwenwang.hetcons;


import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.proto.*;

import java.sql.Ref;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public class HetconsProposalStatus {


    private static final int maxTimeOut = 10 * 1000;
    private HashMap<Integer, QuorumStatus> quorums;
    private HashMap<String, ParticipantStatus> participantStatuses;
    private ReentrantReadWriteLock participantStatusLock;
    private ReentrantReadWriteLock proposalLock;
    public final Integer generalLock = 0;
    private List<String> chainIDs;
    private HetconsConsensusStage stage;
    private LinkedList<HetconsProposal> proposals;
    private Timer m1bTimer;
    private Timer m2bTimer;
    private long consensusTimeout;
    private String ConsensusID;
    private HetconsParticipantService service;
    private List<List<CryptoId>> members;
    private Reference observerGroupReference;
    private Boolean hasDecided;
    private RoundStatus roundStatus;
    private Boolean isProposer;
    private List<Reference> decidedQuorum;
    private HetconsValue decidedValue;

    private static final Logger logger = Logger.getLogger(CharlotteNodeService.class.getName());

    public HetconsProposalStatus(HetconsConsensusStage stage,
                                 HetconsProposal proposal,
                                 List<List<CryptoId>> members,
                                 Reference observerGroupReference) {
        this.stage = stage;
        this.proposals = new LinkedList<HetconsProposal>();
        this.quorums = new HashMap<>();
        this.participantStatuses = new HashMap<>();
        if (proposal != null)
            proposals.add(proposal);
        this.members = members;
        this.observerGroupReference = observerGroupReference;
        initQuorum(members);
        initParticipantStatues(members);
        consensusTimeout = proposal.getTimeout();
        hasDecided = false;
        roundStatus = new RoundStatus();
        isProposer = false;
        proposalLock = new ReentrantReadWriteLock();
        participantStatusLock = new ReentrantReadWriteLock();

    }

    public HetconsProposal getCurrentProposal() {
        try {
            proposalLock.readLock().lock();
            if (proposals.isEmpty())
                return null;
            else
                return proposals.getLast();
        } finally {
            proposalLock.readLock().unlock();
        }


    }

    public void updateProposal(HetconsProposal proposal) {
        try {
            proposalLock.writeLock().lock();
            if (getCurrentProposal().equals(proposal))
                return;
            logger.info("Old Proposal:\n" +
                    getCurrentProposal() + "\n");
            this.proposals.add(proposal);
            if (proposals.size() > 1) {
                reset();
            }
            logger.info("Updated Proposal:\n" +
                    proposal + "\n");
        } finally {
            proposalLock.writeLock().unlock();
        }
    }

    /**
     * After timeout, we reset 1b and 2bs
     */
    public void reset() {
        try {
            participantStatusLock.writeLock().lock();
            this.participantStatuses = new HashMap<>();
            initQuorum(members);
            initParticipantStatues(members);
        } finally {
            participantStatusLock.writeLock().unlock();
        }

    }

    public List<HetconsProposal> getProposalsHistory() {
        return proposals;
    }

    public long getConsensuTimeout() {
        return consensusTimeout;
    }

    public void setConsensuTimeout(long consensuTimeout) {
        if (consensuTimeout > maxTimeOut) {
            Logger.getGlobal().warning("consensus specified timeout value is greater than the max value. Therefore, we use the max value instead.");
            consensuTimeout = maxTimeOut;
        }
        this.consensusTimeout = consensuTimeout;
    }

    public void setDecidedQuorum(List<Reference> decidedQuorum) {
        if (this.decidedQuorum == null)
            this.decidedQuorum = decidedQuorum;
    }

    public List<Reference> getDecidedQuorum() {
        return decidedQuorum;
    }

    public void setDecidedValue(HetconsValue decidedValue) {
        if (this.decidedQuorum == null)
            this.decidedValue = decidedValue;
    }

    public HetconsValue getDecidedValue() {
        return decidedValue;
    }

    public Boolean getProposer() {
        return isProposer;
    }

    public void setProposer(Boolean proposer) {
        isProposer = proposer;
    }

    public void setHasDecided(Boolean hasDecided) {
        this.hasDecided = hasDecided;
    }

    public Boolean getHasDecided() {
        return hasDecided;
    }

    public Timer getM1bTimer() {
        return m1bTimer;
    }

    public void setM1bTimer(Timer m1bTimer) {
        this.m1bTimer = m1bTimer;
    }

    public Timer getM2bTimer() {
        return m2bTimer;
    }

    public void setM2bTimer(Timer m2bTimer) {
        this.m2bTimer = m2bTimer;
    }

    public HetconsParticipantService getService() {
        return service;
    }

    public void setService(HetconsParticipantService service) {
        this.service = service;
    }

    public String getConsensusID() {
        return ConsensusID;
    }

    public void setConsensusID(String consensusID) {
        ConsensusID = consensusID;
    }

    public List<String> getChainIDs() {
        return chainIDs;
    }

    public void setChainIDs(List<String> chainIDs) {
        this.chainIDs = chainIDs;
    }

    public HetconsConsensusStage getStage() {
        return stage;
    }

    public void setStage(HetconsConsensusStage stage) {
        this.stage = stage;
    }

    public Reference getObserverGroupReference() {
        return observerGroupReference;
    }

    public HashMap<String, ParticipantStatus> getParticipantStatuses() {
            return participantStatuses;
    }

    public Integer getGeneralLock() {
        return generalLock;
    }

    /** -----------------------version 2 ----------------------------------*/

    public void initQuorum(List<List<CryptoId>> q) {

        for (int i = 0; i < q.size(); i++) {
            quorums.put(i, new QuorumStatus(q.get(i)));
        }
    }

    public void initParticipantStatues(List<List<CryptoId>> qs) {
        for (int i = 0; i < qs.size(); i++) {
            for (CryptoId m : qs.get(i)) {
                participantStatuses.putIfAbsent(HetconsUtil.cryptoIdToString(m),
                        new ParticipantStatus(m));
                ParticipantStatus s = participantStatuses.get(HetconsUtil.cryptoIdToString(m));
                s.addQuorum(quorums.get(i));
            }
        }
    }

    public void updateRecent1b(boolean has2A, HetconsValue value, HetconsBallot ballot) {
        roundStatus.update1BValue(has2A, value, ballot);
    }

    public void updateRecent2b(HetconsValue value, HetconsBallot ballot) {
        roundStatus.update2BValue(value, ballot);
    }

    public HetconsValue getRecent1b() {
        return roundStatus.m1bValue;
    }

    public HetconsValue getRecent2b() {
        return roundStatus.m2bValue;
    }

    public List<Reference> receive1b(CryptoId id, Reference ref1b) {
        try{
            participantStatusLock.readLock().lock();
            ParticipantStatus s = participantStatuses.get(HetconsUtil.cryptoIdToString(id));
            if (s == null)
                return null;
            s.addM1b(ref1b);
            QuorumStatus q = s.check1bQuorum();
            if (q != null) {
                List<Reference> q1b = new ArrayList<>();
                for (String p : q.participants) {
                    q1b.add(this.participantStatuses.get(p).m1bRef);
                }
                return q1b;
            }
            return null;
        } finally {
            participantStatusLock.readLock().unlock();
        }
    }

    public HashMap<String, Object> receive2b(CryptoId id, Reference ref2b) {
        try{
            participantStatusLock.readLock().lock();
            ParticipantStatus s = participantStatuses.get(HetconsUtil.cryptoIdToString(id));
            if (s == null) {
                return null;
            }
            s.addM2b(ref2b);
            QuorumStatus q = s.check2bQuorum();
            if (q != null) {
                List<CryptoId> qp = new ArrayList<>();
                List<Reference> q2b = new ArrayList<>();
                for (String p : q.participants) {
                    q2b.add(this.participantStatuses.get(p).m2bRef);
                    qp.add(this.participantStatuses.get(p).id);
                }
                HashMap<String, Object> ret = new HashMap<>();
                ret.put("references", q2b);
                ret.put("participants", qp);
                return ret;
            }
            return null;
        } finally {
            participantStatusLock.readLock().unlock();
        }
    }

    class ParticipantStatus {

        CryptoId id;
        Reference m1bRef;
        Reference m2bRef;
        List<QuorumStatus> quorumStatuses;

        ParticipantStatus(CryptoId id) {
            this.id = id;
            quorumStatuses = new ArrayList<>();
        }

        void addQuorum(QuorumStatus q) {
            this.quorumStatuses.add(q);
        }

        void addM1b(Reference m1b) {
            m1bRef = m1b;
            for (QuorumStatus q: quorumStatuses) {
                q.add1b(id);
            }
        }

        void addM2b(Reference m2b) {
            m2bRef = m2b;
            quorumStatuses.forEach(q -> {
                q.add2b(id);
            });
        }

        QuorumStatus check1bQuorum() {
            for (QuorumStatus q : quorumStatuses)
                if (q.isEnough1b())
                    return q;
            return null;
        }

        QuorumStatus check2bQuorum() {
            for (QuorumStatus q : quorumStatuses)
                if (q.isEnough2b())
                    return q;
            return null;
        }
    }

    class QuorumStatus {

        Set<String> participants;
        Set<String> m1bs;
        Set<String> m2bs;

        QuorumStatus(List<CryptoId> ids) {
            participants = new HashSet<>();
            m1bs = new HashSet<>();
            m2bs = new HashSet<>();

            ids.forEach(id -> {
                participants.add(HetconsUtil.cryptoIdToString(id));
            });
        }

        void add1b(CryptoId id) {
            m1bs.add(HetconsUtil.cryptoIdToString(id));
        }

        void add2b(CryptoId id) {
            m2bs.add(HetconsUtil.cryptoIdToString(id));
        }

        boolean isEnough1b() {
            return m1bs.size() == participants.size();
        }

        boolean isEnough2b() {
            return m2bs.size() == participants.size();
        }

    }

    class RoundStatus {

        HetconsValue m1bValue;
        HetconsBallot m1bBallot;
        boolean has2A = false;

        HetconsValue m2bValue;
        HetconsBallot m2bBallot;

        void update1BValue(boolean has2A, HetconsValue value, HetconsBallot ballot) {
            if (this.has2A && !has2A)
                return;

            if (m1bBallot == null || HetconsUtil.ballotCompare(ballot, m1bBallot) >= 0) {
                this.m1bBallot = ballot;
                this.m1bValue = value;
            }
        }

        void update2BValue(HetconsValue value, HetconsBallot ballot) {
            if (m2bBallot == null || HetconsUtil.ballotCompare(ballot, m2bBallot) >= 0) {
                this.m2bBallot = ballot;
                this.m2bValue = value;
            }
        }
    }
}


