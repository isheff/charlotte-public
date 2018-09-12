package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.proto.HetconsObserver;

import java.util.HashMap;
import java.util.Set;

public class HetconsObserverStatus {

    // for which this observer
    private HetconsObserver observer;

    // map from proposal's consensus id to HetconsProposalStatus object. If a set of proposals are conflicting, then they should point to the same status
    private HashMap<String, HetconsProposalStatus> proposal2StatusMap;

    // Map from chain slot to a set of proposals which contain the slot in their proposal.
    private HashMap<String, Set<String>> slot2Proposal;


    public HetconsObserverStatus(HetconsObserver observer) {
        this.observer = observer;
        proposal2StatusMap = new HashMap<>();
        slot2Proposal = new HashMap<>();
    }

    public HetconsObserver getObserver() {
        return observer;
    }

    public HashMap<String, HetconsProposalStatus> getProposal2StatusMap() {
        return proposal2StatusMap;
    }

    public HashMap<String, Set<String>> getSlot2Proposal() {
        return slot2Proposal;
    }
}
