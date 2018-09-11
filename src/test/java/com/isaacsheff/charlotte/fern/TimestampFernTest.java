package com.isaacsheff.charlotte.fern;

import static com.isaacsheff.charlotte.fern.TimestampFern.getFernNode;
import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
import static com.isaacsheff.charlotte.node.PortUtil.getFreshPort;
import static com.isaacsheff.charlotte.yaml.GenerateX509.generateKeyFiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.isaacsheff.charlotte.proto.IntegrityAttestation.SignedTimestampedReferences;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.TimestampedReferences;
import com.isaacsheff.charlotte.proto.IntegrityPolicy;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationResponse;
import com.isaacsheff.charlotte.proto.Signature;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.JsonConfig;
import com.isaacsheff.charlotte.yaml.JsonContact;

/**
 * Test Timestamp service
 * @author Isaac Sheff
 */
public class TimestampFernTest {

  /**
   * Set stuff up before running any tests in this class.
   * In this case, generate some crypto key files
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

  }

  /**
   * Launch a local service and a Fern node, mint a block, and then get and test an integrity attestation for it.
   */
  @Test
  void timestampSingleBlock() throws InterruptedException, FileNotFoundException {
    Map<String, JsonContact> participants = new HashMap<String, JsonContact>(2);
    participants.put("fern", new JsonContact("src/test/resources/server.pem",  "localhost", getFreshPort()));
    participants.put("client", new JsonContact("src/test/resources/server2.pem", "localhost", getFreshPort()));
    // start the fern server
    final CharlotteNode fernNode = 
      getFernNode(
        new Config(new JsonConfig("src/test/resources/private-key.pem",  "fern", participants),
              Paths.get(".")
            ),
        10
        );
    (new Thread(fernNode)).start();
    // start the client's CharlotteNode
    final CharlotteNodeService clientService = new CharlotteNodeService(
        new Config(new JsonConfig("src/test/resources/private-key2.pem", "client", participants),
        Paths.get(".")));
    final CharlotteNode clientNode = new CharlotteNode(clientService);
    (new Thread(clientNode)).start();

    TimeUnit.SECONDS.sleep(60); // wait a second for the server to start up

    // mint a block, and send it out to the HetconsNodes
    Block block = Block.newBuilder().setStr("block contents").build();
    clientService.onSendBlocksInput(block);

    // make a client using the local service, and the contact for the node
    final TimestampClient client = new TimestampClient(clientService, clientService.getConfig().getContact("fern"));
    // get an integrity attestation for the block, and check it.
    assertTrue(null != client.getIntegrityAttestation(
      RequestIntegrityAttestationInput.newBuilder().setPolicy(
        IntegrityPolicy.newBuilder().setFillInTheBlank(
          IntegrityAttestation.newBuilder().setSignedTimestampedReferences(
            SignedTimestampedReferences.newBuilder().
              setTimestampedReferences(
                TimestampedReferences.newBuilder().
                  addBlock(Reference.newBuilder().setHash(sha3Hash(block)))).
              setSignature(Signature.newBuilder().setCryptoId(client.getContact().getCryptoId()))
      ))).build()));
    clientNode.stop();
    fernNode.stop();
    client.shutdown();
  }


  /**
   * Launch a local service and a Fern node, mint 10 blocks, and
   *  then there should be exactly 1 integrity attestation, which
   *  attests to those 10 blocks, already in existence.
   */
  @Test
  void timestampBatch() throws InterruptedException, FileNotFoundException {
    Map<String, JsonContact> participants = new HashMap<String, JsonContact>(2);
    participants.put("fern", new JsonContact("src/test/resources/server.pem",  "localhost", getFreshPort()));
    participants.put("client", new JsonContact("src/test/resources/server2.pem", "localhost", getFreshPort()));
    // start the fern server
    final CharlotteNode fernNode = 
      getFernNode(
        new Config(new JsonConfig("src/test/resources/private-key.pem",  "fern", participants),
              Paths.get(".")
            ),
        10
        );
    (new Thread(fernNode)).start();
    // start the client's CharlotteNode
    final CharlotteNodeService clientService = new CharlotteNodeService(
        new Config(new JsonConfig("src/test/resources/private-key2.pem", "client", participants),
        Paths.get(".")));
    final CharlotteNode clientNode = new CharlotteNode(clientService);
    (new Thread(clientNode)).start();

    TimeUnit.SECONDS.sleep(1); // wait a second for the server to start up

    // mint a block, and send it out to the HetconsNodes
    final TimestampedReferences.Builder referencesBuilder = TimestampedReferences.newBuilder();
    for(int i = 0; i < 10; ++i) {
      final Block block = Block.newBuilder().setStr("block contents " + i).build();
      clientService.onSendBlocksInput(block);
      referencesBuilder.addBlock(Reference.newBuilder().setHash(sha3Hash(block)));
    }

    TimeUnit.SECONDS.sleep(1); // wait a second for everything to catch up

    // make a client using the local service, and the contact for the node
    final TimestampClient client =
      new TimestampClient(clientService, clientService.getConfig().getContact("fern"));

    // search the known blocks for an integrity attestation (should exist by now)
    RequestIntegrityAttestationResponse response = null;
    for (Block block : clientService.getBlockMap().values()) {
      if (block.hasIntegrityAttestation()) {
        response = RequestIntegrityAttestationResponse.newBuilder().setReference(
                     Reference.newBuilder().setHash(sha3Hash(block))).build();
      }
    }
    assertTrue(null != response); // we should have found an integrity attestation
    
    // check that the integrity attestation attests to this set of blocks.
    assertTrue(null != client.checkIntegrityAttestation(
      RequestIntegrityAttestationInput.newBuilder().setPolicy(
        IntegrityPolicy.newBuilder().setFillInTheBlank(
          IntegrityAttestation.newBuilder().setSignedTimestampedReferences(
            SignedTimestampedReferences.newBuilder().
              setTimestampedReferences(referencesBuilder).
              setSignature(Signature.newBuilder().setCryptoId(client.getContact().getCryptoId()))
      ))).build(),
      response));
    clientNode.stop();
    fernNode.stop();
    client.shutdown();
  }


  /**
   * Launch a local service and a Fern node, mint 25 blocks, and then
   *  there should be 2 integrity attestations, attesating to some of
   *  those blocks, in existence.
   */
  @Test
  void timestampBatches() throws InterruptedException, FileNotFoundException {
    Map<String, JsonContact> participants = new HashMap<String, JsonContact>(2);
    participants.put("fern", new JsonContact("src/test/resources/server.pem",  "localhost", getFreshPort()));
    participants.put("client", new JsonContact("src/test/resources/server2.pem", "localhost", getFreshPort()));
    // start the fern server
    final CharlotteNode fernNode = 
      getFernNode(
        new Config(new JsonConfig("src/test/resources/private-key.pem",  "fern", participants),
              Paths.get(".")
            ),
        10
        );
    (new Thread(fernNode)).start();
    // start the client's CharlotteNode
    final CharlotteNodeService clientService = new CharlotteNodeService(
        new Config(new JsonConfig("src/test/resources/private-key2.pem", "client", participants),
        Paths.get(".")));
    final CharlotteNode clientNode = new CharlotteNode(clientService);
    (new Thread(clientNode)).start();

    TimeUnit.SECONDS.sleep(1); // wait a second for the server to start up

    // mint a block, and send it out to the HetconsNodes
    final TimestampedReferences.Builder referencesBuilder = TimestampedReferences.newBuilder();
    for(int i = 0; i < 25; ++i) {
      final Block block = Block.newBuilder().setStr("block contents " + i).build();
      clientService.onSendBlocksInput(block);
      referencesBuilder.addBlock(Reference.newBuilder().setHash(sha3Hash(block)));
    }

    TimeUnit.SECONDS.sleep(1); // wait a second for everything to catch up


    // search the known blocks for an integrity attestation (should exist by now)
    int responseCount = 0;
    for (Block block : clientService.getBlockMap().values()) {
      if (block.hasIntegrityAttestation()) {
        responseCount++;
      }
    }
    assertEquals(2, responseCount); // we should have found exactly 2  integrity attestations
    
    clientNode.stop();
    fernNode.stop();
  }

}
