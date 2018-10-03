package com.isaacsheff.charlotte.experiments;

import static com.isaacsheff.charlotte.node.PortUtil.getFreshPort;
import static com.isaacsheff.charlotte.yaml.GenerateX509.generateKeyFiles;

import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.wilbur.WilburService;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.JsonContact;

/**
 * Test AgreementFernChain service and client
 * @author Isaac Sheff
 */
public class AgreementNWTest {

  /** the participants map to be used in config files. will be set in setup() **/
  private static Map<String, JsonContact> participants;
  private static List<String> fern;
  private static List<String> wilbur;

  /**
   * Set stuff up before running any tests in this class.
   * In this case, generate some crypto key files, and participants map for a config.
   */
  @BeforeAll
  static void setup() {
    final int fernCount = 7;
    final int wilburCount = 3;
    fern = new ArrayList<String>(fernCount);
    wilbur = new ArrayList<String>(wilburCount);


    participants = new HashMap<String, JsonContact>(fernCount + wilburCount + 1);
    for (int i = 0; i < (fernCount + wilburCount + 1); ++i) {
      generateKeyFiles("src/test/resources/server" + i + ".pem",
                       "src/test/resources/private-key" + i + ".pem",
                       "localhost",
                       "127.0.0.1");
      participants.put("participant"+i, new JsonContact("src/test/resources/server"+i+".pem",
                                                        "localhost",
                                                        getFreshPort()));
      if ((i > 0) && (i <= fernCount)) {
        fern.add("participant" + i);
      }
      if (i > fernCount) {
        wilbur.add("participant" + i);
      }
    }
  }

  /**
   * Launch a local server and an additional Fern node, mint 3 blocks, and try to put them in a chain, gathering agreement attestations from both servers.
   */
  @Test
  void endToEnd() throws InterruptedException, FileNotFoundException {
    final CharlotteNode[] nodes = new CharlotteNode[(fern.size() + wilbur.size() + 1)];

    // start up all the ferns on seperate threads
    for (int i = 1; i <= fern.size(); ++i) {
      final JsonExperimentConfig config =new JsonExperimentConfig(
          fern,
          wilbur,
          50,
          wilbur.size()-1,
          "src/test/resources/private-key" + i + ".pem",
          "participant"+i,
          participants,
          10);
      final CharlotteNodeService node = new CharlotteNodeService(new Config(config, Paths.get(".")));
      CharlotteNode charlotteNode = new CharlotteNode(node, new AgreementNWFern(config, node));
      nodes[i] = charlotteNode;
      (new Thread(charlotteNode)).start();
    }
    // start up all the wilburs on seperate threads
    for (int i = (fern.size()+1); i < (fern.size() + wilbur.size() + 1); ++i) {
      final JsonExperimentConfig config =new JsonExperimentConfig(
          fern,
          wilbur,
          50,
          wilbur.size()-1,
          "src/test/resources/private-key" + i + ".pem",
          "participant"+i,
          participants,
          10);
      final CharlotteNodeService node = new AgreementNWilbur(new Config(config, Paths.get(".")));
      CharlotteNode charlotteNode = new CharlotteNode(node, new WilburService(node));
      nodes[i] = charlotteNode;
      (new Thread(charlotteNode)).start();
    }


    // launch client
    final JsonExperimentConfig config =new JsonExperimentConfig(
        fern,
        wilbur,
        50,
        wilbur.size()-1,
        "src/test/resources/private-key0.pem",
        "participant0",
        participants,
        10);
    final CharlotteNodeService node = new CharlotteNodeService(new Config(config, Paths.get(".")));
    nodes[0] = (new CharlotteNode(node));
    (new Thread(nodes[0])).start();
    TimeUnit.SECONDS.sleep(3); // wait for servers to start up
    final AgreementNWClient client = new AgreementNWClient(node, config);
    TimeUnit.SECONDS.sleep(3); // wait for servers to start up
    client.broadcastRequest(Reference.newBuilder(), 0); // send out the root block
    client.waitUntilDone();

//    for (CharlotteNode n : nodes) {
//      n.stop();
//    }
  }
}
