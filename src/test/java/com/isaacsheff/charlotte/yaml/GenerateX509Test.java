package com.isaacsheff.charlotte.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.yaml.GenerateX509;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.junit.jupiter.api.Test;
import java.io.FileReader;
import java.io.Reader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Security;
import java.security.PrivateKey;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Test that we generate X509s properly.
 * @author Isaac Sheff
 */
class GenerateX509Test {
  /**
   * This line is required to use bouncycastle encryption libraries.
   */
  static {Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());}

  /**
   * Use this for logging events in the class.
   */
  private static final Logger logger = Logger.getLogger(GenerateX509Test.class.getName());

  /**
   * This is an end to end test.
   * Reading in keys turns out to be messy, and the code we've got to do it is part of
   *  Contact and Config, so I copied and pasted it out of there.
   * Alas, in order to test key equality, it is helpful to convert stuff to CryptoIds,
   *   which means that if SignatureUtil is broken, this will be too.
   */
  @Test
  void endToEnd() throws InterruptedException, FileNotFoundException {
    KeyPair keypair = GenerateX509.generateDefaultKeyPair();
    GenerateX509.writeByteArrayToFile("src/test/resources/server.pem",
      GenerateX509.getCertBytes(keypair,"isheff.cs.cornell.edu", "128.84.155.11"));
    GenerateX509.writeByteArrayToFile("src/test/resources/private-key.pem",
      GenerateX509.getPrivateKeyFileBytes(keypair.getPrivate()));

    // Read the public key to the variable publicKey
    X509CertificateHolder holder = null;
    Reader reader = new FileReader("src/test/resources/server.pem");
    PEMParser parser= new PEMParser(reader);
    try {
      Object object = parser.readObject();
      if (!(object instanceof X509CertificateHolder)) {
        logger.log(Level.WARNING, "Object Parsed is not a SubjectPublicKeyInfo: " + object);
      }
      holder = ((X509CertificateHolder) object);
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
    PublicKey publicKey = null;
    try {
      SubjectPublicKeyInfo publicKeyInfo = holder.getSubjectPublicKeyInfo();
      JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
      publicKey = converter.getPublicKey(publicKeyInfo);
    } catch (PEMException e) {
      logger.log(Level.SEVERE, "X509 cert file could not be parsed as PEM", e);
    }


    assertEquals(SignatureUtil.createCryptoId(keypair.getPublic()),
                (SignatureUtil.createCryptoId(publicKey)),
                "public key created initially should be the same as the one read back from the file.");



    // Read the private key to the variable privateKey
    PrivateKey privateKey = null;
    reader = new FileReader("src/test/resources/private-key.pem");
    parser= new PEMParser(reader);
    try {
      Object object = parser.readObject();
      if (!(object instanceof PrivateKeyInfo)) {
        logger.log(Level.SEVERE, "Private Key file parsed as a non-PrivateKeyInfo object");
        logger.log(Level.SEVERE, "Private Key file parsed as a non-PrivateKeyInfo object: " + object);
      }
      PrivateKeyInfo privateKeyInfo = (PrivateKeyInfo) object;
      JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
      privateKey = converter.getPrivateKey(privateKeyInfo);
    } catch (PEMException e) {
      logger.log(Level.SEVERE, "Private Key file could not be parsed as PEM", e);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "PEM converter could not pull a PrivateKeyInfo from the Private Key File", e);
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
        "Private Key byte[] reader didn't close properly, but we parsed everything, so it's probably ok.", e);
    }


    // for lack of a better test, test that each byte in the two private keys is the same
    for(int i = 0; i < GenerateX509.getPrivateKeyFileBytes(keypair.getPrivate()).length; ++i) {
      assertEquals(GenerateX509.getPrivateKeyFileBytes(keypair.getPrivate())[i],
                   GenerateX509.getPrivateKeyFileBytes(privateKey)[i],
                 "private key created initially should be the same as the one read back from the file.");
    }
  }
}

