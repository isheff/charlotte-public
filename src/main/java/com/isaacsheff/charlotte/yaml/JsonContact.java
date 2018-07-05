package com.isaacsheff.charlotte.yaml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class JsonContact {

  @JsonProperty("x509") private final String x509;
  @JsonProperty("url") private final String url;
  @JsonProperty("port") private final int port;

  @JsonCreator
  public JsonContact(
      @JsonProperty("x509") String x509,
      @JsonProperty("url") String url,
      @JsonProperty("port") int port
      ) {
    this.url = url;
    this.port = port;
    this.x509 = x509;
  }

  @JsonProperty("x509") public String getX509() {return this.x509;}

  @JsonProperty("url") public String getUrl() {return this.url;}

  @JsonProperty("port") public int getPort() {return this.port;}

}
