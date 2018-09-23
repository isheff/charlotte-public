package com.isaacsheff.charlotte;

import java.security.KeyPair;
import java.security.Security;
import java.util.Map;

import com.isaacsheff.charlotte.node.CharlotteNodeClient;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.Block;


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
    GenerateX509.generateKeyFiles("src/test/resources/server.pem",
                                  "src/test/resources/private-key.pem",
                                  "isheff.cs.cornell.edu",
                                  "128.84.155.11");
    KeyPair keyPair = (new Config("src/test/resources/config.yaml")).getKeyPair();
    Block challenge = Block.newBuilder().setStr("hello, world!").build();
    System.out.println(SignatureUtil.checkSignature(challenge, SignatureUtil.signBytes(keyPair, challenge)));

  }
}
