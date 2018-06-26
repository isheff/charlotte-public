package com.isaacsheff.charlotte.node;

import com.isaacsheff.charlotte.proto.Challenge;
import com.isaacsheff.charlotte.proto.ResponseToChallenge;

/**
 * This class exists to house some utility static methods relating to ChallengeResponse RPC calls.
 * @author Isaac Sheff
 */
public class ChallengeResponseCalculator {

  /**
   * TODO: do this right!.
   * <pre>
   * Using the hash algorithm provided,
   * hash( bytestring"Response to Challenge with Hash: " concat hash(challenge.str))
   * then sign that and return the signature.
   * used to guarangee that an open channel (possibly TLS) corresponds to a crypto ID
   * When using crypto IDs to do your TLS, this would not be necessary.
   * </pre>
   * @param challenge the incomming challenge
   * @return the calculated response
   */
  public static ResponseToChallenge challengeResponse(Challenge challenge) {
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
