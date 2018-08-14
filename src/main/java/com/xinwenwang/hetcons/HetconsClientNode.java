package com.xinwenwang.hetcons;

import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.node.CharlotteNodeClient;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.Contact;

import java.util.Date;
import java.util.List;

public class HetconsClientNode extends CharlotteNodeClient {

    private Config config;


    public HetconsClientNode(Contact contact, Config self) {
        super(contact);
        this.config = self;
    }


    public void send1a(HetconsProposal proposal, HetconsObserverGroup observerGroup) {
        HetconsMessage1a message1a = HetconsMessage1a.newBuilder()
                .setProposal(proposal).build();

        HetconsMessage message = HetconsMessage.newBuilder()
                .setM1A(message1a).setType(HetconsMessageType.M1a)
                .setObserverGroup(observerGroup)
                .setSig(SignatureUtil.signBytes(this.config.getKeyPair(), message1a.toByteString()))
                .build();

        sendBlock(SendBlocksInput.newBuilder().setBlock(Block.newBuilder()
                .setHetconsMessage(message).build())
                .build()
        );
    }

    public void propose(List<HetconsParticipatedAccountsInfo> accountsInfos,
                        HetconsValue value, int ballot,
                        HetconsObserverGroup observerGroup) {
        HetconsProposal proposal = HetconsUtil.buildProposal(accountsInfos, value, ballot);
        send1a(proposal, observerGroup);
    }


}
