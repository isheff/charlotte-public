package com.isaacsheff.charlotte.node;


import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.proto.ChallengeInput;
import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.PublicKey;
import com.isaacsheff.charlotte.proto.ResponseToChallenge;
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
import java.util.ServiceConfigurationError;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bouncycastle.jcajce.provider.digest.SHA3;

/**
 * This class exists to house some utility static methods relating to ChallengeResponse RPC calls.
 * @author Isaac Sheff
 */
public class ChallengeResponseCalculator {

  /**
   * This line is required to use bouncycastle encryption libraries.
   */
  static {Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());}

  /**
   * Use logger for logging events involving ChallengeResponseCalculator.
   */
  private static final Logger logger = Logger.getLogger(ChallengeResponseCalculator.class.getName());

  /**
   * A useful helper method for Throwables that should never be thrown.
   * We log them as severe, and then throw a ConfigurationError,
   *  since we assume somethign must have been configured wrong.
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
   * A helper method to calculate the bytes you're supposed to sign after receiving a challenge.
   * ( bytestring"Response to Challenge with Hash: " concat hash(challenge.str))
   * @param challenge the challenge to which we're responding
   * @return the bytes we're supposed to sign
   */
  private static byte[] getSignMe(ChallengeInput challenge) {
    SHA3.DigestSHA3 sha3DigestMaker = new SHA3.Digest256(); // why is this an object? The library requires it.
    return ByteString.copyFromUtf8("Response to Challenge with Hash: ").
             concat(ByteString.copyFrom(sha3DigestMaker.digest(
               challenge.getChallenge().getStrBytes().toByteArray()))).toByteArray();
  }

  /**
   * <pre>
   * Using the hash algorithm provided,
   * ( bytestring"Response to Challenge with Hash: " concat hash(challenge.str))
   * then sign that and return the signature.
   * used to guarangee that an open channel (possibly TLS) corresponds to a crypto ID
   * When using crypto IDs to do your TLS, this would not be necessary.
   * </pre>
   * @param keyPair the key pair to use in signing the response
   * @param challenge the incomming challenge
   * @return the calculated response
   */
  public static ResponseToChallenge challengeResponse(KeyPair keyPair, ChallengeInput challenge) {
    if (! challenge.hasChallenge()) {
      return ResponseToChallenge.newBuilder().setErrorMessage("No challenge provided.").build();
    }
    if (challenge.getChallenge().getStr().isEmpty()) {
      return ResponseToChallenge.newBuilder().setErrorMessage("Challenge Provided is the empty string.").build();
    }
    // create the string we're supposed to sign:
    byte[] signMe = getSignMe(challenge);

    // Here we start creating the signature string. 
    // Lots of Exceptions are possible, but ought to never happen, so we log severe and throw an error if they do.
    java.security.Signature signature = initSignature();
    try {
      signature.initSign(keyPair.getPrivate());
    } catch (InvalidKeyException e) {
      logSevereAndServiceConfigurationError("The Key which we tried to sign with was invalid: ", e);
    }
    try {
      signature.update(signMe);
    } catch (SignatureException e) {
      logSevereAndServiceConfigurationError("Something went wrong while passing response string to signer:  ", e);
    }
    byte[] signatureBytes = null;
    try {
      signatureBytes = signature.sign();
    } catch (SignatureException e) {
      logSevereAndServiceConfigurationError("Something went horribly wrong while signing response:  ", e);
    }

    // assemble and return the response object:
    return ResponseToChallenge.newBuilder().setSignature(Signature.newBuilder().
             setSha256WithEcdsa(Signature.SignatureAlgorithmSHA256WithECDSA.newBuilder().setByteString(
               ByteString.copyFrom(signatureBytes))).
             setCryptoId(CryptoId.newBuilder().setPublicKey(PublicKey.newBuilder().setElipticCurveP256(
                   PublicKey.ElipticCurveP256.newBuilder().setByteString(
                     ByteString.copyFrom(keyPair.getPublic().getEncoded()))))
             )).build();
  }

  /**
   * Check whether this crypto response was calculated successfully.
   * @param challenge the crypto challenge
   * @param response the crypto response received
   * @return The CryptoId of the responder, OR null, if the response is wrong
   */
  public static CryptoId checkChallengeResponse(ChallengeInput challenge, ResponseToChallenge response) {
    if (! response.hasSignature()) { return null; } // the response has no signature enclosed.
    if (! response.getSignature().hasCryptoId()) { return null; } // the response has no cryptoID.
    if (! response.getSignature().getCryptoId().hasPublicKey()) { return null; } // the cryptoID has no public key.
    if (! response.getSignature().getCryptoId().getPublicKey().hasElipticCurveP256()) { return null; } // wrong key
    if (  response.getSignature().getCryptoId().getPublicKey().getElipticCurveP256().getByteString().isEmpty()) {
      logger.info("tried to verify a challenge response which reatured the empty string as a key");
      return null; // the key is the empty string
    }
    if (! response.getSignature().hasSha256WithEcdsa()) { return null; } // wrong signature type
    if (  response.getSignature().getSha256WithEcdsa().getByteString().isEmpty()){ return null; } // 0 signature bytes
    if (! challenge.hasChallenge()) {
      logger.log(Level.WARNING, "Someone tried to verify a challenge response with a string-less challenge");
      return null;
    }
    if (challenge.getChallenge().getStr().isEmpty()) {
      logger.log(Level.WARNING, "Someone tried to verify a challenge response with an empty string challenge");
      return null;
    }


    java.security.PublicKey publicKey = null;
    try {
      publicKey = KeyFactory.getInstance("EC", "BC").generatePublic(new X509EncodedKeySpec(
          response.getSignature().getSha256WithEcdsa().getByteString().toByteArray()));
    } catch(NoSuchAlgorithmException e) {
      logSevereAndServiceConfigurationError("Key Parsing generated NoSuchAlgorithm when shouldn't have, on EC: ", e);
    } catch(NoSuchProviderException e) {
      logSevereAndServiceConfigurationError("Key Parsing generated NoSuchProvider when shouldn't have, on BC: ", e);
    } catch (InvalidKeySpecException e) {
      logger.info("tried to verify a challenge response which had an invalid key");
      return null; // the key was invalid
    }

    java.security.Signature signature = initSignature();
    try {
      signature.initVerify(publicKey);
    } catch (InvalidKeyException e) {
      logger.log(Level.WARNING, "something very odd has happened: we parsed a key, and then it was invalid.", e);
      return null; // the key was invalid
    }
    try {
      signature.update(getSignMe(challenge)); // the bytes that supposedly were signed
    } catch (SignatureException e) {
      logger.log(Level.WARNING, "something very odd has happened: we parsed a key, and got a "+
                "signature exception while feeding in the string supposedly signed.", e);
      return null; // the signature didn't verify
    }
    try {
      if (!signature.verify(response.getSignature().getCryptoId().getPublicKey().
            getElipticCurveP256().getByteString().toByteArray())){
        logger.info("tried to verify a challenge response, but the signature didn't verify.");
        return null; // the signature didn't verify
      }
    } catch (SignatureException e) {
      logger.info("tried to verify a challenge response, but there was a SignatureException while verifying.");
      return null; // the signature didn't verify, or something went wrong.
    }

    // If all of the above went well, then we return the cryptoId from the response, which was correctly signed.
    return response.getSignature().getCryptoId();
  }
}
