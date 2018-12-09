package com.xinwenwang.hetcons.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.HetconsObserverQuorum;
import com.isaacsheff.charlotte.yaml.Contact;
import com.isaacsheff.charlotte.yaml.JsonContact;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class QuorumConfig {

    /* The name of this quorum config */
    @JsonProperty("name")
    String name;

    /* Whether this will be used as the main config of the quorum */
    @JsonProperty("main")
    boolean main;

    /* Participant nodes in JsonContact format */
    @JsonProperty("participants")
    List<JsonContact> participants;

    @JsonProperty("specs")
    List<QuorumSpecConfig> specs;


    public QuorumConfig(@JsonProperty("name") String name,
                        @JsonProperty("main") boolean main,
                        @JsonProperty("specs") List<QuorumSpecConfig> specs,
                        @JsonProperty("participants") List<JsonContact> participants) {
        this.participants = participants;
        this.main = main;
        this.name = name;
        this.specs = specs;
    }

    private static HetconsObserverQuorum.Spec apply(QuorumSpecConfig s) {
        return HetconsObserverQuorum.Spec.newBuilder()
                .setSize(s.size)
                .setBase(s.base)
                .build();
    }

    /**
     * Parse and return this config file to a HetconsObserverQuorum Object.
     * @param owner the owner(Observer) of current quorum
     * @param path the directory that contains the key-pairs
     * @return a HetconsObserverQuorum that parsed from config file
     */
    public HetconsObserverQuorum toHetconsObserverQuorum(CryptoId owner, Path path) {
        return HetconsObserverQuorum.newBuilder()
                .setOwner(owner)
                .addAllSpecs(specs.stream().map(QuorumConfig::apply).collect(Collectors.toList()))
                .addAllMembers(participants.stream().map(jsonContact -> new Contact(jsonContact, path, null).getCryptoId())
                        .collect(Collectors.toList()))
                .setMain(main)
                .setName(name)
                .build();
    }

    public List<JsonContact> getParticipants() {
        return participants;
    }
}
