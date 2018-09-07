package com.isaacsheff.charlotte.wilburquery;


import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
import static com.isaacsheff.charlotte.node.PortUtil.getFreshPort;
import static com.isaacsheff.charlotte.wilburquery.WilburQueryService.getWilburQueryNode;
import static com.isaacsheff.charlotte.yaml.GenerateX509.generateKeyFiles;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.AvailabilityAttestation;
import com.isaacsheff.charlotte.proto.AvailabilityAttestation.SignedStoreForever;
import com.isaacsheff.charlotte.proto.AvailabilityAttestation.StoreForever;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.Signature;
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

  private static CharlotteNodeService service;

  private static WilburQueryClient client;

  private static Block block0;
  private static Block block1;
  private static Block block2;

  /**
   * Set stuff up before running any tests in this class.
   * In this case, generate some crypto key files, and participants map for a config.
   */
  @BeforeAll
  static void setup() throws InterruptedException{
    generateKeyFiles("src/test/resources/server.pem",
                     "src/test/resources/private-key.pem",
                     "localhost",
                     "127.0.0.1");
    participants = new HashMap<String, JsonContact>(1);
    participants.put("wilbur",
      new JsonContact("src/test/resources/server.pem",  "localhost", getFreshPort()));

    // start the wilbur server
    service = new CharlotteNodeService(
        new Config(new JsonConfig("src/test/resources/private-key.pem", 
                   "wilbur",
                   participants),
              Paths.get(".")
            ));
    (new Thread(getWilburQueryNode(service))).start();

    TimeUnit.SECONDS.sleep(1); // wait a second for the server to start up

    // mint some blocks, and send them out to the HetconsNodes
    block0 = Block.newBuilder().setStr("block contents").build();
    block1 = Block.newBuilder().
          setAvailabilityAttestation(
            AvailabilityAttestation.newBuilder().setSignedStoreForever(
              SignedStoreForever.newBuilder().setSignature(
                Signature.newBuilder().setCryptoId(
                  service.getConfig().getCryptoId()
                )
              ).setStoreForever(
                StoreForever.newBuilder().addBlock(
                  Reference.newBuilder().setHash(
                    sha3Hash(block0)
                  )
                )
              )
            )
          )
        .build();
    block2 = Block.newBuilder().
          setAvailabilityAttestation(
            AvailabilityAttestation.newBuilder().setSignedStoreForever(
              SignedStoreForever.newBuilder().setSignature(
                Signature.newBuilder().setCryptoId(
                  service.getConfig().getCryptoId()
                )
              ).setStoreForever(
                StoreForever.newBuilder().addBlock(
                  Reference.newBuilder().setHash(
                    sha3Hash(block0)
                  )
                ).
                addBlock(
                  Reference.newBuilder().setHash(
                    sha3Hash(block1)
                  )
                )
              )
            )
          )
        .build();
    service.onSendBlocksInput(block0);
    service.onSendBlocksInput(block1);
    service.onSendBlocksInput(block2);

    // make a client using the local service, and the contact for the wilbur node
    client = new WilburQueryClient(service.getConfig().getContact("wilbur"));


  }

  /**
   */
  @AfterAll
  static void shutdown() throws InterruptedException {
    client.shutdown();
  }

  /**
   * Test a basic request for a single block.
   */
  @Test
  void singleBlockEquality() {
    // get an availability attestation for the block, and check it.
    assertEquals(1,
        client.wilburQuery(
          WilburQueryInput.newBuilder().setFillInTheBlank(
            Block.newBuilder().setStr("block contents").build()
          ).build()
        ).getBlockList().size()
      );
  }

  /**
   * Test a more complex request for a single block.
   */
  @Test
  void singleBlockPartial() {
    // get an availability attestation for the block, and check it.
    assertEquals(1,
        client.wilburQuery(
          WilburQueryInput.newBuilder().setFillInTheBlank(
            Block.newBuilder().
              setAvailabilityAttestation(
                AvailabilityAttestation.newBuilder().setSignedStoreForever(
                  SignedStoreForever.newBuilder().setStoreForever(
                    StoreForever.newBuilder().
                    addBlock(
                      Reference.newBuilder().setHash(
                        sha3Hash(block1)
                      )
                    )
                  )
                )
              )
          ).build()
        ).getBlockList().size()
      );
  }

  /**
   * Test a more complex request for two blocks.
   */
  @Test
  void doubleBlockPartial() {
    // get an availability attestation for the block, and check it.
    assertEquals(2,
        client.wilburQuery(
          WilburQueryInput.newBuilder().setFillInTheBlank(
            Block.newBuilder().
              setAvailabilityAttestation(
                AvailabilityAttestation.newBuilder().setSignedStoreForever(
                  SignedStoreForever.newBuilder().setSignature(
                    Signature.newBuilder().setCryptoId(
                      service.getConfig().getCryptoId()
                    )
                  )
                )
              )
          ).build()
        ).getBlockList().size()
      );
  }
}
