package com.isaacsheff.charlotte.experiments;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.isaacsheff.charlotte.yaml.Contact;
import com.isaacsheff.charlotte.yaml.JsonContact;
import com.xinwenwang.hetcons.config.HetconsConfig;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HetconsExperimentClientConfig extends JsonExperimentConfig {

    @JsonProperty("contactServer")
    private String contactServer;

    @JsonProperty("chainNames")
    private List<String> chainNames;

    public HetconsExperimentClientConfig(
            @JsonProperty("fernservers") List<String> fernServers,
            @JsonProperty("blocksperexperiment") int blocksPerExperiment,
            @JsonProperty("privatekey") String privatekey,
            @JsonProperty("me") String me,
            @JsonProperty("contacts") Map<String, JsonContact> contacts,
            @JsonProperty("contactServer") String contactServer,
            @JsonProperty("chainNames") List<String> chainNames

    ) {
        super(fernServers, blocksPerExperiment, privatekey, me, contacts);
        this.contactServer = contactServer;
        this.chainNames = chainNames;
    }

    public String getContactServer() {
        return contactServer;
    }

    public List<String> getChainNames() {
        return chainNames;
    }
}
