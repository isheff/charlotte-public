package com.xinwenwang.hetcons;

import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.node.CharlotteNodeClient;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.Contact;

import java.util.Date;
import java.util.List;

public class ClientServer extends CharlotteNodeClient {


    public ClientServer(Contact contact) {
        super(contact);
    }

    public void propose(List<HetconsParticipatedAccountsInfo> accountsInfos, HetconsValue value) {
        long currentTime = new Date().getTime();
        int ballot = 0;
        HetconsProposal.Builder builder =
                HetconsProposal.newBuilder()
                .setBallotNumber(ballot)
                .setTime(HetconsTime.newBuilder().setVal(currentTime).build())
                .setProposalType(HetconsProposalType.BlockSlot)
                .setHashOfBallotNumberAndTime(Hash.newBuilder()
                        .setSha3(ByteString.copyFromUtf8(Long.toString(currentTime) + Integer.toString(ballot)))
                        .build()
                )
                .setValue(HetconsValue.newBuilder()
                        .setBlock(Block.newBuilder().build())
                );

        for (HetconsParticipatedAccountsInfo accountsInfo : accountsInfos) {
            builder.addAccounts(accountsInfo);
        }

        HetconsMessage message = HetconsMessage.newBuilder().setProposal(builder.build()).build();
        sendBlock(SendBlocksInput.newBuilder().setBlock(Block.newBuilder()
                .setHetconsMessage(message).build())
                .build()
        );
    }
}
