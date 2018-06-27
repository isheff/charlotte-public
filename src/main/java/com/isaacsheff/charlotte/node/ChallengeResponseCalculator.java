package com.isaacsheff.charlotte.node;

import java.security.KeyPair;
import java.security.Security;

import com.isaacsheff.charlotte.proto.Challenge;
import com.isaacsheff.charlotte.proto.ResponseToChallenge;

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
   * TODO: do this right!.
   * <pre>
   * Using the hash algorithm provided,
   * hash( bytestring"Response to Challenge with Hash: " concat hash(challenge.str))
   * then sign that and return the signature.
   * used to guarangee that an open channel (possibly TLS) corresponds to a crypto ID
   * When using crypto IDs to do your TLS, this would not be necessary.
   * </pre>
   * @param keyPair the key pair to use in signing the response
   * @param challenge the incomming challenge
   * @return the calculated response
   */
  public static ResponseToChallenge challengeResponse(KeyPair keyPair, Challenge challenge) {
    return ResponseToChallenge.newBuilder().build();
  }

  /**
   * TODO: do this right!.
   * Check whether this crypto response was calculated successfully.
   * @param challenge the crypto challenge
   * @param response the crypto response received
   * @return whether the crypto response is correct
   */
  public static boolean checkChallengeResponse(Challenge challenge, ResponseToChallenge response) {
    return false;
  }
}
