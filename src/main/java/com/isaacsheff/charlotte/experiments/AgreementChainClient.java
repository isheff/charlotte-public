package com.isaacsheff.charlotte.experiments;

import static com.isaacsheff.charlotte.fern.AgreementChainFernClient.stripRequest;
import static com.isaacsheff.charlotte.fern.AgreementChainFernService.getFernNode;
import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.isaacsheff.charlotte.collections.ConcurrentHolder;
import com.isaacsheff.charlotte.fern.AgreementChainFernClient;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AgreementChainClient {
  /** used for logging events in this class **/
  private static final Logger logger = Logger.getLogger(AgreementChainClient.class.getName());

  /**
   * Run the experiment.
   * This attempts to append 3 blocks to an agreement chain, and checks at last that it has done so.
   * @param args command line arguments args[0] must be the config yaml file.
   */
  public static void main(String[] args) throws InterruptedException, FileNotFoundException, IOException {
    // start the client's CharlotteNode
    if (args.length < 1) {
      logger.log(Level.SEVERE, "no config file name given as argument");
      return;
    }
    final JsonExperimentConfig config =
      (new ObjectMapper(new YAMLFactory())).readValue(Paths.get(args[0]).toFile(), JsonExperimentConfig.class);

    CharlotteNodeService clientService = new CharlotteNodeService(new Config(config, Paths.get(args[0]).getParent()));
    final CharlotteNode clientNode = getFernNode(clientService);
    (new Thread(clientNode)).start();

    TimeUnit.SECONDS.sleep(1); // wait a second for the server to start up

    // mint a block, and send it out to the HetconsNodes
    Block[] blocks = new Block[config.getBlocksPerExperiment()];

    for (int i = 0; i < config.getBlocksPerExperiment(); ++i) {
      blocks[i] = Block.newBuilder().setStr("block contents "+i).build();
      clientService.onSendBlocksInput(blocks[i]);
    }

    // make a client using the local service, and the contact for the node
    AgreementChainFernClient client = new AgreementChainFernClient(clientService);
    // get an integrity attestation for the block, and check it.

    // get agreement on a root block
    for (String fern : config.getFernServers()) {
      client.sendWhenReady(clientService.getConfig().getContact(fern).getCryptoId(),
        RequestIntegrityAttestationInput.newBuilder().setPolicy(
          IntegrityPolicy.newBuilder().setFillInTheBlank(
            IntegrityAttestation.newBuilder().setSignedChainSlot(
              SignedChainSlot.newBuilder().
                setChainSlot(
                  ChainSlot.newBuilder().
                    setSlot(0).
                    setRoot(Reference.newBuilder().setHash(sha3Hash(blocks[0]))).
                    setBlock(Reference.newBuilder().setHash(sha3Hash(blocks[0])))).
                setSignature(Signature.newBuilder().setCryptoId(clientService.getConfig().getContact(fern).getCryptoId()))
        ))).build());
    }

    // Once the root block is set up, all the TCP channels and whatnot
    //  are ready, so we should pause for any lingering computations
    //  to complete, so we can really time the next part at its best.
    TimeUnit.SECONDS.sleep(5); // wait a second for the server to start up

    // record the inputs sent out for each fern server
    RequestIntegrityAttestationInput[] inputs =new RequestIntegrityAttestationInput[config.getFernServers().size()];
    // get agreement on the other blocks
    for (int i = 1; i < config.getBlocksPerExperiment(); ++i) {
      int f = 0;
      logger.info("Setting up request for block "+i);
      for (String fern : config.getFernServers()) {
        inputs[f] =
          RequestIntegrityAttestationInput.newBuilder().setPolicy(
            IntegrityPolicy.newBuilder().setFillInTheBlank(
              IntegrityAttestation.newBuilder().setSignedChainSlot(
                SignedChainSlot.newBuilder().
                  setChainSlot(
                    ChainSlot.newBuilder().
                      setSlot(i).
                      setRoot(Reference.newBuilder().setHash(sha3Hash(blocks[0]))).
                      setBlock(Reference.newBuilder().setHash(sha3Hash(blocks[i]))).
                      setParent(Reference.newBuilder().setHash(sha3Hash(blocks[i-1])))).
                  setSignature(Signature.newBuilder().setCryptoId(clientService.getConfig().getContact(fern).getCryptoId()))
          ))).build();
        client.sendWhenReady(clientService.getConfig().getContact(fern).getCryptoId(), inputs[f]);
        ++f;
      }
    }


    // wait to receive an attestation from each fern server for the last input
    for (int f = 0; f < config.getFernServers().size(); ++f) {
      client.getKnownResponses().putIfAbsent(stripRequest(inputs[f]), new ConcurrentHolder<Hash>());
      if (null == client.getKnownResponses().get(stripRequest(inputs[f])).get()) {
        logger.log(Level.SEVERE, "null known response at end of chain. This should not be possible");
      }
    }
    logger.info("Experiment Complete");
    client.shutdown();
    clientNode.stop();
    System.exit(0);
  }
}
