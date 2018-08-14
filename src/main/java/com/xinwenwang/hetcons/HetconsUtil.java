package com.xinwenwang.hetcons;

import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.proto.*;

import java.util.Date;
import java.util.List;

public class HetconsUtil {

    public static HetconsProposal buildProposal(List<HetconsParticipatedAccountsInfo> accountsInfos, HetconsValue value, int ballotNum) {
        long currentTime = new Date().getTime();
        int ballot = ballotNum;
        HetconsProposal.Builder builder =
                HetconsProposal.newBuilder()
                        .setBallotNumber(ballot)
                        .setTime(HetconsTime.newBuilder().setVal(currentTime).build())
                        .setProposalType(HetconsProposalType.BlockSlot)
                        .setHashOfBallotNumberAndTime(Hash.newBuilder()
                                .setSha3(ByteString.copyFromUtf8(Long.toString(currentTime) + Integer.toString(ballot)))
                                .build()
                        )
                        .setValue(value);

        for (HetconsParticipatedAccountsInfo accountsInfo : accountsInfos) {
            builder.addAccounts(accountsInfo);
        }
        return builder.build();
    }
}
