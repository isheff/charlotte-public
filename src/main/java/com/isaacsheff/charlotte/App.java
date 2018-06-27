package com.isaacsheff.charlotte;

import java.security.KeyPair;

import com.isaacsheff.charlotte.node.ChallengeResponseCalculator;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Challenge;
import com.isaacsheff.charlotte.proto.ChallengeInput;

/**
 * Hello world!
 *
 */
public class App {
  public static void main( String[] args ) {
    KeyPair keyPair = (new CharlotteNodeService()).getKeyPair();
    ChallengeInput challenge = ChallengeInput.newBuilder().setChallenge(Challenge.newBuilder().setStr("hi")).build();
    System.out.println("STILL BROKEN: " + (null == (ChallengeResponseCalculator.checkChallengeResponse(challenge,
                                ChallengeResponseCalculator.challengeResponse(keyPair, challenge)))));
  }
}
