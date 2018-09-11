package com.isaacsheff.charlotte.node;

import static com.isaacsheff.charlotte.node.PortUtil.getFreshPort;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonMap;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Test the CharlotteNodeClient.
 * This may use ports 8100 - 8199
 * @author Isaac Sheff
 */
public class CharlotteNodeClientTest {
  /**
   * Use this for logging events in the class.
   */
  private static final Logger logger = Logger.getLogger(CharlotteNodeClientTest.class.getName());


  /** The port used on the dummy server for an individual test. */
  private int port;

  /**
   * Set stuff up before running any tests in this class.
   * In this case, generate some crypto key files.
   */
  @BeforeAll
  static void setup() {
    GenerateX509.generateKeyFiles("src/test/resources/server.pem",
                                  "src/test/resources/private-key.pem",
                                  "isheff.cs.cornell.edu",
                                  "128.84.155.11");
  }


  /** 
   * launch a dummy server, send 3 blocks to it, and check to see the proper 3 blocks arrived.
   * */
  @Test
  void sendSomeBlocks() throws InterruptedException {
    // calcualte what port to put this server on
    port = getFreshPort();

    // populate the stack of blocks we expect to receive
    final BlockingQueue<Block> receivedBlocks = new ArrayBlockingQueue<Block>(3);

    // create a CharlotteNodeService that queues the blocks received
    final CharlotteNodeService service = new CharlotteNodeService(
        new Config(new JsonConfig("src/test/resources/private-key.pem",
                                  "localhost",
                                  singletonMap("localhost",
                                    new JsonContact("src/test/resources/server.pem", "localhost", port))
                                 ),
              Paths.get(".")
            )
        ) {
        @Override public Iterable<SendBlocksResponse> onSendBlocksInput(Block block) {
          try {
            receivedBlocks.put(block);
          } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "CANNOT RECEIVE BLOCK", e);
          }
          return emptySet();
        }
      };

    // start up a server on a separate thread with the service
    final CharlotteNode charlotteNode = new CharlotteNode(service);
    final Thread thread = new Thread(charlotteNode);
    thread.start();
    TimeUnit.SECONDS.sleep(1); // wait a second for the server to start up

    // create a client, and send the expected sequence of blocks
    final CharlotteNodeClient client = (new Contact(
        new JsonContact("src/test/resources/server.pem", "localhost", port), Paths.get("."))).
      getCharlotteNodeClient();
    client.sendBlock(Block.newBuilder().setStr("block 0").build());
    client.sendBlock(Block.newBuilder().setStr("block 1").build());
    client.sendBlock(Block.newBuilder().setStr("block 2").build());

    // check that the blocks received were the same as the ones sent
    assertEquals(Block.newBuilder().setStr("block 0").build(), receivedBlocks.take(),
                 "block received should match block sent");
    assertEquals(Block.newBuilder().setStr("block 1").build(), receivedBlocks.take(),
                 "block received should match block sent");
    assertEquals(Block.newBuilder().setStr("block 2").build(), receivedBlocks.take(),
                 "block received should match block sent");

    // check to ensure no other blocks somehow got queued
    assertTrue(receivedBlocks.isEmpty(), "no further blocks should be expected");
    client.shutdown();
    charlotteNode.stop();
  }
}
