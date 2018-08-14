package com.xinwenwang.hetcons;


import com.isaacsheff.charlotte.proto.HetconsConsensusStage;
import com.isaacsheff.charlotte.proto.HetconsMessage2ab;
import com.isaacsheff.charlotte.proto.HetconsProposal;
import com.isaacsheff.charlotte.proto.HetconsStageStatus;

import java.util.LinkedList;
import java.util.List;

public class HetconsStatus {

    private HetconsConsensusStage stage;
    private LinkedList<HetconsProposal> proposals;
    private HetconsMessage2ab latestMessage2a;


    public HetconsStatus(HetconsConsensusStage stage, HetconsProposal proposal) {
        this.stage = stage;
        this.proposals = new LinkedList<HetconsProposal>();
        if (proposal != null)
            proposals.add(proposal);
        latestMessage2a = null;
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
        this.proposals.add(proposal);
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

    public void setLatestMessage2a(HetconsMessage2ab message2a) {
        this.latestMessage2a = message2a;
    }

    public HetconsMessage2ab getLatestMessage2a() {
        return latestMessage2a;
    }
}
