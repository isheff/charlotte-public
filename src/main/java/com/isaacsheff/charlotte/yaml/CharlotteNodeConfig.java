package com.isaacsheff.charlotte.yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.HashMap;
import java.util.Map;

public class CharlotteNodeConfig extends HashMap<String, ContactInfoImpl>
                                 implements ContactInfo {

  private static final long serialVersionUID = 201807051512L; // the current date and time

  /**
   * Use logger for logging events involving CharlotteNodeConfig.
   */
  private static final Logger logger = Logger.getLogger(CharlotteNodeConfig.class.getName());

  @JsonIgnore private final ContactInfo me;
  @JsonIgnore private final byte[] privateKey;
  @JsonProperty("privatekey") private final String privateKeyFileName;

  @JsonCreator
  public CharlotteNodeConfig(
      @JsonProperty("contacts")   Map<String, ContactInfoImpl> contacts,
      @JsonProperty("me")         String                       me,
      @JsonProperty("privatekey") String                       privateKeyFileName
      ) {
    super(contacts);
    this.privateKeyFileName = privateKeyFileName;
    byte[] privateKeyBytes = null;
    try {
      InputStream read = new FileInputStream(getPrivateKeyFileName());
      privateKeyBytes = read.readAllBytes();
      read.close();
    } catch (FileNotFoundException e) {
      logger.log(Level.SEVERE, "Private Key file file not found: " + getPrivateKeyFileName(), e);
    } catch (SecurityException e) {
      logger.log(Level.SEVERE, "Don't have permission to read Private Key file: " + getPrivateKeyFileName(), e);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "There was an Exception reading Private Key file: " + getPrivateKeyFileName(), e);
    } catch (OutOfMemoryError e) {
      logger.log(Level.SEVERE, "Ran out of memory while reading Private Key file: " + getPrivateKeyFileName(), e);
    }
    this.privateKey = privateKeyBytes;

    this.me = this.get(me);
    if (this.me == null) {
      logger.log(Level.SEVERE, "Could not find my own contact info: " + me );
    }
    logger.info("created CharlotteNodeConfig for " + getUrl() + ":" + getPort());
  }

  public ContactInfo getMe() {return this.me;}
  public HashMap<String, ContactInfoImpl> getContacts() {return this;}
  @JsonIgnore public byte[] getPrivateKey() {return this.privateKey;}
  public String getPrivateKeyFileName() {return this.privateKeyFileName;}

  @JsonIgnore public byte[] getX509() {return getMe().getX509();}
  public String getX509FileName() {return getMe().getX509FileName();}
  public String getUrl() {return getMe().getUrl();}
  public int getPort() {return getMe().getPort();}

}
