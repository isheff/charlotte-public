package com.isaacsheff.charlotte.node;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.yaml.GenerateX509;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.security.KeyPair;

/**
 * Do we generate signatures and verify them properly?.
 * If GenerateX509 is not working, this won't either.
 * @author Isaac Sheff
 */
class SignatureUtilTest {

  /** Generate a keypair before each method call. */
  private KeyPair keyPair;

  /** Generate an example thing to sign. */
  private Block challenge;

  /** Generate a different example thing to sign. */
  private Block challenge2;

  /**
   * Set stuff up before running each test in this class.
   * Generates a KeyPair to use.
   */
  @BeforeEach
  void init() {
    keyPair = GenerateX509.generateDefaultKeyPair();
    assumeTrue(null != keyPair);
    challenge = Block.newBuilder().setStr("hello, world!").build();
    challenge2 = Block.newBuilder().setStr("Different Thing").build();
  }

  /** Test Whether Signatures Verify. */
  @Test
  void verifySignature() {
    assertTrue(SignatureUtil.checkSignature(challenge, SignatureUtil.signBytes(keyPair, challenge)),
               "correct signatures should verify correctly");
  }

  /** 
   * Test Whether Incorrect Signatures Don't Verify.
   * If this is working correctly, it will generate a log message, but that's ok.
   * */
  @Test
  void invalidSignature() {
    assertTrue(!SignatureUtil.checkSignature(challenge2, SignatureUtil.signBytes(keyPair, challenge)),
               "incorrect signatures should not verify correctly");
  }

}

