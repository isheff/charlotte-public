package com.isaacsheff.charlotte.experiments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.isaacsheff.charlotte.yaml.Contact;
import com.isaacsheff.charlotte.yaml.JsonContact;
import com.xinwenwang.hetcons.config.HetconsConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HetconsExperimentClientConfig extends JsonExperimentConfig {

    @JsonProperty("contactServer")
    private String contactServer;

    @JsonProperty("chainNames")
    private List<String> chainNames;

    @JsonProperty("timeout")
    private int timeout;

    @JsonProperty("startingIndex")
    private int startingIndex;

    @JsonProperty("doubleChainProbability")
    private float doubleChainProbability;

    @JsonProperty("doubleChainNames")
    private List<String> doubleChainNames;

    @JsonProperty("singleChainNames")
    private List<String> singleChainNames;

    public HetconsExperimentClientConfig(
            @JsonProperty("fernservers") List<String> fernServers,
            @JsonProperty("blocksperexperiment") int blocksPerExperiment,
            @JsonProperty("privatekey") String privatekey,
            @JsonProperty("me") String me,
            @JsonProperty("contacts") Map<String, JsonContact> contacts,
            @JsonProperty("contactServer") String contactServer,
            @JsonProperty("chainNames") List<String> chainNames,
            @JsonProperty("startingIndex") int startingIndex,
            @JsonProperty("timeout") int timeout,
            @JsonProperty("doubleChainProbability") float doubleChainProbability,
            @JsonProperty("doubleChainNames") List<String> doubleChainNames,
            @JsonProperty("singleChainNames") List<String> singleChainNames
    ) {
        super(fernServers,
                Collections.emptyList(),
                blocksPerExperiment,
                0,
                privatekey,
                me,
                contacts,
                0);
        this.contactServer = contactServer;
        this.chainNames = chainNames;
        this.timeout = timeout;
        this.startingIndex = startingIndex;
        this.doubleChainProbability = doubleChainProbability;
        this.singleChainNames = singleChainNames;
        this.doubleChainNames = doubleChainNames;
    }

    public String getContactServer() {
        return contactServer;
    }

    public List<String> getChainNames() {
        return chainNames;
    }

    public int getTimeout() {
        return timeout;
    }

    public int getStartingIndex() {
        return startingIndex;
    }

    public float getDoubleChainProbability() {
        return doubleChainProbability;
    }
}
