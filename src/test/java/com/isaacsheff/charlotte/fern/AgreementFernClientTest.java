package com.isaacsheff.charlotte.fern;

import static com.isaacsheff.charlotte.fern.AgreementFernService.getFernNode;
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

import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
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
public class AgreementFernClientTest {

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
   * Launch a local service and a Fern node, mint a block, and then get and test an integrity attestation for it.
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
    final CharlotteNode clientNode = (new CharlotteNode(clientService));
    (new Thread(clientNode)).start();

    TimeUnit.SECONDS.sleep(1); // wait a second for the server to start up

    // mint a block, and send it out to the HetconsNodes
    Block block = Block.newBuilder().setStr("block contents").build();
    clientService.onSendBlocksInput(block);

    // make a client using the local service, and the contact for the node
    AgreementFernClient client = new AgreementFernClient(clientService, clientService.getConfig().getContact("fern"));
    // get an integrity attestation for the block, and check it.
    assertTrue(null != client.getIntegrityAttestation(
      RequestIntegrityAttestationInput.newBuilder().setPolicy(
        IntegrityPolicy.newBuilder().setFillInTheBlank(
          IntegrityAttestation.newBuilder().setSignedChainSlot(
            SignedChainSlot.newBuilder().
              setChainSlot(
                ChainSlot.newBuilder().
                  setSlot(0).
                  setRoot(Reference.newBuilder().setHash(sha3Hash(block))).
                  setBlock(Reference.newBuilder().setHash(sha3Hash(block))).
                  setParent(Reference.newBuilder().setHash(sha3Hash(block)))).
              setSignature(Signature.newBuilder().setCryptoId(client.getContact().getCryptoId()))
      ))).build()));
    client.shutdown();
    clientNode.stop();
    fernNode.stop();
  }

}
