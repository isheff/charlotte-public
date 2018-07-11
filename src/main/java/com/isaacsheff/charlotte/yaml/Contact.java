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
import java.security.Security;

/**
 * Represents the contact information for a CharlotteNode server, as stored in a config file.
 * These contacts are expected to have a URL, TCP Port, and X509 certificate (public key).
 * This is in some sense a wrapper around JsonConfig, which is literally what you get
 *  when you parse a config file's contact elements.
 * However, creating a Contact object checks a bunch of stuff, like reading and parsing the X509 cert file.
 * @author Isaac Sheff
 */
public class Contact {
  /**
   * This line is required to use bouncycastle encryption libraries.
   */
  static {Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());}

  /**
   * Use this for logging events in the Contact class.
   */
  private static final Logger logger = Logger.getLogger(Contact.class.getName());

  /**
   * The literal data parsed from a contact in a config file.
   */
  private final JsonContact jsonContact;

  /**
   * The literal bytes of the x509 certificate file.
   */
  private final byte[] x509Bytes;

  /**
   * An Ssl configuration in which the X509 certificate file for this contact is trusted.
   * Used in opening secure channels to talk to the server this contact represents.
   */
  private final SslContext sslContext;

  /**
   * The X509 certificate parsed as a PublicKey for crypto libraries. 
   * Uses BouncyCastle.
   */
  private final PublicKey publicKey;

  /**
   * Generate a Contact using a JsonContact.
   *  (which was parsed from a config file),
   * and the path representing the dir in which the config file was located
   *  (to resolve relative filenames).
   * If the JsonContact is null, we log warnings, but do not actually crash immediately.
   * These contacts are expected to have a URL, TCP Port, and X509 certificate (public key).
   * @param jsonContact was parsed from a config file
   * @param path the path representing the dir in which the config file was located
   */
  public Contact(JsonContact jsonContact, Path path) {
    this.jsonContact = jsonContact;
    if (null == getJsonContact()) {
      logger.log(Level.WARNING, "Creating a Contact with null json / yaml information. Things may break.");
    }
    this.x509Bytes = readFile("x509", path.resolve(getX509()));
    this.sslContext = getContext(getX509Stream());
    this.publicKey = generatePublicKey();
  }

  /**
   * @return the PublicKey parsed from the X509 certificate
   */
  public PublicKey getPublicKey() {return publicKey;}

  /**
   * @return the JsonContact (from which this Contact was made) parsed from the config file
   */
  public JsonContact getJsonContact() {return this.jsonContact;}

  /**
   * @return the url string for finding the server this Contact represents
   */
  public String getUrl() {return getJsonContact().getUrl();}

  /**
   * @return the TCP port number for the server this Contact represents
   */
  public int getPort() {return getJsonContact().getPort();}

  /**
   * @return the filename of the X509 certificate, relative to the config file.
   */
  public String getX509() {return getJsonContact().getX509();}

  /**
   * @return The literal bytes of the x509 certificate file.
   */
  public byte[] getX509Bytes() {return this.x509Bytes;}

  /**
   * @return An InputStream which reads the literal bytes of the x509 certificate file.
   */
  public ByteArrayInputStream getX509Stream() {return (new ByteArrayInputStream(getX509Bytes()));}

  /**
   * @return A Reader which reads the literal bytes of the x509 certificate file.
   */
  public InputStreamReader getX509Reader() {return (new InputStreamReader(getX509Stream()));}

  /**
   * Used in opening secure channels to talk to the server this contact represents.
   * @return An Ssl configuration in which the X509 certificate file for this contact is trusted.
   */
  public SslContext getSslContext() {return this.sslContext;}

  /**
   * Used in opening channels to talk to the server this contact represents.
   * @return A ChannelBuilder for this contact's url and port
   */
  public NettyChannelBuilder getChannelBuilder() {return NettyChannelBuilder.forAddress(getUrl(),getPort());}

  /**
   * Create a Managed Channel talking to the server this Contact describes.
   * It uses:
   * <ul>
   *   <li>TLS using the X509 certificate in this Contact</li>
   *   <li>Automatic Retry (as implemented in NettyChannel objects</li>
   * </ul>
   * @return A Managed Channel talking to the server this Contact describes.
   */
  public ManagedChannel getManagedChannel() {
    return getChannelBuilder().useTransportSecurity().enableRetry().sslContext(getSslContext()).build();
  }

  /**
   * Generate a PublicKey from the PEM X509 file read in this Contact.
   * This parsing could go wrong and log SEVERE things, and then return null.
   * This will be run in the constructor.
   * @return PublicKey from the PEM X509 file read in this Contact.
   */
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

  /**
   * Read the PEM X509 file read in this Contact.
   * This could go wrong and log SEVERE things, and then return null.
   * This will be run in the constructor.
   * @return bytes from the PEM X509 file read in this Contact.
   */
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

  /**
   * Generate an Ssl configuration in which the X509 certificate file for this contact is trusted.
   * This configuration will be used in opening secure channels to talk to the server this contact
   *  represents.
   * This could go wrong and log WARNING things, and then return null.
   * This will be run in the constructor.
   * @return an Ssl configuration in which the X509 certificate file for this contact is trusted.
   */
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
