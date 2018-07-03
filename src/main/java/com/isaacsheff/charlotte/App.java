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


import com.isaacsheff.charlotte.yaml.User;
import com.isaacsheff.charlotte.yaml.Address;

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
    KeyPair keyPair = (new CharlotteNodeService()).getKeyPair();
    ChallengeInput challenge = ChallengeInput.newBuilder().setChallenge(Challenge.newBuilder().setStr("hi")).build();
    System.out.println("DOES IT WORK?: " + (null != (ChallengeResponseCalculator.checkChallengeResponse(challenge,
                                ChallengeResponseCalculator.challengeResponse(keyPair, challenge)))));
    CharlotteNode node = new CharlotteNode(5555);
    (new Thread(node)).start();
    CharlotteNodeClient client = new CharlotteNodeClient(node.getCert(),"128.84.155.11",5555);
    System.out.println("Client created.");
    client.testChallengeResponseBlocking();
    System.out.println("blocking test complete.");

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    try {
      User user = mapper.readValue(new File("src/test/text.yaml"), User.class);
      System.out.println(ReflectionToStringBuilder.toString(user,ToStringStyle.MULTI_LINE_STYLE));
      for (Map.Entry<String, Address> address : user.getAddresses().entrySet()) {
        System.out.println(address.getKey());
        System.out.println(ReflectionToStringBuilder.toString(address.getValue(),ToStringStyle.MULTI_LINE_STYLE));
      }
      System.out.println(ReflectionToStringBuilder.toString(user.getAddresses(),ToStringStyle.MULTI_LINE_STYLE));
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}
