package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.HetconsObserverQuorum;

import java.nio.file.Path;
import java.util.*;

/**
 * Class for quorums for an observer
 */
public class HetconsQuorumStatus {

    /* The name of the chain that own this quorum */
    private String chainName;

    /* The observer that owns this chain */
    private CryptoId owner;

    /* The quorum that the consensus will use to make decision */
    private HetconsObserverQuorum mainQuorum;

    /* These are helper quorums which might be cross-referenced to other chains' quorums */
    private Map<String, HetconsObserverQuorum> otherQuorums;

    /**
     * Constructor for a quorum status
     * @precondition there is one and only one main quorum in the quorum list
     * @param quorums a list of quorums
     */
    public HetconsQuorumStatus(List<HetconsObserverQuorum> quorums,
                               String chainName) {
        otherQuorums = new HashMap<>();
        this.chainName = chainName;
        for (HetconsObserverQuorum q : quorums) {
            if (q.getMain())
                mainQuorum = q;
            else
                otherQuorums.put(q.getName(), q);
        }
    }

    public HetconsObserverQuorum getMainQuorum() {
        return mainQuorum;
    }

    public HetconsObserverQuorum getSubQuorum(String name) {
        return otherQuorums.get(name);
    }

    public String getChainName() {
        return chainName;
    }

    public Set<CryptoId> getParticipants() {
       Set<CryptoId> ids = new HashSet<>();
        ids.addAll(mainQuorum.getMembersList());
        otherQuorums.values().forEach(e -> ids.addAll(e.getMembersList()));
        return ids;
    }
}
