package com.isaacsheff.charlotte.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.isaacsheff.charlotte.proto.Block;

/**
 * Test our sha3 hash utility.
 * @author Isaac Sheff
 */
class HashUtilTest {

  /** Generate an example thing to hash. */
  private Block challenge;

  /** Generate an equal example thing to hash. */
  private Block challenge2;

  /** Generate a different example thing to hash. */
  private Block challenge3;

  /**
   * Set stuff up before running each test in this class.
   * Generates stuff to hash.
   */
  @BeforeEach
  void init() {
    challenge = Block.newBuilder().setStr("hello, world!").build();
    challenge2 = Block.newBuilder().setStr("hello, world!").build();
    challenge3 = Block.newBuilder().setStr("Different Thing").build();
  }

  /** Equal things hash to equal hashes. */
  @Test
  void equalHashes() {
    assumeTrue(challenge.equals(challenge2));
    assertEquals(HashUtil.sha3Hash(challenge), HashUtil.sha3Hash(challenge2),
                 "Hashes of equal things should be equal");
  }

  /** Non-Equal things hash to non-equal hashes. */
  @Test
  void nonEqualHashes() {
    assumeTrue(!challenge.equals(challenge3));
    assertTrue(!HashUtil.sha3Hash(challenge).equals(HashUtil.sha3Hash(challenge3)),
                 "Hashes of non-equal things should not be equal");
  }
}
