package com.xinwenwang.hetcons;

import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.*;

import java.util.*;

public class HetconsUtil {

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
        return Long.toString(new Date().getTime()) + bytes2Hex(HashUtil.sha3(value));
    }

    public static HetconsBallot buildBallot(HetconsValue value) {
        return HetconsBallot.newBuilder().setBallotSequence(buildBallotString(value)).build();
    }

    public static boolean isSameProposal(HetconsProposal proposal1, HetconsProposal proposal2) {
        return proposal1.getSlotsList().equals(proposal2.getSlotsList()) && proposal1.getValue().equals(proposal2.getValue());
    }

    // TODO: move following methods to HetconsUtil class
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

    public static int ballotCompare(HetconsBallot b1, HetconsBallot b2) {
        return b1.getBallotSequence().compareTo(b2.getBallotSequence());
    }

}
