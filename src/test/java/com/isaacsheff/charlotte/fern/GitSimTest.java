package com.isaacsheff.charlotte.fern;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.isaacsheff.charlotte.fern.GitSimFern.getFernNode;
import static com.isaacsheff.charlotte.node.PortUtil.getFreshPort;
import static com.isaacsheff.charlotte.yaml.GenerateX509.generateKeyFiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.JsonConfig;
import com.isaacsheff.charlotte.yaml.JsonContact;


/**
 * Test GitSim
 * @author Isaac Sheff
 */
public class GitSimTest {

  /** the participants map to be used in config files. will be set in setup() **/
  private static Map<String, JsonContact> participants;

  private static CharlotteNode fernNode;
  private static CharlotteNode clientNode;
  private static GitSimClient client; 
  private static Reference initialReference;

  /**
   * Set stuff up before running any tests in this class.
   * In this case, generate some crypto key files, and participants map for a config.
   */
  @BeforeAll
  static void setup() throws InterruptedException, FileNotFoundException {
    generateKeyFiles("src/test/resources/server.pem",
                     "src/test/resources/private-key.pem",
                     "localhost",
                     "127.0.0.1");
    generateKeyFiles("src/test/resources/server2.pem",
                     "src/test/resources/private-key2.pem",
                     "localhost",
                     "127.0.0.1");
    participants = new HashMap<String, JsonContact>(2);
    participants.put("fern", new JsonContact("src/test/resources/server.pem",  "localhost", getFreshPort()));
    participants.put("client", new JsonContact("src/test/resources/server2.pem", "localhost", getFreshPort()));

    // start the fern server
    fernNode =
      (getFernNode(new CharlotteNodeService(
        new Config(new JsonConfig("src/test/resources/private-key.pem",  "fern", participants),
              Paths.get(".")
            )
        )));
    (new Thread(fernNode)).start();
    // start the client's CharlotteNode
    final CharlotteNodeService clientService = new CharlotteNodeService(
        new Config(new JsonConfig("src/test/resources/private-key2.pem", "client", participants),
        Paths.get(".")));
    clientNode = getFernNode(clientService);
    (new Thread(clientNode)).start();

    TimeUnit.SECONDS.sleep(1); // wait a second for the server to start up

    client = new GitSimClient(clientService, clientService.getConfig().getContact("fern"));
    initialReference = null;

  }

  @AfterAll
  static void cleanup() throws InterruptedException, FileNotFoundException {
    client.shutdown();
    clientNode.stop();
    fernNode.stop();
  }

  /**
   * Launch a local server and an additional Fern node, mint 3 blocks, and try to put them in a chain, gathering agreement attestations from both servers.
   */
  @Test
  void initialCommit() {
    initialReference = client.commit("master",
                                     "my first commit!",
                                     copyFromUtf8("repository contents"),
                                     emptySet(),
                                     emptySet());
    assertTrue(null != initialReference);
  }

  void secondCommit() {
    assertTrue(null != client.commit("master",
                                     "my second commit!",
                                     copyFromUtf8("repository contents are now slightly different."),
                                     singleton(initialReference),
                                     singleton(copyFromUtf8("I added the \"are now slightly different.\""))));
  }

  void conflictingCommit() {
    assertEquals(null, client.commit("master",
                                     "a conflicting second commit!",
                                     copyFromUtf8("repository contents are now even more different."),
                                     singleton(initialReference),
                                     singleton(copyFromUtf8("I added the \"are now even more different.\""))));
  }

  void differentBranchCommit() {
    assertTrue(null != client.commit("different-branch",
                                     "a second commit that would be conflicting if it were on master branch!",
                                     copyFromUtf8("repository contents are now even more different."),
                                     singleton(initialReference),
                                     singleton(copyFromUtf8("I added the \"are now even more different.\""))));
  }
}
