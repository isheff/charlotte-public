package com.isaacsheff.charlotte.yaml;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.security.PublicKey;

public class Contact {
  private static final Logger logger = Logger.getLogger(Contact.class.getName());

  private final JsonContact jsonContact;
  private final byte[] x509Bytes;
  private final SslContext sslContext;
  private final PublicKey publicKey;

  public Contact(JsonContact jsonContact, Path path) {
    this.jsonContact = jsonContact;
    if (null == getJsonContact()) {
      logger.log(Level.WARNING, "Creating a Contact with null json / yaml information. Things may break.");
    }
    this.x509Bytes = readFile("x509", path.resolve(getX509()));
    this.sslContext = getContext(getX509Stream());
    this.publicKey = generatePublicKey();
  }

  public PublicKey getPublicKey() {return publicKey;}
  public JsonContact getJsonContact() {return this.jsonContact;}
  public String getUrl() {return getJsonContact().getUrl();}
  public int getPort() {return getJsonContact().getPort();}
  public String getX509() {return getJsonContact().getX509();}
  public byte[] getX509Bytes() {return this.x509Bytes;}
  public ByteArrayInputStream getX509Stream() {return (new ByteArrayInputStream(getX509Bytes()));}
  public InputStreamReader getX509Reader() {return (new InputStreamReader(getX509Stream()));}
  public SslContext getSslContext() {return this.sslContext;}
  public NettyChannelBuilder getChannelBuilder() {return NettyChannelBuilder.forAddress(getUrl(),getPort());}
  public ManagedChannel getManagedChannel() {
    return getChannelBuilder().useTransportSecurity().enableRetry().sslContext(getSslContext()).build();
  }

  private PublicKey generatePublicKey() {
    PublicKey publicKey = null;
    Reader reader = getX509Reader();
    PEMParser parser= new PEMParser(reader);
    try {
      Object object = parser.readObject();
      if (!(object instanceof SubjectPublicKeyInfo)) {
      }
      SubjectPublicKeyInfo publicKeyInfo = (SubjectPublicKeyInfo) object;
      JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
      publicKey = converter.getPublicKey(publicKeyInfo);
    } catch (PEMException e) {
      logger.log(Level.SEVERE, "X509 cert file could not be parsed as PEM", e);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "PEM converter could not pull a PublicKeyInfo from the X509 PEM", e);
    }
    try {
      parser.close();
    } catch (IOException e) {
      logger.log(Level.WARNING,
          "PEM parser did not close properly, but we parsed everything, so it's probably ok.", e);
    }
    try {
      reader.close();
    } catch (IOException e) {
      logger.log(Level.WARNING,
        "X509 byte[] reader didn't close properly, but we parsed everything, so it's probably ok.", e);
    }
    return publicKey;
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
