package com.xinwenwang.hetcons.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.HetconsObserverQuorum;
import com.isaacsheff.charlotte.yaml.Contact;
import com.isaacsheff.charlotte.yaml.JsonContact;

import java.util.List;
import java.util.stream.Collectors;

public class QuorumConfig {


    @JsonProperty("participants")
    List<JsonContact> participants;

    public QuorumConfig(@JsonProperty("participants") List<JsonContact> participants) {
        this.participants = participants;
    }

    public List<JsonContact> getParticipants() {
        return participants;
    }

    public HetconsObserverQuorum toHetconsObserverQuorum(CryptoId owner) {
        return HetconsObserverQuorum.newBuilder()
                .setOwner(owner)
                .setSize(participants.size())
                .addAllMemebers(participants.stream().map(jsonContact -> {
                    return new Contact(jsonContact, HetconsConfig.configFileDirectory.toPath()).getCryptoId();
                }).collect(Collectors.toList()))
                .build();
    }
}
