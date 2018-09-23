package com.isaacsheff.charlotte.experiments;

import static com.google.protobuf.util.Timestamps.fromMillis;
import static com.isaacsheff.charlotte.node.SignatureUtil.signBytes;
import static java.lang.System.currentTimeMillis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.IntegrityAttestation;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.SignedTimestampedReferences;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.TimestampedReferences;

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
    CharlotteNodeService clientService = new SilentBroadcastNode(args[0]);
    (new Thread(new CharlotteNode(clientService))).start();
    final TimestampExperimentConfig config =
      (new ObjectMapper(new YAMLFactory())).readValue(Paths.get(args[0]).toFile(), TimestampExperimentConfig.class);

    Block[] blocks = new Block[config.getBlocksPerExperiment()*2];

    for (int i = 0; i < (config.getBlocksPerExperiment() * 2); ++i) {
      blocks[i] = Block.newBuilder().setStr("block contents "+i).build();
    }
    TimeUnit.SECONDS.sleep(1); // wait a second for the server to start up
    // send out an attetation (attesting to 0 blocks) to get those channels warmed up!
    final TimestampedReferences dummy =
      TimestampedReferences.newBuilder().setTimestamp(fromMillis(currentTimeMillis())).build();
    clientService.onSendBlocksInput(Block.newBuilder().setIntegrityAttestation(
          IntegrityAttestation.newBuilder().setSignedTimestampedReferences(
            SignedTimestampedReferences.newBuilder().
              setTimestampedReferences(dummy).
              setSignature(signBytes(clientService.getConfig().getKeyPair(), dummy))
              )).build());


    TimeUnit.SECONDS.sleep(10); // wait a second for the server to start up

    logger.info("Begin Experiment");
    int fernCount = config.getFernServers().size();
    for (int i = 0; i < config.getBlocksPerExperiment(); ++i) {
      clientService.sendBlock(config.getFernServers().get(i % fernCount), blocks[i]);
    }

    TimeUnit.SECONDS.sleep(30); // wait a second for the servers to warm up
    logger.info("SECOND ROUND");
    for (int i = config.getBlocksPerExperiment(); i < (config.getBlocksPerExperiment() * 2); ++i) {
      clientService.sendBlock(config.getFernServers().get(i % fernCount), blocks[i]);
    }
    logger.info("All blocks sent");

    TimeUnit.SECONDS.sleep(60); // wait for everything to be done.
    System.exit(0);
  }
}
