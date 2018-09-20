package com.isaacsheff.charlotte.experiments;

import static com.isaacsheff.charlotte.fern.AgreementChainFernClient.stripRequest;
import static com.isaacsheff.charlotte.fern.AgreementChainFernService.getFernNode;
import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.isaacsheff.charlotte.collections.ConcurrentHolder;
import com.isaacsheff.charlotte.fern.AgreementChainFernClient;
import com.isaacsheff.charlotte.fern.TimestampClient;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.TimestampNode;
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

public class TimestampExperimentClient {
  /** used for logging events in this class **/
  private static final Logger logger = Logger.getLogger(TimestampExperimentClient.class.getName());

  /**
   * Run the experiment.
   * @param args command line arguments args[0] must be the config yaml file.
   */
  public static void main(String[] args) throws InterruptedException, FileNotFoundException, IOException {
    // start the client's CharlotteNode
    if (args.length < 1) {
      logger.log(Level.SEVERE, "no config file name given as argument");
      return;
    }
    CharlotteNodeService clientService = TimestampExperimentNode.launchServer(args[0]);
    final TimestampExperimentConfig config =
      (new ObjectMapper(new YAMLFactory())).readValue(Paths.get(args[0]).toFile(), TimestampExperimentConfig.class);

    Block[] blocks = new Block[config.getBlocksPerExperiment()];

    for (int i = 0; i < config.getBlocksPerExperiment(); ++i) {
      blocks[i] = Block.newBuilder().setStr("block contents "+i).build();
    }

    TimeUnit.SECONDS.sleep(10); // wait a second for the server to start up

    logger.info("Begin Experiment");
    int fernCount = config.getFernServers().size();
    for (int i = 0; i < config.getBlocksPerExperiment(); ++i) {
      clientService.sendBlock(config.getFernServers().get(i % fernCount), blocks[i]);
    }
    logger.info("All blocks sent");
  }
}
