package com.xinwenwang.hetcons;


import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.proto.*;

import java.sql.Ref;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

public class HetconsProposalStatus {


    private HashMap<Integer, QuorumStatus> quorums;
    private HashMap<String, ParticipantStatus> participantStatuses;


    private HetconsConsensusStage stage;
    private LinkedList<HetconsProposal> proposals;
    private Timer m1bTimer;
    private Timer m2bTimer;
    private HetconsParticipantService service;
    private long consensuTimeout;

    private List<String> chainIDs;

    private String ConsensusID;

    private static final int maxTimeOut = 10 * 1000;

    public HetconsProposalStatus(HetconsConsensusStage stage, HetconsProposal proposal) {
        this.stage = stage;
        this.proposals = new LinkedList<HetconsProposal>();
        this.quorums = new HashMap<>();
        this.participantStatuses = new HashMap<>();
        if (proposal != null)
            proposals.add(proposal);
    }

    public HetconsProposalStatus(HetconsConsensusStage stage) {
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

    public HetconsProposalStatus(HetconsProposal proposal) {
        this(HetconsConsensusStage.ConsensusIdile, proposal);
    }

    public List<HetconsProposal> getProposalsHistory() {
        return proposals;
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

    public void setService(HetconsParticipantService service) {
        this.service = service;
    }

    public HetconsParticipantService getService() {
        return service;
    }





    public void setConsensusID(String consensusID) {
        ConsensusID = consensusID;
    }

    public String getConsensusID() {
        return ConsensusID;
    }

    public void onDecided(String proposalID) {}


    public void setChainIDs(List<String> chainIDs) {
        this.chainIDs = chainIDs;
    }

    public List<String> getChainIDs() {
        return chainIDs;
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
                ParticipantStatus s = participantStatuses.putIfAbsent(HetconsUtil.cryptoIdToString(m),
                        new ParticipantStatus(m));
                s.addQuorum(quorums.get(i));
            }
        }
    }

    public List<Reference> receive1b(CryptoId id, Reference ref1b) {
        ParticipantStatus s = participantStatuses.get(HetconsUtil.cryptoIdToString(id));
        s.addM1b(ref1b);
        QuorumStatus q = s.checkQuorum();
        if (q != null) {
            List<Reference> q1b = new ArrayList<>();
            for (String p : q.participants) {
                q1b.add(this.participantStatuses.get(p).m1bRef);
            }
            return q1b;
        }
        return null;
    }

    public List<Reference> receive2b(CryptoId id, Reference ref2b) {
        ParticipantStatus s = participantStatuses.get(HetconsUtil.cryptoIdToString(id));
        s.addM2b(ref2b);
        QuorumStatus q = s.checkQuorum();
        if (q != null) {
            List<Reference> q2b = new ArrayList<>();
            for (String p : q.participants) {
                q2b.add(this.participantStatuses.get(p).m2bRef);
            }
            return q2b;
        }
        return null;
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

        QuorumStatus checkQuorum() {
            for (QuorumStatus q : quorumStatuses)
                if (q.isEnough1b())
                    return q;
            return null;
        }

        void addM2b(Reference m2b) {
            m2bRef = m2bRef;
            quorumStatuses.forEach(q -> {
                q.add2b(id);
            });
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

    }
}


