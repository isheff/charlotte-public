package com.isaacsheff.charlotte.yaml;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This is what you get when Jackson parses a CharlotteNode config file.
 * It stores a bunch of contacts (other server contact info) each identified by name, 
 *  which name corresponds to this server, and a filename of this server's private key.
 * In theory the config file can be JSON, XML, or YAML, but we'll use YAML.
 * @author Isaac Sheff
 */
public class JsonConfig {

  /**
   * filename of this server's private key (a PEM file), relative to the config file
   */
  @JsonProperty("privatekey") private final String privatekey;

  /**
   * which of the contacts corresponds to this server (public key, url, etc)?
   */
  @JsonProperty("me") private final String me;

  /**
   * A map of String "names" of known other servers to contact information for each.
   */
  @JsonProperty("contacts") private final Map<String, JsonContact> contacts;

  /**
   * This constructor is meant to be used by Jackson when it's parsing a config file.
   * @param privatekey the filename of the private key (PEM file) relative to the config file
   * @param me which of the named contacts is this server
   * @param contacts a map fo names and contact info of other servers in the system
   */
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

  /**
   * @return filename of this server's private key (a PEM file), relative to the config file
   */
  @JsonProperty("privatekey") public String getPrivateKey() {return this.privatekey;}

  /**
   * @return which of the contacts corresponds to this server (public key, url, etc)?
   */
  @JsonProperty("me") public String getMe() {return this.me;}

  /**
   * @return A map of String "names" of known other servers to contact information for each.
   */
  @JsonProperty("contacts") public Map<String, JsonContact> getContacts() {return this.contacts;}
}
