package com.isaacsheff.charlotte.experiments;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.isaacsheff.charlotte.yaml.JsonContact;
import com.xinwenwang.hetcons.config.HetconsConfig;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HetconsExperimentFernConfig extends JsonExperimentConfig {


    @JsonCreator
    public HetconsExperimentFernConfig(
            @JsonProperty("fernservers") List<String> fernServers,
            @JsonProperty("blocksperexperiment") int blocksPerExperiment,
            @JsonProperty("privatekey") String privatekey,
            @JsonProperty("me") String me,
            @JsonProperty("contacts") Map<String, JsonContact> contacts
    ) {
        super(fernServers, blocksPerExperiment, privatekey, me, contacts);
    }
}
