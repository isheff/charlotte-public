package com.xinwenwang.hetcons;


import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.proto.*;

import java.sql.Ref;
import java.util.*;
import java.util.concurrent.ExecutorService;

import java.util.concurrent.Future;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HetconsProposalStatus {


    private static final int maxTimeOut = 10 * 1000;
    private HashMap<Integer, QuorumStatus> quorums;
    private QuorumStatus activeQuorum;
    private HashMap<String, ParticipantStatus> participantStatuses;
    private ReentrantReadWriteLock participantStatusLock;
    private ReentrantReadWriteLock proposalLock;
    public final Integer generalLock = 0;
    private List<String> chainIDs;
    private HetconsConsensusStage stage;
    private LinkedList<HetconsProposal> proposals;
    private Future<?> m1bTimer;
    private Future<?> m2bTimer;
    private long consensusTimeout;
    private String ConsensusID;
    private HetconsParticipantService service;
    private HetconsQuorumStatus currentQuorum;
    private Map<String, HetconsQuorumStatus> globalStatus;
    private Reference observerGroupReference;
    private Boolean hasDecided;
    private RoundStatus roundStatus;
    private Boolean isProposer;
    private List<Reference> decidedQuorum;
    private HetconsValue decidedValue;
    private ExecutorService timer;
    private Set<CryptoId> allParticipants;

    private static final Logger logger = Logger.getLogger(CharlotteNodeService.class.getName());

    public HetconsProposalStatus(HetconsConsensusStage stage,
                                 HetconsProposal proposal,
                                 HetconsQuorumStatus quorumStatus,
                                 Reference observerGroupReference) {
        this.stage = stage;
        this.proposals = new LinkedList<HetconsProposal>();
        this.quorums = new HashMap<>();
        this.participantStatuses = new HashMap<>();
        if (proposal != null)
            proposals.add(proposal);
        this.currentQuorum = quorumStatus;
        this.observerGroupReference = observerGroupReference;
        initQuorum(currentQuorum);
        initParticipantStatues(activeQuorum);
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
            initQuorum(currentQuorum);
            initParticipantStatues(activeQuorum);
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

    public ExecutorService getTimer() {
        return timer;
    }

    public void setTimer(ExecutorService timer) {
        this.timer = timer;
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

    public Future<?> getM1bTimer() {
        return m1bTimer;
    }

    public void setM1bTimer(Future<?> m1bTimer) {
        this.m1bTimer = m1bTimer;
    }

    public Future<?> getM2bTimer() {
        return m2bTimer;
    }

    public void setM2bTimer(Future<?> m2bTimer) {
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

    public void initQuorum(HetconsQuorumStatus q) {
        activeQuorum = new QuorumStatus(q.getMainQuorum());
        allParticipants = activeQuorum.getAllParticipants();
    }

    public void initParticipantStatues(QuorumStatus qs) {
        qs.getAllParticipants().forEach( m -> {
            participantStatuses.putIfAbsent(HetconsUtil.cryptoIdToString(m),
                    new ParticipantStatus(m));
            ParticipantStatus s = participantStatuses.get(HetconsUtil.cryptoIdToString(m));
            s.addQuorum(qs);
                }
        );
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
            QuorumStatus q;
            participantStatusLock.readLock().lock();
            ParticipantStatus s = participantStatuses.get(HetconsUtil.cryptoIdToString(id));
            if (s == null)
                return null;
            synchronized (activeQuorum.m1bs) {
                if (!s.addM1b(ref1b))
                    return null;
                q = s.check1bQuorum();
            }
            if (q != null) {
                List<Reference> q1b = new ArrayList<>();
                for (ParticipantStatus p : q.getQuorumM1bs()) {
                    q1b.add(p.m1bRef);
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
            QuorumStatus q;
            participantStatusLock.readLock().lock();
            ParticipantStatus s = participantStatuses.get(HetconsUtil.cryptoIdToString(id));
            if (s == null) {
                return null;
            }
            synchronized (activeQuorum.m2bs) {
                if(!s.addM2b(ref2b))
                    return null;
                q = s.check2bQuorum();
            }
            if (q != null) {
                List<CryptoId> qp = new ArrayList<>();
                List<Reference> q2b = new ArrayList<>();
                for (ParticipantStatus p : q.getQuorumM2bs()) {
                    q2b.add(p.m2bRef);
                    qp.add(p.id);
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

    public Set<CryptoId> getParticipants() {
        return allParticipants;
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

        boolean addM1b(Reference m1b) {
            m1bRef = m1b;
            boolean noDuplicated = true;
            for (QuorumStatus q: quorumStatuses) {
                noDuplicated = noDuplicated && q.add1b(this);
            }
            return noDuplicated;
        }

        boolean addM2b(Reference m2b) {
            m2bRef = m2b;
            boolean noDuplicated = true;
            for (QuorumStatus q: quorumStatuses) {
                noDuplicated = noDuplicated && q.add2b(this);
            }
            return noDuplicated;
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

        Set<CryptoId> participants;
        Set<String> duplicateCheck;
        Set<ParticipantStatus> m1bs;
        Set<ParticipantStatus> m2bs;

        List<QuorumStatus> subQuorumStatus;
        int size;


        QuorumStatus(HetconsObserverQuorum q) {

            subQuorumStatus = new ArrayList<>();
            participants = new HashSet<>();
            m1bs = new HashSet<>();
            m2bs = new HashSet<>();
            duplicateCheck = new HashSet<>();

            if (q.getSpecsCount() == 1) {
                HetconsObserverQuorum.Spec spec = q.getSpecsList().get(0);
                String chainName = spec.getBase().split("\\.")[0];
                String quorumName = spec.getBase().split("\\.")[1];
                if (quorumName.equals(q.getName())) {
                    participants.addAll(q.getMembersList());
                    subQuorumStatus.add(this);
                    this.size = q.getSpecs(0).getSize();
                } else {
                    subQuorumStatus.add(new QuorumStatus(globalStatus.get(chainName).getSubQuorum(quorumName)));
                }
            } else {
                for (HetconsObserverQuorum.Spec spec : q.getSpecsList()) {
                    String chainName = spec.getBase().split(".")[0];
                    String quorumName = spec.getBase().split(".")[1];
                    if (chainName.length() == 0)
                        chainName = currentQuorum.getChainName();
                    subQuorumStatus.add(new QuorumStatus(globalStatus.get(chainName).getSubQuorum(quorumName)));
                }
            }
        }

        boolean add1b(ParticipantStatus p) {
            if (!duplicateCheck.add("m1b" +HetconsUtil.cryptoIdToString(p.id)))
                return false;
            if (subQuorumStatus.size() == 1 && !participants.isEmpty())
                m1bs.add(p);
            else
                subQuorumStatus.forEach(s -> s.add1b(p));
            return true;
        }

        boolean add2b(ParticipantStatus p) {
            if (!duplicateCheck.add("m2b" +HetconsUtil.cryptoIdToString(p.id)))
                return false;
            if (subQuorumStatus.size() == 1 && !participants.isEmpty())
                m2bs.add(p);
            else
                subQuorumStatus.forEach(s -> s.add2b(p));
            return true;
        }

        /**
         * Recursive check if there is enough 1bs
         * @return
         */
        boolean isEnough1b() {
            if (subQuorumStatus.size() == 1 && !participants.isEmpty())
                return m1bs.size() >= size;
            else
                return subQuorumStatus.stream().map(QuorumStatus::isEnough1b).reduce((a, b) -> a && b).get();
        }

        /**
         * Recursive check if there is enough 2bs
         * @return
         */
        boolean isEnough2b() {
            if (subQuorumStatus.size() == 1 && !participants.isEmpty())
                return m2bs.size() >= size;
            else
                return subQuorumStatus.stream().map(QuorumStatus::isEnough2b).reduce((a, b) -> a && b).get();
        }

        Set<CryptoId> getAllParticipants() {
           if (subQuorumStatus.size() == 1 && !participants.isEmpty()) {
               return participants;
           } else {
               return subQuorumStatus.stream().map(QuorumStatus::getAllParticipants).reduce((a, e) -> {
                 a.addAll(e);
                 return a;
               }).get();
           }
        }

        public Set<ParticipantStatus> getQuorumM1bs() {
            if (!participants.isEmpty() && subQuorumStatus.size() == 1)
                return m1bs;
            else
                return subQuorumStatus.stream().map(QuorumStatus::getQuorumM1bs).reduce((a, e) -> {
                    a.addAll(e);
                    return a;
                }).get();
        }

        public Set<ParticipantStatus> getQuorumM2bs() {
            if (!participants.isEmpty() && subQuorumStatus.size() == 1)
                return m2bs;
            else
                return subQuorumStatus.stream().map(QuorumStatus::getQuorumM2bs).reduce((a, e) -> {
                    a.addAll(e);
                    return a;
                }).get();
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


