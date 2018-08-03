package com.isaacsheff.charlotte.wilbur;

import static com.isaacsheff.charlotte.wilbur.WilburService.getWilburNode;
import static com.isaacsheff.charlotte.yaml.GenerateX509.generateKeyFiles;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.JsonConfig;
import com.isaacsheff.charlotte.yaml.JsonContact;

public class WilburClientTest {
  private static Map<String, JsonContact> participants;

  /**
   * Set stuff up before running any tests in this class.
   * In this case, generate some crypto key files.
   */
  @BeforeAll
  static void setup() {
    generateKeyFiles("src/test/resources/server.pem",
                     "src/test/resources/private-key.pem",
                     "localhost",
                     "127.0.0.1");
    generateKeyFiles("src/test/resources/server2.pem",
                     "src/test/resources/private-key2.pem",
                     "localhost",
                     "127.0.0.1");
    participants = new HashMap<String, JsonContact>(2);
    participants.put("wilbur", new JsonContact("src/test/resources/server.pem",  "localhost", 8301));
    participants.put("client", new JsonContact("src/test/resources/server2.pem", "localhost", 8302));

  }

  @Test
  void endToEnd() throws InterruptedException, FileNotFoundException {
    // start the wilbur server
    (new Thread(getWilburNode(new CharlotteNodeService(
        new Config(new JsonConfig("src/test/resources/private-key.pem",  "wilbur", participants),
              Paths.get(".")
            )
        )))).start();
    // start the client's CharlotteNode
    CharlotteNodeService clientService = new CharlotteNodeService(
        new Config(new JsonConfig("src/test/resources/private-key2.pem", "client", participants),
        Paths.get(".")));
    (new Thread(new CharlotteNode(clientService))).start();

    TimeUnit.SECONDS.sleep(5); // wait a second for the server to start up

    Block block = Block.newBuilder().setStr("block contents").build();
    clientService.onSendBlocksInput(block);

    WilburClient client = new WilburClient(clientService, clientService.getConfig().getContact("wilbur"));
    client.checkAvailabilityAttestation(block, client.requestAvailabilityAttestation(block));



  }

}
