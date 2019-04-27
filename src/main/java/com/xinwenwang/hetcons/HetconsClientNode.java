package com.xinwenwang.hetcons;

import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.node.CharlotteNodeClient;
import com.isaacsheff.charlotte.node.HashUtil;
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


    public void send1a(HetconsProposal proposal, Hash blockhash) {
        HetconsMessage1a message1a = HetconsMessage1a.newBuilder()
                .setProposal(proposal).build();

        HetconsMessage message = HetconsMessage.newBuilder()
                .setM1A(message1a).setType(HetconsMessageType.M1a)
                .setObserverGroupReferecne(Reference.newBuilder().setHash(blockhash).build())
                .build();

        HetconsBlock block = HetconsBlock.newBuilder()
                .setHetconsMessage(message)
                .setSig(SignatureUtil.signBytes(this.config.getKeyPair(), message))
                .build();
        sendBlock(SendBlocksInput.newBuilder().setBlock(Block.newBuilder()
                .setHetconsBlock(block).build())
                .build()
        );
    }

    public Hash broadcastOberverGroup(HetconsObserverGroup observerGroup) {
        HetconsMessage message = HetconsMessage.newBuilder()
                .setObserverGroup(observerGroup)
                .setType(HetconsMessageType.OBSERVERGROUP)
                .build();
        HetconsBlock hetconsBlock = HetconsBlock.newBuilder()
                .setHetconsMessage(message)
                .setSig(SignatureUtil.signBytes(this.config.getKeyPair(), message))
                .build();
        Block block = Block.newBuilder().setHetconsBlock(hetconsBlock).build();
        sendBlock(block);
        return HashUtil.sha3Hash(block);
    }

    public void propose(List<IntegrityAttestation.ChainSlot> slots,
                        HetconsValue value, HetconsBallot ballot,
                        HetconsObserverGroup observerGroup,
                        long timeout) {

        HetconsProposal proposal = HetconsUtil.buildProposal(slots, value, ballot, timeout);
        Hash blockhash = broadcastOberverGroup(observerGroup);
        send1a(proposal, blockhash);
    }
}
