package com.isaacsheff.charlotte.yaml;


import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId; import java.util.Date;
import java.util.ServiceConfigurationError;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;


/**
 * Static utility functions for generating X509 certificates and private keys.
 * Can be run as a utility program (has a main method):
 * GenerateX509 publicFileName privateFileName dnsName ipAddress
 * It will use Elliptic Curve keys with the P-256 curve.
 * Files will be PEM format.
 * The key files generated play nice with the rest of Charlotte.
 * Uses BouncyCastle.
 * @author Isaac Sheff
 */
public class GenerateX509 {
  /**
   * This line is required to use bouncycastle encryption libraries.
   */
  static {Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());}

  /**
   * Use logger for logging events in GenerateX509's utility functions.
   */
  private static final Logger logger = Logger.getLogger(GenerateX509.class.getName());

  /**
   * You can run GenerateX509 as a stand-alone to generate a pair of public/private keys for a dnsName with an IP address.
   * GenerateX509 publicFileName privateFileName dnsName ipAddress
   * It will use Elliptic Curve keys with the P-256 curve.
   * Files will be PEM format.
   * The key files generated play nice with the rest of Charlotte.
   * Uses BouncyCastle.
   * @param args the command-line arguments
   */
  public static void main(String[] args) {
    if (args.length < 4) {
      System.out.println("Correct Usage: GenerateX509 publicFileName privateFileName dnsName ipAddress");
    } else {
      generateKeyFiles(args[0], args[1], args[2], args[3]);
    }
  }

  /**
   * Writes to disk corresponding public and private key files.
   * It will use Elliptic Curve keys with the P-256 curve.
   * Files will be PEM format.
   * The key files generated play nice with the rest of Charlotte.
   * Uses BouncyCastle.
   * @param publicFile the filename of the public key file
   * @param privateFile the filename of the private key file
   * @param dnsName the dns name of the machine for which these keys apply
   * @param ipAddress the IP address for the machine for which these keys apply
   */
  public static void generateKeyFiles(String publicFile, String privateFile, String dnsName, String ipAddress) {
    generateKeyFiles(Paths.get(publicFile), Paths.get(privateFile), dnsName, ipAddress);
  }
  
  /**
   * Writes to disk corresponding public and private key files.
   * It will use Elliptic Curve keys with the P-256 curve.
   * Files will be PEM format.
   * The key files generated play nice with the rest of Charlotte.
   * Uses BouncyCastle.
   * @param publicFile the file path of the public key file
   * @param privateFile the file path of the private key file
   * @param dnsName the dns name of the machine for which these keys apply
   * @param ipAddress the IP address for the machine for which these keys apply
   */
  public static void generateKeyFiles(Path publicFile, Path privateFile, String dnsName, String ipAddress) {
    KeyPair keypair = generateDefaultKeyPair();
    writeByteArrayToFile(publicFile, getCertBytes(keypair, dnsName, ipAddress));
    writeByteArrayToFile(privateFile, getPrivateKeyFileBytes(keypair.getPrivate()));
  }
  
  /**
   * Write the byte[] given directly to a file.
   * @param path the filename to write
   * @param bytes the bytes which will be the new content of the file
   */
  public static void writeByteArrayToFile(String path, byte[] bytes) {
    writeByteArrayToFile(Paths.get(path), bytes);}

  /**
   * Write the byte[] given directly to a file.
   * @param path the file path to write
   * @param bytes the bytes which will be the new content of the file
   */
  public static void writeByteArrayToFile(Path path, byte[] bytes) {
    FileOutputStream stream = null;
    try {
      stream = new FileOutputStream(path.toFile());
      stream.write(bytes);
    } catch (FileNotFoundException e) {
      logger.log(Level.SEVERE, "File Not Found while trying to create file " + path, e);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Exception while trying to write file " + path, e);
    }
    try {
      stream.close();
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Exception while trying to close file " + path, t);
    }
  }




  /**
   * Make a default key pair.
   * This will be an eliptic curve key with BouncyCastle as the provider.
   * The curve is P-256.
   * @return the key pair
   */
  public static KeyPair generateDefaultKeyPair() {
    try {
      KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC", "BC");
      keyGen.initialize(new ECGenParameterSpec("P-256"));
      return keyGen.generateKeyPair();
    } catch(GeneralSecurityException e) {
      // actually throws NoSuchProviderException, NoSuchAlgorithmException, or InvalidAlgorithmParameterException
      logger.log(Level.SEVERE, "Key generation exception popped up when it really should not have: ", e);
      throw (new ServiceConfigurationError("Key generation exception popped up when it really should not have: "+e));
    }
  }

  /**
   * Get the bytes of a PEM file corresponding to a Private Key.
   * Will log SEVERE if something important goes wrong.
   * @param privateKey the private key we want as a PEM file
   * @return the raw bytes of a PEM file representing that private key
   */
  public static byte[] getPrivateKeyFileBytes(PrivateKey privateKey) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    JcaPEMWriter writer = new JcaPEMWriter(new OutputStreamWriter(outputStream));
    try {
      writer.writeObject(new PemObject("PRIVATE KEY", privateKey.getEncoded()));
    } catch (IOException e) {
      logger.log (Level.SEVERE, "Something went wrong while writing private key to bytes", e);
    }
    try{
      writer.close();
    } catch (IOException e) {
      logger.log (Level.WARNING, "Something went wrong while closing byte[] writer for private key", e);
    }
    return outputStream.toByteArray();
  }

  /**
   * Creates an X509 certificte from the keypair, and pipes the bytes into the InputStream it returns.
   * Literally the bytes of the certificate file as a PEM formatted file.
   * @param keyPair the public/private key pair to become a self-signed X.509 PEM certificate
   * @param dnsName the DNS name of the machine for which this cert applies
   * @param ipAddress the IP address of the machine for which this cert applies
   * @return a byte[] form of the PEM file
   */
  public static byte[] getCertBytes(KeyPair keyPair, String dnsName, String ipAddress) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    JcaPEMWriter writer = new JcaPEMWriter(new OutputStreamWriter(outputStream));

    // make "signer," which we'll use in signing (self-signing) the certificate
    JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder("SHA256WITHECDSA");
    ContentSigner signer = null;
    try {
      signer = csBuilder.build(keyPair.getPrivate());
    } catch (OperatorCreationException e) {
      logger.log(Level.SEVERE, "problem while building ContentSigner", e);
    }

    // useful for setting dates in the certificate file
    LocalDateTime startDate = LocalDate.now().atStartOfDay();

    // configure the certificate itself
    X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
        new X500Name("CN=ca"), // bullshit value we just made up
        new BigInteger("0"),   // bullshit value we just made up
        Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant()),
        Date.from(startDate.plusDays(3650).atZone(ZoneId.systemDefault()).toInstant()), // 10 years from now
        new X500Name("CN=ca"), // bullshit value we just made up
        SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));

    try{
      builder.addExtension(Extension.subjectAlternativeName, false, new DERSequence(new ASN1Encodable[] {
              new GeneralName(GeneralName.dNSName, "localhost"),
              new GeneralName(GeneralName.dNSName, dnsName),
              new GeneralName(GeneralName.iPAddress, "127.0.0.1"),
              new GeneralName(GeneralName.iPAddress, ipAddress)
          }));
    } catch (CertIOException e) {
      logger.log(Level.SEVERE, "problem while trying to add stuff to Certificate Builder", e);
    }

    // make the certificate using the configuration, signed with signer
    X509CertificateHolder holder = builder.build(signer);

    // write the certificate to the stream in PEM format
    try {
      writer.writeObject(new PemObject("CERTIFICATE", holder.toASN1Structure().getEncoded()));
    } catch (IOException e) {
      logger.log (Level.SEVERE, "Something went wrong while writing out Certificate to byte[]", e);
    }

    try{
      writer.close();
    } catch (IOException e) {
      logger.log (Level.WARNING, "Something went wrong while closing byte[] writer for cert", e);
    }
    return outputStream.toByteArray();
  }
}


