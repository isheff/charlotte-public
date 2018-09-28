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
 * Test GitSim.
 * This Fern server runs a kind of simulation of the Git version
 *  control system.
 * In this simulation, each Commit is a block featuring a hash of the
 *  repository contents, and diffs from (with references to) prior
 *  commits.
 * A commit with multiple prior commit references is a "merge," with
 *  none is "initial," and with one is a "regular" commit.
 *
 * <p>
 * Furthermore, any Fern server can maintain one or more "branches,"
 *  identified by branch name (a String).
 * Different servers may disagree on which commits belong in which
 *  branches.
 * Integrity Attestations here are signed statements that a given
 *  commit belongs in a given branch at a given (real) time.
 * Furthermore, a Fern server will not attest to any other commits
 *  being in the same branch, unless this commit is an ancestor of the
 *  new commit.
 * Any backtracking must be handled explicitly in the diffs.
 * </p>
 *
 * @author Isaac Sheff
 */
public class GitSimTest {

  /** the participants map to be used in config files. will be set in setup() **/
  private static Map<String, JsonContact> participants;

  /** the fern server **/
  private static CharlotteNode fernNode;

  /** the local charlottenode that the client uses to receive blocks **/
  private static CharlotteNode clientNode;

  /** the client that talks to the fern server **/
  private static GitSimClient client; 

  /** the initial commit block **/
  private static Reference initialReference;

  /**
   * Set stuff up before running any tests in this class.
   * In this case, generate some crypto key files, and participants map for a config.
   * Then launch local and fern servers, and set up the client.
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

  /**
   *  When we're all done, shut down all the servers and client.
   */
  @AfterAll
  static void cleanup() throws InterruptedException, FileNotFoundException {
    // client.shutdown();
    // clientNode.stop();
    // fernNode.stop();
  }

  /** Commit an initial commit to the master branch. **/
  @Test
  void initialCommit() {
    initialReference = client.commit("master",
                                     "my first commit!",
                                     copyFromUtf8("repository contents"),
                                     emptySet(),
                                     emptySet());
    assertTrue(null != initialReference);
  }

  /** Commit a second commit to the master branch. **/
  void secondCommit() {
    assertTrue(null != client.commit("master",
                                     "my second commit!",
                                     copyFromUtf8("repository contents are now slightly different."),
                                     singleton(initialReference),
                                     singleton(copyFromUtf8("I added the \"are now slightly different.\""))));
  }

  /** Commit a different commit to the master branch, that doesn't follow the second. This should fail. **/
  void conflictingCommit() {
    assertEquals(null, client.commit("master",
                                     "a conflicting second commit!",
                                     copyFromUtf8("repository contents are now even more different."),
                                     singleton(initialReference),
                                     singleton(copyFromUtf8("I added the \"are now even more different.\""))));
  }

  /** Commit a different commit to a different branch. This should succeed. **/
  void differentBranchCommit() {
    assertTrue(null != client.commit("different-branch",
                                     "a second commit that would be conflicting if it were on master branch!",
                                     copyFromUtf8("repository contents are now even more different."),
                                     singleton(initialReference),
                                     singleton(copyFromUtf8("I added the \"are now even more different.\""))));
  }
}
