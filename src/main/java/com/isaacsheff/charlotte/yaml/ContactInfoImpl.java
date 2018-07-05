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

public class ContactInfoImpl implements ContactInfo {
  private static final Logger logger = Logger.getLogger(ContactInfoImpl.class.getName());


  @JsonIgnore private final byte[] x509;
  @JsonProperty("x509") private final String x509FileName;
  private final String url;
  private final int port;

  @JsonCreator
  public ContactInfoImpl(
      @JsonProperty("x509") String x509FileName,
      @JsonProperty("url") String url,
      @JsonProperty("port") int port
      ) {
    this.url = url;
    this.port = port;
    this.x509FileName = x509FileName;
    byte[] x509Bytes = null;
    try {
      InputStream read = new FileInputStream(getX509FileName());
      x509Bytes = read.readAllBytes();
      read.close();
    } catch (FileNotFoundException e) {
      logger.log(Level.SEVERE, "x509 file not found: " + getX509FileName(), e);
    } catch (SecurityException e) {
      logger.log(Level.SEVERE, "Don't have permission to read x509: " + getX509FileName(), e);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "There was an Exception reading x509: " + getX509FileName(), e);
    } catch (OutOfMemoryError e) {
      logger.log(Level.SEVERE, "Ran out of memory while reading x509: " + getX509FileName(), e);
    }
    this.x509 = x509Bytes;
  }

  @JsonIgnore public byte[] getX509() {return this.x509;}

  public String getX509FileName() {return this.x509FileName;}

  public String getUrl() {return this.url;}

  public int getPort() {return this.port;}

}
