package com.isaacsheff.charlotte.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;

import com.isaacsheff.charlotte.collections.BlockingConcurrentHashMap;
import com.isaacsheff.charlotte.collections.BlockingMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A test suite for the BlockingConcurrentHashMap
 * @author Isaac Sheff
 */
class BlockingConcurrentHashMapTest {

  /** An example BlockingConcurrentHashMap used in each test. */
  private BlockingMap<String, String> testMap;


  /**
   * Make a blank map before running each test in this class.
   */
  @BeforeEach
  void init() {
    testMap = new BlockingConcurrentHashMap<String, String>();
  }

  /** Test put under "normal" circumstances. */
  @Test
  void normalPut() {
    assertEquals(null, testMap.put("key", "value"), "Put with fresh key should return null");
    assertEquals("value", testMap.put("key", "new value"),
                 "Put with used key should return previous value");
    assertEquals("new value", testMap.put("key", "newer value"),
                 "Put with used key should return most recent previous value");
  }

  /** Test putIfAbsent under "normal" circumstances. */
  @Test
  void normalPutIfAbsent() {
    assertEquals(null, testMap.putIfAbsent("key", "value"),
                 "PutIfAbsent with fresh key should return null");
    assertEquals("value", testMap.putIfAbsent("key", "new value"),
                 "PutIfAbsent with used key should return previous value");
    assertEquals("value", testMap.putIfAbsent("key", "newer value"),
                 "PutIfAbsent with used key should return most recent value set.");
  }

  /** Test get under "normal" circumstances. */
  @Test
  void normalGet() {
    assertEquals(null, testMap.get("key"), "get should return null for fresh key");
    normalPut(); // should put "newer value" in for key "key"
    assertEquals("newer value", testMap.get("key"), "get should return most recently put value");

    // now let's play with a different key
    assertEquals(null, testMap.get("fresh key 2"), "get should return null for fresh key 2");
    assertEquals(null, testMap.put("fresh key 2", "value 2"), "put should return null for fresh key 2");
    assertEquals("value 2", testMap.get("fresh key 2"), "get should return most recently put value");

    // make sure gets after putIfAbsent are proper
    assertEquals("newer value", testMap.putIfAbsent("key", "value 3"),
                 "PutIfAbsent with used key should return most recent set value.");
    assertEquals("newer value", testMap.get("key"),
                 "get should return most recently put value, unchanged by PutIfAbsent");
  }

  /** Test blockingGet under "normal" circumstances. */
  @Test
  void normalBlockingGet() {
    normalPut(); // should put "newer value" in for key "key"
    assertEquals("newer value", testMap.blockingGet("key"), "get should return most recently put value");

    // now let's play with a different key
    assertEquals(null, testMap.put("fresh key 2", "value 2"), "put should return null for fresh key 2");
    assertEquals("value 2", testMap.blockingGet("fresh key 2"),
                 "get should return most recently put value");

    // make sure gets after putIfAbsent are proper
    assertEquals("newer value", testMap.putIfAbsent("key", "value 3"),
                 "PutIfAbsent with used key should return most recent set value.");
    assertEquals("newer value", testMap.blockingGet("key"),
                 "get should return most recently put value, unchanged by PutIfAbsent");
  }

  /** Test blockingGet when it actually has to block */
  @Test
  void asyncBlockingGet() throws InterruptedException {
    Thread t = new Thread(() -> {assertEquals("value", testMap.blockingGet("key"),
                  "blockingGet should return the first value set after it was called");});
    t.start();
    Thread t2 = new Thread(() -> {assertEquals("value 2", testMap.blockingGet("key 2"),
                  "blockingGet should return the first value set after it was called");});
    t2.start();
    TimeUnit.SECONDS.sleep(1); // wait a second to ensure that the blockingGets are blocking
    assertEquals(null, testMap.putIfAbsent("key 2", "value 2"), "put with fresh key should return null");
    normalPut(); // should put "newer value" in for key "key"
    assertEquals("newer value", testMap.blockingGet("key"), "get should return most recently put value");
    t.join();
    t2.join();
  }

} 
