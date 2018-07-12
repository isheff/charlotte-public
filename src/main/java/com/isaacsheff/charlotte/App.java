package com.isaacsheff.charlotte;

import java.security.KeyPair;
import java.security.Security;
import java.util.Map;

import com.isaacsheff.charlotte.node.ChallengeResponseCalculator;
import com.isaacsheff.charlotte.node.CharlotteNodeClient;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.proto.Challenge;
import com.isaacsheff.charlotte.proto.ChallengeInput;


import com.isaacsheff.charlotte.yaml.CharlotteNodeConfig;
import com.isaacsheff.charlotte.yaml.ContactInfo;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.GenerateX509;

import java.io.File;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import org.apache.commons.lang3.builder.ToStringStyle;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Hello world!
 *
 */
public class App {
  /**
   * This line is required to use bouncycastle encryption libraries.
   */
  static {Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());}
  public static void main( String[] args ) {
    GenerateX509.generateKeyFiles("src/test/server.pem", "src/test/private-key.pem", "isheff.cs.cornell.edu", "128.84.155.11");
    KeyPair keyPair = (new Config("src/test/config.yaml")).getKeyPair();
    ChallengeInput challenge = ChallengeInput.newBuilder().setChallenge(Challenge.newBuilder().setStr("hi")).build();
    System.out.println("DOES IT WORK?: " + (null != (ChallengeResponseCalculator.checkChallengeResponse(challenge,
                                ChallengeResponseCalculator.challengeResponse(keyPair, challenge)))));
    CharlotteNode node = new CharlotteNode(5555);
    (new Thread(node)).start();
    CharlotteNodeClient client = new CharlotteNodeClient(node.getCert(),"128.84.155.11",5555);
    System.out.println("Client created.");
    client.testChallengeResponseBlocking();
    System.out.println("blocking test complete.");

  }
}
