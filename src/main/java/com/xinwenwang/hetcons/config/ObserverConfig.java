package com.xinwenwang.hetcons.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.HetconsObserver;
import com.isaacsheff.charlotte.proto.PublicKey;
import com.isaacsheff.charlotte.yaml.Contact;
import com.isaacsheff.charlotte.yaml.JsonContact;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ObserverConfig {

    @JsonProperty("self") private JsonContact self;

    @JsonProperty("quorums") private List<QuorumConfig> quorums;

    @JsonCreator
    public ObserverConfig(@JsonProperty("self") JsonContact self,
                          @JsonProperty("quorums") List<QuorumConfig> quorums) {
        this.self = self;
        this.quorums = quorums;
    }

    public JsonContact getSelf() {
        return self;
    }

    public List<QuorumConfig> getQuorums() {
        return quorums;
    }

    public HetconsObserver toHetconsObserver(Path path) {

        Contact contact = new Contact(self, path, null);

        return HetconsObserver.newBuilder().setId(contact.getCryptoId())
                .addAllQuorums(quorums.stream().map(quorumConfig -> {
                    return quorumConfig.toHetconsObserverQuorum(contact.getCryptoId(), path);
                }).collect(Collectors.toList()))
                .build();
    }
}
