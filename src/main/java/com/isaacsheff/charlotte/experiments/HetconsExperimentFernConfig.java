package com.isaacsheff.charlotte.experiments;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.isaacsheff.charlotte.yaml.JsonConfig;
import com.isaacsheff.charlotte.yaml.JsonContact;
import com.xinwenwang.hetcons.config.HetconsConfig;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HetconsExperimentFernConfig extends JsonConfig {


    @JsonCreator
    public HetconsExperimentFernConfig(
            @JsonProperty("privatekey") String privatekey,
            @JsonProperty("me") String me,
            @JsonProperty("contacts") Map<String, JsonContact> contacts
    ) {
        super(privatekey, me, contacts);
    }
}
