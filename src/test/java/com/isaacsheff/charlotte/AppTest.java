package com.isaacsheff.charlotte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A fairly bland test suite for the fairly meaningless "App" class, mostly to remind myself of how JUnit works.
 * @author Isaac Sheff
 */
class AppTest {

  /**
   * An example of a static parameter, which can be set by a BeforeAll method.
   */
  static int staticTestParam;

  /**
   * An example of a test parameter which can be set by a BeforeEach method.
   */
  int testParam;

  /**
   * Set stuff up before running any tests in this class.
   * This won't really count as a test, even if we label it @Test, but I guess we can put assertions in it.
   */
  @BeforeAll
  static void setup() {
    staticTestParam = 2;
    assertTrue(true, "Optional message here");
  }

  /**
   * Set stuff up before running each test in this class.
   * This won't really count as a test, even if we label it @Test, but I guess we can put assertions in it.
   * Not sure if there's a different instance object fro each test run.
   */
  @BeforeEach
  void init() {
    testParam = 2;
    assertEquals(2,2, "Optional message here");
  }

  /**
   * Test pretty much nothing of interest.
   */
  @Test
  void justAnExample() {
    assertEquals(testParam, staticTestParam, "Optional message here");
  }
}
