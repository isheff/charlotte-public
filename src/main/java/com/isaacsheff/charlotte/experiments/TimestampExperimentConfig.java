package com.isaacsheff.charlotte.experiments;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.isaacsheff.charlotte.yaml.JsonContact;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true) // if there are random other fields at the top level of config, just ignore them
public class TimestampExperimentConfig extends JsonExperimentConfig {

  /** the number of blocks to receive before you should issue a timestamp **/
  @JsonProperty("timestampreferencesperattestation")
  private final int timestampReferencesPerAttestation;

  /** @return the number of blocks to append to the chain in the experiment **/
  public int getTimestampReferencesPerAttestation() {return timestampReferencesPerAttestation;}
  /**
   * This constructor is meant to be used by Jackson when it's parsing a config file.
   * @param timestampReferencesPerAttestation the number of blocks to receive before you should issue a timestamp 
   * @param agreementChainClientFernServers the set of fern servers this client should talk to
   * @param privatekey the filename of the private key (PEM file) relative to the config file
   * @param me which of the named contacts is this server
   * @param contacts a map fo names and contact info of other servers in the system
   */
  @JsonCreator
  public TimestampExperimentConfig (
      @JsonProperty("timestampreferencesperattestation") int timestampReferencesPerAttestation,
      @JsonProperty("fernservers") List<String> fernServers,
      @JsonProperty("blocksperexperiment") int blocksPerExperiment,
      @JsonProperty("privatekey") String privatekey,
      @JsonProperty("me") String me,
      @JsonProperty("contacts") Map<String, JsonContact> contacts
      ) {
    super(fernServers, blocksPerExperiment, privatekey, me, contacts);
    this.timestampReferencesPerAttestation = timestampReferencesPerAttestation;
  }
}


