package com.xinwenwang.hetcons.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.isaacsheff.charlotte.proto.HetconsObserverGroup;
import com.isaacsheff.charlotte.yaml.JsonContact;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ChainConfig {

    @JsonProperty("root") private List<String> roots;

    @JsonProperty("observers") private List<ObserverConfig> observers;

    @JsonCreator
    public ChainConfig(
            @JsonProperty("root") List<String> root,
            @JsonProperty("observers") List<ObserverConfig> observers
    ) {
        this.observers = observers;
        this.roots = root;
    }

    public List<String> getRoot() {
        return roots;
    }

    public List<ObserverConfig> getObservers() {
        return observers;
    }

    public HetconsObserverGroup getObserverGroup(Path dir) {
        return HetconsObserverGroup.newBuilder()
                .addAllObservers(observers.stream().map(observerConfig -> {
                  return observerConfig.toHetconsObserver(dir);
                }).collect(Collectors.toList()))
                .build();
    }
}
