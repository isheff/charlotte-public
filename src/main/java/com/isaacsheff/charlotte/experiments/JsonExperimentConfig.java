package com.isaacsheff.charlotte.experiments;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.isaacsheff.charlotte.yaml.JsonConfig;
import com.isaacsheff.charlotte.yaml.JsonContact;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true) // if there are random other fields at the top level of config, just ignore them
public class JsonExperimentConfig extends JsonConfig {

  /** the set of fern servers this client should talk to */
  @JsonProperty("agreementchainclientfernservers") 
  private final List<String> agreementChainClientFernServers;

  /** @return the set of fern servers this client should talk to */
  public List<String> getAgreementChainClientFernServers() {return agreementChainClientFernServers;}
  /**
   * This constructor is meant to be used by Jackson when it's parsing a config file.
   * @param agreementChainClientFernServers the set of fern servers this client should talk to
   * @param privatekey the filename of the private key (PEM file) relative to the config file
   * @param me which of the named contacts is this server
   * @param contacts a map fo names and contact info of other servers in the system
   */
  @JsonCreator
  public JsonExperimentConfig(
      @JsonProperty("agreementchainclientfernservers") List<String> agreementChainClientFernServers,
      @JsonProperty("privatekey") String privatekey,
      @JsonProperty("me") String me,
      @JsonProperty("contacts") Map<String, JsonContact> contacts
      ) {
    super(privatekey, me, contacts);
    this.agreementChainClientFernServers = agreementChainClientFernServers;
  }
}


