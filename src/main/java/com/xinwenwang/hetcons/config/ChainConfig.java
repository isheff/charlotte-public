package com.xinwenwang.hetcons.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.isaacsheff.charlotte.proto.HetconsObserverGroup;
import com.isaacsheff.charlotte.yaml.JsonContact;

import java.util.List;
import java.util.stream.Collectors;

public class ChainConfig {

    @JsonProperty("id") private String id;

    @JsonProperty("observers") private List<ObserverConfig> observers;

    @JsonCreator
    public ChainConfig(
            @JsonProperty("id") String id,
            @JsonProperty("observers") List<ObserverConfig> observers
    ) {
        this.observers = observers;
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public List<ObserverConfig> getObservers() {
        return observers;
    }

    public HetconsObserverGroup getObserverGroup() {
        return HetconsObserverGroup.newBuilder()
                .addAllObservers(observers.stream().map(observerConfig -> {
                  return observerConfig.toHetconsObserver();
                }).collect(Collectors.toList()))
                .build();
    }
}
