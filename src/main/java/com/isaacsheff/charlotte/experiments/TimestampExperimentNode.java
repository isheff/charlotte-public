package com.isaacsheff.charlotte.experiments;

import static com.isaacsheff.charlotte.fern.TimestampFern.getFernNode;
import static java.lang.Integer.parseInt;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.isaacsheff.charlotte.fern.TimestampFern;
import com.isaacsheff.charlotte.node.TimestampNode;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.yaml.Config;

public class TimestampExperimentNode extends TimestampNode {
  /** used for logging stuff in this class */
  private static final Logger logger = Logger.getLogger(TimestampExperimentNode.class.getName());

  /**
   * Create a new service with an empty map of blocks and an empty map of addresses.
   * @param referencesPerAttestation the number of blocks we want per timestamp we auto-request
   * @param fern the local timestamping service
   * @param config the Configuration settings for this Service
   */
  public TimestampExperimentNode(int referencesPerAttestation, TimestampFern fern, Config config) {
    super(referencesPerAttestation, fern, config);
  }


  /**
   * Send this block to all known contacts IFF it's an integrity attestation.
   * Since each contact's sendBlock function is nonblocking, this will be done in parallel.
   * @param block the block to send
   */
  @Override
  public void broadcastBlock(Block block) {
    if (block.hasIntegrityAttestation()) {
      super.broadcastBlock(block);
    }
  }



  public static TimestampExperimentNode launchServer(String filename)  throws InterruptedException, IOException {
    final TimestampExperimentConfig config =
      (new ObjectMapper(new YAMLFactory())).readValue(Paths.get(filename).toFile(), TimestampExperimentConfig.class);
    final TimestampExperimentFern fern = new TimestampExperimentFern();
    final TimestampExperimentNode nodeService = new TimestampExperimentNode(config.getTimestampReferencesPerAttestation(),
        fern, new Config(config, Paths.get(filename).getParent()));
    fern.setNode(nodeService);
    
    final Thread thread = new Thread(getFernNode(fern));
    thread.start();
    logger.info("TimestampExperimentFern service started on new thread");
    thread.join();
    return nodeService;
  }



  /**
   * Run as a main class with an arg specifying a config file name to run a Fern Timestamp server.
   * creates and runs a new CharlotteNode which runs a Fern Service
   *  and a TimestampNode service (which is a CharlotteNode Service), in a new thread.
   * @param args command line args. args[0] should be the name of the config file, args[1] is BlocksPerTimestamp, args[2] (optional) is timeout until the server shuts down, in seconds
   */
  public static void main(String[] args) throws InterruptedException, IOException {
    if (args.length < 1) {
      System.out.println("Correct Usage: FernService configFileName.yaml");
      return;
    }
    launchServer(args[0]);
    if (args.length < 3) {
      TimeUnit.SECONDS.sleep(Integer.MAX_VALUE);
    } else {
      TimeUnit.SECONDS.sleep(parseInt(args[2]));
    }
  }
}
