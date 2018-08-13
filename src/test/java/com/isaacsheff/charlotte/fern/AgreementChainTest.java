package com.isaacsheff.charlotte.fern;

import static com.isaacsheff.charlotte.fern.AgreementChainFernService.getFernNode;
import static com.isaacsheff.charlotte.fern.AgreementChainFernClient.stripRequest;
import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
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
 * Test AgreementFern clients (which, by necessity, also tests the service).
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
    participants.put("fern", new JsonContact("src/test/resources/server.pem",  "localhost", 8501));
    participants.put("client", new JsonContact("src/test/resources/server2.pem", "localhost", 8502));

  }

  /**
   * Launch a local service and a Fern node, mint a block, and then get and test an integrity attestation for it.
   */
  @Test
  void endToEnd() throws InterruptedException, FileNotFoundException {
    // start the fern server
    (new Thread(getFernNode(new CharlotteNodeService(
        new Config(new JsonConfig("src/test/resources/private-key.pem",  "fern", participants),
              Paths.get(".")
            )
        )))).start();
    // start the client's CharlotteNode
    CharlotteNodeService clientService = new CharlotteNodeService(
        new Config(new JsonConfig("src/test/resources/private-key2.pem", "client", participants),
        Paths.get(".")));
    (new Thread(getFernNode(clientService))).start();

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
              setSignature(Signature.newBuilder().setCryptoId(clientService.getConfig().getCryptoId()))
      ))).build();
    client.broadcastWhenReady(input2);
    client.getKnownResponses().putIfAbsent(stripRequest(input2), new ConcurrentHolder<Hash>());
    assertTrue(null != client.getKnownResponses().get(stripRequest(input2)).get());
  }

}
