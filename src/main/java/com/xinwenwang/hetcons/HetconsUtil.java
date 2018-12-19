package com.xinwenwang.hetcons;

import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HetconsUtil {

    static ConcurrentHashMap<CryptoId, String> cryptoString = new ConcurrentHashMap<>();

    public static HetconsProposal buildProposal(List<IntegrityAttestation.ChainSlot> slots,
                                                HetconsValue value,
                                                HetconsBallot ballot,
                                                long timeout) {
        long currentTime = new Date().getTime();
        HetconsProposal.Builder builder =
                HetconsProposal.newBuilder()
                        .setBallot(ballot)
                        .setTime(HetconsTime.newBuilder().setVal(currentTime).build())
                        .setProposalType(HetconsProposalType.BlockSlot)
                        .setHashOfBallotNumberAndTime(Hash.newBuilder()
                                .setSha3(ByteString.copyFromUtf8(Long.toString(currentTime) + Long.toString(ballot.getBallotNumber())))
                                .build()
                        )
                        .setValue(value)
                        .setTimeout(timeout);

        for (IntegrityAttestation.ChainSlot slot : slots) {
            builder.addSlots(slot);
        }
        return builder.build();
    }

    public static String buildBallotString(HetconsValue value) {
        return Long.toString(new Date().toInstant().toEpochMilli()) + bytes2Hex(HashUtil.sha3(value));
    }

    public static HetconsBallot buildBallot(HetconsValue value) {
        return HetconsBallot.newBuilder().setBallotSequence(buildBallotString(value)).build();
    }

    public static boolean isSameProposal(HetconsProposal proposal1, HetconsProposal proposal2) {
        return proposal1.getSlotsList().equals(proposal2.getSlotsList()) && proposal1.getValue().equals(proposal2.getValue());
    }

    // TODO: move following methods to HetconsUtil class
    public static String cryptoIdToString(CryptoId id) {
        if (cryptoString.containsKey(id))
            return cryptoString.get(id);

        String ret = id.toString();
        if (id.hasHash()) {
            ret = id.getHash().getSha3().toStringUtf8();
        } else if (id.hasPublicKey()) {
            ret = id.getPublicKey().getEllipticCurveP256().getByteString().toStringUtf8();
        }
        cryptoString.put(id, ret);
//        return ret;
        return bytes2Hex(ret.getBytes());
    }

    public static String bytes2Hex(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
//        StringBuilder sb = new StringBuilder();
//        for (byte b : bytes) {
//            sb.append(String.format("%x", b));
//        }
//        return sb.toString();
    }


    /**
     * Return a string to identify a proposal, it is a concatenation of all chain slots, sorted in order,
     * involved in the proposal.
     * @param slots
     * @return a id for the proposal in string
     */
    public static String buildConsensusId(List<IntegrityAttestation.ChainSlot> slots) {
        StringBuilder builder = new StringBuilder();
        List<IntegrityAttestation.ChainSlot> slots2 = new ArrayList<>(slots);
        Collections.sort(slots2, Comparator.comparing(HetconsUtil::buildChainSlotID));
        for (IntegrityAttestation.ChainSlot slot : slots2) {
            builder.append(buildChainSlotID(slot));
        }
        return builder.toString();
    }

    /**
     * Return a string that represents a chain slot in a format of "root_hash_of_chain || slot_# ",
     * where || is string concatenation.
     * @param slot the slot to be use
     * @return a string id for chain slot
     */
    public static String buildChainSlotID(IntegrityAttestation.ChainSlot slot) {
        return String.format("%s%d", slot.getRoot().getHash().getSha3().toStringUtf8(), slot.getSlot());
    }

    public static String buildChainID(List<String> chainNames) {
        return chainNames.stream().sorted().reduce((a, n) -> a.concat(n)).get();
    }

    public static int ballotCompare(HetconsBallot b1, HetconsBallot b2) {
        return b1.getBallotSequence().compareTo(b2.getBallotSequence());
    }

    public static HetconsValue get1bValue(HetconsMessage1b m1b, CharlotteNodeService service) {
        return m1b.hasM2A() ? get2bValue(m1b.getM2A(), service) : getM1aFromReference(m1b.getM1ARef(), service).getProposal().getValue();
    }

    public static HetconsValue get2bValue(HetconsMessage2ab m2b, CharlotteNodeService service) {
        for (Reference r : m2b.getQuorumOf1Bs().getBlockHashesList()) {
            Block b2b = service.getBlock(r);
            if (b2b != null)
                return get1bValue(b2b.getHetconsBlock().getHetconsMessage().getM1B(), service);
        }
        return null;
    }

    public static HetconsMessage1a getM1aFromReference(Reference m1aRef, CharlotteNodeService service) {
        try {
            Block block = service.getBlock(m1aRef);
            return block.getHetconsBlock().getHetconsMessage().getM1A();
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

}
