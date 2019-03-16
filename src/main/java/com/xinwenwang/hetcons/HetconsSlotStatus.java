package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.proto.HetconsBallot;
import com.isaacsheff.charlotte.proto.HetconsMessage1a;
import com.isaacsheff.charlotte.proto.HetconsMessage2ab;
import com.isaacsheff.charlotte.proto.Reference;

import java.util.List;

public class HetconsSlotStatus {

    private String activeProposal;

    private HetconsBallot ballot;

    private StatusStage stage;

    private List<Reference> quorumOf1b;

    private HetconsMessage2ab m2a;

    private List<Reference> quorumOf2b;

    private String slot;

    public HetconsSlotStatus(HetconsBallot ballot) {
        this.ballot = ballot;
        this.stage = StatusStage.RECEIVED1A;
    }

    public HetconsSlotStatus() {
        this.stage = StatusStage.NOPROPOSAL;
    }

    public HetconsSlotStatus(String slot) {
        this.stage = StatusStage.NOPROPOSAL;
        this.slot = slot;
    }

    synchronized public void updateBallot(HetconsBallot ballot) {
        if (this.ballot == null  ||
                this.ballot.getBallotSequence().compareTo(ballot.getBallotSequence()) < 0) {
            if (this.ballot == null)
                this.stage = StatusStage.RECEIVED1A;
            this.ballot = ballot;
        }
    }

    public boolean has2aFromOtherProposal(String id, HetconsObserverStatus observerStatus) {
        return !(m2a == null || id.equals(HetconsUtil.buildConsensusId(observerStatus.getM1aFromReference(m2a.getM1ARef()).getProposal().getSlotsList())));
    }

    public boolean hasLargerBallot(HetconsBallot ballot) {
        return this.ballot != null && this.ballot.getBallotSequence().compareTo(ballot.getBallotSequence()) > 0;
    }


    /**
     * Only update once message 2a.
     * @param m2a the m2a to be used for this slot.
     */
    synchronized public void setM2a(HetconsMessage2ab m2a, HetconsObserverStatus observerStatus) {

        if (this.m2a == null || observerStatus.getM1aFromReference(this.m2a.getM1ARef()).getProposal().getBallot().getBallotSequence().compareTo(
                observerStatus.getM1aFromReference(m2a.getM1ARef()).getProposal().getBallot().getBallotSequence()
        ) < 0) {
            this.m2a = m2a;
            this.stage = StatusStage.RECEIVED2A;
        }
    }

    public HetconsMessage2ab getM2a() {
        return m2a;
    }

    public boolean isDecided() {
        return this.stage == StatusStage.DECIDED;
    }

    public void decide(HetconsBallot ballot, List<Reference> m2bs, String proposalID) {
        activeProposal = proposalID;
        quorumOf2b = m2bs;
        this.stage = StatusStage.DECIDED;
    }

    public void decide(List<Reference> m2bs, String proposalID) {
        activeProposal = proposalID;
        quorumOf2b = m2bs;
        this.stage = StatusStage.DECIDED;
    }

    public void setActiveProposal(String activeProposal) {
        this.activeProposal = activeProposal;
    }

    public String getActiveProposal() {
        return activeProposal;
    }

    public HetconsBallot getBallot() {
        return ballot;
    }

    public String getSlot() {
        return slot;
    }
}

enum StatusStage {
    NOPROPOSAL,
    RECEIVED1A,
    RECEIVED2A,
    DECIDED
}
