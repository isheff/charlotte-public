package com.isaacsheff.charlotte.yaml;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents contact information for a server (url, port, X509 cert).
 * This is meant to be used with Jackson XML / JSON / YAML parser.
 * Really, I'm just going to use YAML.
 * These contacts are expected to have a URL, TCP Port, and X509 certificate (public key).
 * @author Isaac Sheff
 */
public class JsonContact {

  /** The filename of the x509 certificate. */
  @JsonProperty("x509") private final String x509;

  /** Some kind of identifier for the server, maybe an IP, maybe a DNS thing, whatever. */
  @JsonProperty("url") private final String url;
   
  /** The TCP port we should try and call this server on */
  @JsonProperty("port") private final int port;

  /**
   * Create a new JsonContact.
   * This is meant to be used by the Jackson parser.
   * These contacts are expected to have a URL, TCP Port, and X509 certificate (public key).
   * @param x509 the filename of the x509 certificate, relative to the location of the config file.
   * @param url Some kind of identifier for the server, maybe an IP, maybe a DNS thing, whatever. 
   * @param port The TCP port we should try and call this server on 
   */
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

  /** @return the filename of the x509 certificate of this Contact, relative to the config file. */
  @JsonProperty("x509") public String getX509() {return this.x509;}

  /** @return the Url or ip address or whatever of this contact */
  @JsonProperty("url") public String getUrl() {return this.url;}

  /** @return the TCP port of this contact */
  @JsonProperty("port") public int getPort() {return this.port;}
}
