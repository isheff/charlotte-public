package com.xinwenwang.hetcons;

import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.proto.*;

import java.util.Date;
import java.util.List;

public class HetconsUtil {

    public static HetconsProposal buildProposal(List<IntegrityAttestation.ChainSlot> slots, HetconsValue value, HetconsBallot ballot) {
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
                        .setValue(value);

        for (IntegrityAttestation.ChainSlot slot : slots) {
            builder.addSlots(slot);
        }
        return builder.build();
    }

    public static boolean isSameProposal(HetconsProposal proposal1, HetconsProposal proposal2) {
        return proposal1.getSlotsList().equals(proposal2.getSlotsList()) && proposal1.getValue().equals(proposal2.getValue());
    }
}
