package com.xinwenwang.hetcons;

import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.*;

import java.util.Date;
import java.util.List;

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
}
