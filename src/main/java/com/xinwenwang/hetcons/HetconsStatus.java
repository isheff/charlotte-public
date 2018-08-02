package com.xinwenwang.hetcons;


import com.isaacsheff.charlotte.proto.HetconsConsensusStage;
import com.isaacsheff.charlotte.proto.HetconsProposal;
import com.isaacsheff.charlotte.proto.HetconsStageStatus;

import java.util.LinkedList;
import java.util.List;

public class HetconsStatus {

    private HetconsConsensusStage stage;
    private List<HetconsProposal> proposals;


    public HetconsStatus(HetconsConsensusStage stage, HetconsProposal proposal) {
        this.stage = stage;
        this.proposals = new LinkedList<HetconsProposal>();
        proposals.add(proposal);
    }

    public HetconsConsensusStage getStage() {
        return stage;
    }

    public void setStage(HetconsConsensusStage stage) {
        this.stage = stage;
    }

    public HetconsProposal getCurrentProposal() {
        return proposals.get(0);
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
}
