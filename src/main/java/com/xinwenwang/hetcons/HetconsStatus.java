package com.xinwenwang.hetcons;


import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.proto.*;

import java.lang.reflect.Array;
import java.util.*;

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
    private HashMap<String, Boolean> participantResponsed;
    private HashMap<String, Boolean> participantM2BResponsed;

    public HetconsStatus(HetconsConsensusStage stage, HetconsProposal proposal) {
        this.stage = stage;
        this.proposals = new LinkedList<HetconsProposal>();
        if (proposal != null)
            proposals.add(proposal);
        participants = new HashMap<>();
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
        this.proposals.add(proposal);
        if (proposals.size() > 1)
            reset();
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
        HetconsMessage2ab oldMessage = latestMessage2a.putIfAbsent(cryptoIdToString(observer), message2ab);
        if (oldMessage != null) {
            if (oldMessage.getProposal().getBallot().getBallotNumber() < message2ab.getProposal().getBallot().getBallotNumber()) {
                latestMessage2a.put(cryptoIdToString(observer), message2ab);
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
        HetconsMessage2ab message2ab = latestMessage2a.get(cryptoIdToString(observer));
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
                    participants.putIfAbsent(cryptoIdToString(id), new ArrayList<HetconsObserverQuorum>());
                    participants.get(cryptoIdToString(id)).add(quorum);
                    participantResponsed.put(cryptoIdToString(id), Boolean.FALSE);
                    participantM2BResponsed.put(cryptoIdToString(id), Boolean.FALSE);
                    participantIds.putIfAbsent(cryptoIdToString(id), id);
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
        participantResponsed = new HashMap<>();
        participantM2BResponsed = new HashMap<>();
        participantIds = new HashMap<>();
    }

    /**
     *
     * @param id
     * @param
     * @return
     */
    public ArrayList<HetconsObserverQuorum> receive1b(CryptoId id, HetconsMessage1b message1b) {

        ArrayList<HetconsObserverQuorum> validQuorums = new ArrayList<>();
        if (participantResponsed.containsKey(cryptoIdToString(id)) && !participantResponsed.get(cryptoIdToString(id))) {
            participantResponsed.put(cryptoIdToString(id), Boolean.TRUE);
            participants.get(cryptoIdToString(id)).forEach(quorum -> {
                quorumOf1bsValues.get(quorum).add(message1b.getValue());
                quorumOf1bs.get(quorum).add(HashUtil.sha3Hash(message1b));
                if (quorumOf1bs.get(quorum).size() == quorum.getSize()) {
                    validQuorums.add(quorum);
                    quorumOf1bsValues.get(quorum).forEach(value -> {
                        if (!value.equals(message1b.getValue())) {
                            validQuorums.remove(quorum);
                            quorumOf1bs.get(quorum).clear();
                            quorumOf1bsValues.get(quorum).clear();
                        }
                    });
                }
            });
        }
        return validQuorums;
    }

    public ArrayList<HetconsObserverQuorum> receive2b(CryptoId id, HetconsMessage2ab message2b) {

        ArrayList<HetconsObserverQuorum> validQuorums = new ArrayList<>();
        if (participantM2BResponsed.containsKey(cryptoIdToString(id)) && !participantM2BResponsed.get(cryptoIdToString(id))) {
            participantM2BResponsed.put(cryptoIdToString(id), Boolean.TRUE);
            participants.get(cryptoIdToString(id)).forEach(quorum -> {
                quorumOf2bsValues.get(quorum).add(message2b.getValue());
                quorumOf2bs.get(quorum).add(HashUtil.sha3Hash(message2b));
                if (quorumOf2bs.get(quorum).size() == quorum.getSize()) {
                    validQuorums.add(quorum);
                    quorumOf2bsValues.get(quorum).forEach(value -> {
                        if (!value.equals(message2b.getValue())) {
                            validQuorums.remove(quorum);
                            quorumOf2bs.get(quorum).clear();
                            quorumOf2bsValues.get(quorum).clear();
                        }
                    });
                }
            });
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

    public static String cryptoIdToString(CryptoId id) {
        String ret = id.toString();
        if (id.hasHash()) {
            ret = id.getHash().getSha3().toStringUtf8();
        } else if (id.hasPublicKey()) {
            ret = id.getPublicKey().getEllipticCurveP256().getByteString().toStringUtf8();
        }
        return bytes2Hex(ret.getBytes());
    }

    public static String bytes2Hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%x", b));
        }
        return sb.toString();
    }
}
