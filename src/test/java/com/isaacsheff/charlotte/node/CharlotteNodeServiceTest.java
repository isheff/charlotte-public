package com.isaacsheff.charlotte.node;

import static com.isaacsheff.charlotte.node.PortUtil.getFreshPort;
import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.isaacsheff.charlotte.node.CharlotteNodeClient;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.SendBlocksResponse;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.Contact;
import com.isaacsheff.charlotte.yaml.GenerateX509;
import com.isaacsheff.charlotte.yaml.JsonConfig;
import com.isaacsheff.charlotte.yaml.JsonContact;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Test the CharlotteNodeService.
 * This may use ports 8200 - 8299
 * @author Isaac Sheff
 */
public class CharlotteNodeServiceTest {
  /** Use this for logging events in the class. */
  private static final Logger logger = Logger.getLogger(CharlotteNodeServiceTest.class.getName());

  /** The port used on dummy server 1 for an individual test. */
  private int port0;

  /** The port used on dummy server 2 for an individual test. */
  private int port1;

  /**
   * Set stuff up before running any tests in this class.
   * In this case, generate some crypto key files.
   */
  @BeforeAll
  static void setup() {
    GenerateX509.generateKeyFiles("src/test/resources/server.pem",
                                  "src/test/resources/private-key.pem",
                                  "localhost",
                                  "127.0.0.1");
    GenerateX509.generateKeyFiles("src/test/resources/server2.pem",
                                  "src/test/resources/private-key2.pem",
                                  "localhost",
                                  "127.0.0.1");
  }


  /** Launch 2 dummy servers, send 3 blocks to 1 of them, and check that BOTH receive all 3 blocks. */
  @Test
  void sendSomeBlocks() throws InterruptedException {
    port0 = getFreshPort();
    port1 = getFreshPort();

    final HashMap<String, JsonContact> contacts = new HashMap<String, JsonContact>(2);
    contacts.put("node0", new JsonContact("src/test/resources/server.pem", "localhost", port0));
    contacts.put("node1", new JsonContact("src/test/resources/server2.pem", "localhost", port1));


    // populate the stack of blocks we expect to receive
    final BlockingQueue<Block> receivedBlocks0 = new ArrayBlockingQueue<Block>(3);
    // create a CharlotteNodeService that queues the blocks received
    final Config config0 = 
        new Config(new JsonConfig("src/test/resources/private-key.pem",
                                  "node0",
                                  contacts
                                 ),
              Paths.get(".")
            );
    final CharlotteNode node0 = new CharlotteNode(new CharlotteNodeService(config0) {
        @Override public Iterable<SendBlocksResponse> afterBroadcastNewBlock(Block block) {
          try {
            receivedBlocks0.put(block);
          } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "CANNOT RECEIVE BLOCK", e);
          }
          return emptySet();
        }
      });
    // start up a server on a separate thread with the service
    final Thread thread0 = new Thread(node0);
    thread0.start();

    // populate the stack of blocks we expect to receive
    final BlockingQueue<Block> receivedBlocks1 = new ArrayBlockingQueue<Block>(3);
    // create a CharlotteNodeService that queues the blocks received
    final Config config1 =
        new Config(new JsonConfig("src/test/resources/private-key2.pem",
                                  "node1",
                                  contacts
                                 ),
              Paths.get(".")
            );
    final CharlotteNode node1 = new CharlotteNode(new CharlotteNodeService(config1) {
        @Override public Iterable<SendBlocksResponse> afterBroadcastNewBlock(Block block) {
          try {
            receivedBlocks1.put(block);
          } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "CANNOT RECEIVE BLOCK", e);
          }
          return emptySet();
        }
      });
    // start up a server on a separate thread with the service
    final Thread thread1 = new Thread(node1);
    thread1.start();

    // create a client, and send the expected sequence of blocks
    final CharlotteNodeClient client = (new Contact(
        new JsonContact("src/test/resources/server.pem", "localhost", port0), Paths.get("."), config1)).
      getCharlotteNodeClient();

    TimeUnit.SECONDS.sleep(1); // wait a second for the server to start up

    client.sendBlock(Block.newBuilder().setStr("block 0").build());
    client.sendBlock(Block.newBuilder().setStr("block 1").build());
    client.sendBlock(Block.newBuilder().setStr("block 2").build());

    // check that the blocks received by the server were the same as the ones sent
    assertEquals(Block.newBuilder().setStr("block 0").build(), receivedBlocks0.take(),
                 "block received should match block sent");
    assertEquals(Block.newBuilder().setStr("block 1").build(), receivedBlocks0.take(),
                 "block received should match block sent");
    assertEquals(Block.newBuilder().setStr("block 2").build(), receivedBlocks0.take(),
                 "block received should match block sent");
    // check that the blocks received by the OTHER server were the same as the ones sent
    assertEquals(Block.newBuilder().setStr("block 0").build(), receivedBlocks1.take(),
                 "block received should match block sent");
    assertEquals(Block.newBuilder().setStr("block 1").build(), receivedBlocks1.take(),
                 "block received should match block sent");
    assertEquals(Block.newBuilder().setStr("block 2").build(), receivedBlocks1.take(),
                 "block received should match block sent");

    
    // check to ensure no other blocks somehow got queued
    assertTrue(receivedBlocks0.isEmpty(), "no further blocks should be expected");
    // check to ensure no other blocks somehow got queued
    assertTrue(receivedBlocks1.isEmpty(), "no further blocks should be expected");

    // TODO: I don't know why this shutdown code causes grpc RuntimeExceptions in SendBlocksObserver.
    //       This isn't really a problem, as the servers' shutdown behaviour doesn't really matter.
    //       However, it bugs me.
    //       This should be fixed.
    // client.shutdown();
    // node0.stop();
    // node1.stop();
  }
}
