package com.isaacsheff.charlotte.experiments;

import static com.isaacsheff.charlotte.node.PortUtil.getFreshPort;
import static com.isaacsheff.charlotte.yaml.GenerateX509.generateKeyFiles;
import static java.util.Arrays.asList;

import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.JsonContact;

import io.grpc.ServerBuilder;

/**
 * Test AgreementFernChain service and client
 * @author Isaac Sheff
 */
public class AgreementNTest {

  /** the participants map to be used in config files. will be set in setup() **/
  private static Map<String, JsonContact> participants;

  /**
   * Set stuff up before running any tests in this class.
   * In this case, generate some crypto key files, and participants map for a config.
   */
  @BeforeAll
  static void setup() {
    participants = new HashMap<String, JsonContact>(5);
    for (int i = 0; i < 5; ++i) {
      generateKeyFiles("src/test/resources/server" + i + ".pem",
                       "src/test/resources/private-key" + i + ".pem",
                       "localhost",
                       "127.0.0.1");
      participants.put("participant"+i, new JsonContact("src/test/resources/server"+i+".pem",
                                                        "localhost",
                                                        getFreshPort()));
    }
  }

  /**
   * Launch a local server and an additional Fern node, mint 3 blocks, and try to put them in a chain, gathering agreement attestations from both servers.
   */
  @Test
  void endToEnd() throws InterruptedException, FileNotFoundException {
    final CharlotteNode[] nodes = new CharlotteNode[5];

    // start up all the ferns on seperate threads
    for (int i = 1; i < 5; ++i) {
      final JsonExperimentConfig config =new JsonExperimentConfig(
          asList("participant1","participant2","participant3","participant4"),
          5,
          "src/test/resources/private-key" + i + ".pem",
          "participant"+i,
          participants);
      final CharlotteNodeService node = new CharlotteNodeService(new Config(config, Paths.get(".")));
      CharlotteNode charlotteNode = new CharlotteNode(node,
        ServerBuilder.forPort(node.getConfig().getPort()).
                      addService(new AgreementNFern(config, node)),
        node.getConfig().getPort());
      nodes[i] = charlotteNode;
      (new Thread(charlotteNode)).start();
    }


    // launch client
    final JsonExperimentConfig config =new JsonExperimentConfig(
        asList("participant1","participant2","participant3","participant4"),
        5,
        "src/test/resources/private-key0.pem",
        "participant0",
        participants);
    final CharlotteNodeService node = new CharlotteNodeService(new Config(config, Paths.get(".")));
    nodes[0] = (new CharlotteNode(node));
    (new Thread(nodes[0])).start();
    TimeUnit.SECONDS.sleep(3); // wait for servers to start up
    final AgreementNClient client = new AgreementNClient(node, config);
    TimeUnit.SECONDS.sleep(3); // wait for servers to start up
    client.broadcastRequest(Reference.newBuilder(), 0); // send out the root block
    client.waitUntilDone();

    for (CharlotteNode n : nodes) {
      n.stop();
    }
  }
}
