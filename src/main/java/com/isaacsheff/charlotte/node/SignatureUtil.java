package com.isaacsheff.charlotte.node;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.PublicKey;
import com.isaacsheff.charlotte.proto.Signature;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ServiceConfigurationError;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Useful utility function for making and checking ECDSA P-256 signatures.
 * @author Isaac Sheff
 */
public class SignatureUtil {
  /**
   * This line is required to use bouncycastle encryption libraries.
   */
  static {Security.addProvider(new BouncyCastleProvider());}

  /**
   * Use logger for logging events involving SignatureUtil.
   */
  private static final Logger logger = Logger.getLogger(SignatureUtil.class.getName());

  /**
   * A useful helper method for Throwables that should never be thrown.
   * We log them as severe, and then throw a ConfigurationError,
   *  since we assume something must have been configured wrong.
   */
  private static void logSevereAndServiceConfigurationError(String str, Throwable t) {
    logger.log(Level.SEVERE, str, t);
    throw (new ServiceConfigurationError(str+t));
  }

  /**
   * A helper method for starting up a signature object.
   * Initiated with algorithm "SHA256withECDSA" and provider "BC".
   * If something goes wrong in initiating an instance, it logs it as SEVERE, and throws an error.
   * @return the signature object
   */
  private static java.security.Signature initSignature() {
    try {
      return java.security.Signature.getInstance("SHA256withECDSA", "BC");
    } catch (NoSuchAlgorithmException e) {
      logSevereAndServiceConfigurationError(
          "Signature generated NoSuchAlgorithm when shouldn't have, on SHA256withECDSA: ", e);
    } catch (NoSuchProviderException e) {
      logSevereAndServiceConfigurationError("Signature generated NoSuchProvider when shouldn't have, on BC: ", e);
    }
    return null; // this point should never be reached.
  }

  /**
   * Get the CryptoId object corresponding to the Java PublicKey object.
   * This assymes you're using a P256 Ecliptic Curve Key
   * @param publicKey the Java PublicKey 
   * @return the corresponding CryptoId object
   */
  public static CryptoId createCryptoId(java.security.PublicKey publicKey) {
    return CryptoId.newBuilder().setPublicKey(PublicKey.newBuilder().setElipticCurveP256(
             PublicKey.ElipticCurveP256.newBuilder().setByteString(
               ByteString.copyFrom(publicKey.getEncoded())))).build();
  }

  /**
   * Create a Signature (charlotte protobuf) object for the given keypair and bytestring.
   * @param keyPair the key pair to use in signing the response
   * @param bytes the bytes you want to sign
   * @return the calculated Signature
   */
  public static Signature signBytes(KeyPair keyPair, byte[] bytes) {
    // Here we start creating the signature string. 
    // Lots of Exceptions are possible, but ought to never happen, so we log severe and throw an error if they do.
    java.security.Signature signature = initSignature();
    try {
      signature.initSign(keyPair.getPrivate());
    } catch (InvalidKeyException e) {
      logSevereAndServiceConfigurationError("The Key which we tried to sign with was invalid: ", e);
    }
    try {
      signature.update(bytes);
    } catch (SignatureException e) {
      logSevereAndServiceConfigurationError("Something went wrong while passing bytes to signer:  ", e);
    }
    byte[] signatureBytes = null;
    try {
      signatureBytes = signature.sign();
    } catch (SignatureException e) {
      logSevereAndServiceConfigurationError("Something went horribly wrong while signing some bytes:  ", e);
    }

    // assemble and return the response object:
    return Signature.newBuilder().
             setSha256WithEcdsa(Signature.SignatureAlgorithmSHA256WithECDSA.newBuilder().setByteString(
               ByteString.copyFrom(signatureBytes))).
             setCryptoId(createCryptoId(keyPair.getPublic())).build();
  }
  
  /**
   * Create a Signature (charlotte protobuf) object for the given keypair and bytestring.
   * @param keyPair the key pair to use in signing the response
   * @param bytes the byteString you want to sign
   * @return the calculated Signature
   */
  public static Signature signBytes(KeyPair keyPair, ByteString bytes) {return signBytes(keyPair,bytes.toByteArray());}

  /**
   * Create a Signature (charlotte protobuf) object for the given keypair and message.
   * @param keyPair the key pair to use in signing the response
   * @param message the message you want to sign
   * @return the calculated Signature
   */
  public static Signature signBytes(KeyPair keyPair, MessageLite message) {
    return signBytes(keyPair, message.toByteArray());
  }

  /**
   * Check whether this crypto signature was calculated successfully.
   * @param bytes the bytes supposedly signed
   * @param signature the signature
   * @return whether the signature was correct
   */
  public static boolean checkSignature(byte[] bytes, Signature signature) {
    // second, parse the public key from X.509 bytes to a java PublicKey object
    java.security.PublicKey publicKey = null;
    try {
      publicKey = KeyFactory.getInstance("EC", "BC").generatePublic(new X509EncodedKeySpec(
          signature.getCryptoId().getPublicKey().getElipticCurveP256().getByteString().toByteArray()));
    } catch(NoSuchAlgorithmException e) {
      logSevereAndServiceConfigurationError("Key Parsing generated NoSuchAlgorithm when shouldn't have, on EC: ", e);
    } catch(NoSuchProviderException e) {
      logSevereAndServiceConfigurationError("Key Parsing generated NoSuchProvider when shouldn't have, on BC: ", e);
    } catch (InvalidKeySpecException e) {
      logger.info("tried to verify a signature which had an invalid key");
      return false; // the key was invalid
    }

    // Parse the signature into a java Signature object, and then verify it with the public key
    java.security.Signature sig = initSignature();
    try {
      sig.initVerify(publicKey);
    } catch (InvalidKeyException e) {
      logger.log(Level.WARNING, "something very odd has happened: we parsed a key, and then it was invalid.", e);
      return false; // the key was invalid
    }
    try {
      sig.update(bytes); // the bytes that supposedly were signed
    } catch (SignatureException e) {
      logger.log(Level.WARNING, "something very odd has happened: we parsed a key, and got a "+
                "signature exception while feeding in the string supposedly signed.", e);
      return false; // the signature didn't verify
    }
    try {
      if (!sig.verify(signature.getSha256WithEcdsa().getByteString().toByteArray())){
        logger.info("tried to verify a signature, but the signature didn't verify.");
        return false; // the signature didn't verify
      }
    } catch (SignatureException e) {
      logger.log(Level.INFO,
                 "tried to verify a signature, but there was a SignatureException while verifying.",e);
      return false; // the signature didn't verify, or something went wrong.
    }

    return true;
  }

  /**
   * Check whether this crypto signature was calculated successfully.
   * @param bytes the byteString supposedly signed
   * @param signature the signature
   * @return whether the signature was correct
   */
  public static boolean checkSignature(ByteString bytes, Signature signature) {
    return checkSignature(bytes.toByteArray(), signature);
  }

  /**
   * Check whether this crypto signature was calculated successfully.
   * @param message the Message supposedly signed
   * @param signature the signature
   * @return whether the signature was correct
   */
  public static boolean checkSignature(MessageLite message, Signature signature) {
    return checkSignature(message.toByteArray(), signature);
  }
}
