package com.isaacsheff.charlotte.yaml;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class JsonConfig {

  @JsonProperty("privatekey") private final String privatekey;
  @JsonProperty("me") private final String me;
  @JsonProperty("contacts") private final Map<String, JsonContact> contacts;

  @JsonCreator
  public JsonConfig(
      @JsonProperty("privatekey") String privatekey,
      @JsonProperty("me") String me,
      @JsonProperty("contacts") Map<String, JsonContact> contacts
      ) {
    this.privatekey = privatekey;
    this.me = me;
    this.contacts = contacts;
  }

  @JsonProperty("privatekey") public String getPrivateKey() {return this.privatekey;}
  @JsonProperty("me") public String getMe() {return this.me;}
  @JsonProperty("contacts") public Map<String, JsonContact> getContacts() {return this.contacts;}
}
