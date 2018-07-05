package com.isaacsheff.charlotte.yaml;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class Contact {
  private static final Logger logger = Logger.getLogger(Contact.class.getName());

  private final JsonContact jsonContact;
  private final byte[] x509Bytes;
  private final SslContext sslContext;

  public Contact(JsonContact jsonContact, Path path) {
    this.jsonContact = jsonContact;
    if (null == getJsonContact()) {
      logger.log(Level.WARNING, "Creating a Contact with null json / yaml information. Things may break.");
    }
    this.x509Bytes = readFile("x509", path.resolve(getX509()));
    this.sslContext = getContext(getX509Stream());
  }

  public JsonContact getJsonContact() {return this.jsonContact;}
  public String getUrl() {return getJsonContact().getUrl();}
  public int getPort() {return getJsonContact().getPort();}
  public String getX509() {return getJsonContact().getX509();}
  public byte[] getX509Bytes() {return this.x509Bytes;}
  public ByteArrayInputStream getX509Stream() {return (new ByteArrayInputStream(getX509Bytes()));}
  public SslContext getSslContext() {return this.sslContext;}
  public NettyChannelBuilder getChannelBuilder() {return NettyChannelBuilder.forAddress(getUrl(),getPort());}
  public ManagedChannel getManagedChannel() {
    return getChannelBuilder().useTransportSecurity().enableRetry().sslContext(getSslContext()).build();
  }



  public static byte[] readFile(String description, Path filename) {
    byte[] bytes = null;
    try {
      InputStream read = new FileInputStream(filename.toFile());
      bytes = read.readAllBytes();
      read.close();
    } catch (FileNotFoundException e) {
      logger.log(Level.SEVERE, description + " file not found: " + filename, e);
    } catch (SecurityException e) {
      logger.log(Level.SEVERE, "Don't have permission to read " + description + ": " + filename, e);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "There was an Exception reading " + description + ": " + filename, e);
    } catch (OutOfMemoryError e) {
      logger.log(Level.SEVERE, "Ran out of memory while reading " + description + ": " + filename, e);
    }
    return bytes;
  }

  private static SslContext getContext(InputStream cert) {
    SslContext context = null;
    try {
      context = GrpcSslContexts.forClient().trustManager(cert).build();
    } catch (Exception e) {
      logger.log(Level.WARNING, "Something went wrong setting trust manager. Maybe your cert is off.", e);
    }
    return context;
  }
}
