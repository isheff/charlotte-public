package com.isaacsheff.charlotte.fern;

import static com.isaacsheff.charlotte.fern.AgreementChainFernService.getFernNode;
import static com.isaacsheff.charlotte.fern.AgreementChainFernClient.stripRequest;
import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
import static com.isaacsheff.charlotte.node.PortUtil.getFreshPort;
import static com.isaacsheff.charlotte.yaml.GenerateX509.generateKeyFiles;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.isaacsheff.charlotte.collections.ConcurrentHolder;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.proto.IntegrityAttestation;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.ChainSlot;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.SignedChainSlot;
import com.isaacsheff.charlotte.proto.IntegrityPolicy;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.Signature;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.JsonConfig;
import com.isaacsheff.charlotte.yaml.JsonContact;


/**
 * Test AgreementFernChain service and client
 * @author Isaac Sheff
 */
public class AgreementChainTest {

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
    generateKeyFiles("src/test/resources/server2.pem",
                     "src/test/resources/private-key2.pem",
                     "localhost",
                     "127.0.0.1");
    participants = new HashMap<String, JsonContact>(2);
    participants.put("fern", new JsonContact("src/test/resources/server.pem",  "localhost", getFreshPort()));
    participants.put("client", new JsonContact("src/test/resources/server2.pem", "localhost", getFreshPort()));

  }

  /**
   * Launch a local server and an additional Fern node, mint 3 blocks, and try to put them in a chain, gathering agreement attestations from both servers.
   */
  @Test
  void endToEnd() throws InterruptedException, FileNotFoundException {
    // start the fern server
    final CharlotteNode fernNode =
               (getFernNode(new CharlotteNodeService(
        new Config(new JsonConfig("src/test/resources/private-key.pem",  "fern", participants),
              Paths.get(".")
            )
        )));
    (new Thread(fernNode)).start();
    // start the client's CharlotteNode
    CharlotteNodeService clientService = new CharlotteNodeService(
        new Config(new JsonConfig("src/test/resources/private-key2.pem", "client", participants),
        Paths.get(".")));
    final CharlotteNode clientNode = getFernNode(clientService);
    (new Thread(clientNode)).start();

    TimeUnit.SECONDS.sleep(1); // wait a second for the server to start up

    // mint a block, and send it out to the HetconsNodes
    Block block0 = Block.newBuilder().setStr("block contents 0").build();
    clientService.onSendBlocksInput(block0);
    Block block1 = Block.newBuilder().setStr("block contents 1").build();
    clientService.onSendBlocksInput(block1);
    Block block2 = Block.newBuilder().setStr("block contents 2").build();
    clientService.onSendBlocksInput(block2);

    // make a client using the local service, and the contact for the node
    AgreementChainFernClient client = new AgreementChainFernClient(clientService);
    // get an integrity attestation for the block, and check it.

    // get agreement on a root block
    client.broadcastWhenReady(
      RequestIntegrityAttestationInput.newBuilder().setPolicy(
        IntegrityPolicy.newBuilder().setFillInTheBlank(
          IntegrityAttestation.newBuilder().setSignedChainSlot(
            SignedChainSlot.newBuilder().
              setChainSlot(
                ChainSlot.newBuilder().
                  setSlot(0).
                  setRoot(Reference.newBuilder().setHash(sha3Hash(block0))).
                  setBlock(Reference.newBuilder().setHash(sha3Hash(block0))))
      ))).build());

    // get agreement on block 1
    client.broadcastWhenReady(
      RequestIntegrityAttestationInput.newBuilder().setPolicy(
        IntegrityPolicy.newBuilder().setFillInTheBlank(
          IntegrityAttestation.newBuilder().setSignedChainSlot(
            SignedChainSlot.newBuilder().
              setChainSlot(
                ChainSlot.newBuilder().
                  setSlot(1).
                  setRoot(Reference.newBuilder().setHash(sha3Hash(block0))).
                  setBlock(Reference.newBuilder().setHash(sha3Hash(block1))).
                  setParent(Reference.newBuilder().setHash(sha3Hash(block0))))
      ))).build());

    // get agreement on block 2
    RequestIntegrityAttestationInput input2 = 
      RequestIntegrityAttestationInput.newBuilder().setPolicy(
        IntegrityPolicy.newBuilder().setFillInTheBlank(
          IntegrityAttestation.newBuilder().setSignedChainSlot(
            SignedChainSlot.newBuilder().
              setChainSlot(
                ChainSlot.newBuilder().
                  setSlot(2).
                  setRoot(Reference.newBuilder().setHash(sha3Hash(block0))).
                  setBlock(Reference.newBuilder().setHash(sha3Hash(block2))).
                  setParent(Reference.newBuilder().setHash(sha3Hash(block1)))).
              // this cryptoid is only specified so we can also use this as an index to look up client's response.
              setSignature(Signature.newBuilder().setCryptoId(clientService.getConfig().getContacts().get("fern").
                  getCryptoId()))
      ))).build();
    client.broadcastWhenReady(input2);

    // look up client's response to see if we indeed have an appropriate integrity attestation
    client.getKnownResponses().putIfAbsent(stripRequest(input2), new ConcurrentHolder<Hash>());
    assertTrue(null != client.getKnownResponses().get(stripRequest(input2)).get());
    // client.shutdown();
    // clientNode.stop();
    // fernNode.stop();
  }

}
