package com.isaacsheff.charlotte.wilburquery;


import static com.isaacsheff.charlotte.node.PortUtil.getFreshPort;
import static com.isaacsheff.charlotte.wilburquery.WilburQueryService.getWilburQueryNode;
import static com.isaacsheff.charlotte.yaml.GenerateX509.generateKeyFiles;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.WilburQueryInput;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.JsonConfig;
import com.isaacsheff.charlotte.yaml.JsonContact;

/**
 * Test Wilbur clients (which, by necessity, also tests wilbur service).
 * @author Isaac Sheff
 */
public class WilburQueryTest {

  /** the participants map to be used in config files. will be set in setup() **/
  private static Map<String, JsonContact> participants;

  /**
   * Set stuff up before running any tests in this class.
   * In this case, generate some crypto key files, and participants map for a config.
   */
  @BeforeAll
  static void setup() {
    generateKeyFiles("src/test/resources/server.pem",
                     "src/test/resources/private-key.pem",
                     "localhost",
                     "127.0.0.1");
    participants = new HashMap<String, JsonContact>(1);
    participants.put("wilbur",
      new JsonContact("src/test/resources/server.pem",  "localhost", getFreshPort()));

  }

  /**
   * Launch a local service and a Wilbur node, mint a block, and then get and test an availability attestation for it.
   */
  @Test
  void endToEnd() throws InterruptedException, FileNotFoundException {
    // start the wilbur server
    final CharlotteNodeService service = new CharlotteNodeService(
        new Config(new JsonConfig("src/test/resources/private-key.pem", 
                   "wilbur",
                   participants),
              Paths.get(".")
            ));
    (new Thread(getWilburQueryNode(service))).start();

    TimeUnit.SECONDS.sleep(1); // wait a second for the server to start up

    // mint a block, and send it out to the HetconsNodes
    final Block block = Block.newBuilder().setStr("block contents").build();
    service.onSendBlocksInput(block);

    // make a client using the local service, and the contact for the wilbur node
    final WilburQueryClient client =
      new WilburQueryClient(service.getConfig().getContact("wilbur"));
    // get an availability attestation for the block, and check it.
    assertEquals(1,
        client.wilburQuery(
          WilburQueryInput.newBuilder().setFillInTheBlank(block).build()
        ).getBlockList().size()
      );
    client.shutdown();
  }

}
