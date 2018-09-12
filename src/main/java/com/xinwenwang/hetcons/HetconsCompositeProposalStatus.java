package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.proto.HetconsConsensusStage;
import com.isaacsheff.charlotte.proto.HetconsProposal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class HetconsCompositeProposalStatus extends HetconsProposalStatus {

    private HetconsProposalStatus currentStatus;
    private HetconsProposal proposal;
    private HashMap<String, HetconsProposalStatus> statusMap;
    private HashMap<String, ArrayList<String>> slotConflictMap;
    private HashMap<String, Set<String>> proposalConflictMap;

    public HetconsCompositeProposalStatus(Iterable<HetconsProposalStatus> status) {
        super(HetconsConsensusStage.UNRECOGNIZED, null);
        statusMap = new HashMap<>();
        proposalConflictMap = new HashMap<>();
        slotConflictMap = new HashMap<>();

        /* We first build a map for which proposal contains each chain slot*/
        status.forEach(s -> {
            statusMap.put(s.getConsensusID(), s);
            for (String id : s.getConsensusID().split("|")) {
                slotConflictMap.putIfAbsent(id, new ArrayList<>());
                slotConflictMap.get(id).add(s.getConsensusID());
            }
        });

        /* Then, we are able to build a map for proposal to its conflict proposals */
        status.forEach(s -> {
            proposalConflictMap.putIfAbsent(s.getConsensusID(), new HashSet<>());
            for (String id : s.getConsensusID().split("|")) {
                proposalConflictMap.get(s.getConsensusID()).addAll(slotConflictMap.get(id));
                proposalConflictMap.get(s.getConsensusID()).remove(s.getConsensusID());
            }
        });
    }

    @Override
    public void onDecided(String proposalID) {
        super.onDecided(proposalID);
    }

    private void resolveConflicts() {

    }

    @Override
    public void onReceiveNewProposal(HetconsProposal proposal) {

    }

    @Override
    public HetconsProposalStatus getStatus() {
        return currentStatus;
    }
}
