package com.isaacsheff.charlotte.experiments;

import static com.isaacsheff.charlotte.fern.TimestampFern.getFernNode;
import static java.lang.Integer.parseInt;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.isaacsheff.charlotte.fern.TimestampFern;
import com.isaacsheff.charlotte.node.TimestampNode;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.Contact;

/**
 * A CharlotteNodeService implementation that waits to collect enough
 *  blocks (as set in experiment config file) before calling up its
 *  fern service to issue a new timestamp.
 * @author Isaac Sheff
 */
public class TimestampExperimentNode extends TimestampNode {
  /** used for logging stuff in this class */
  private static final Logger logger = Logger.getLogger(TimestampExperimentNode.class.getName());

  private final JsonExperimentConfig jsonConfig;

  /**
   * Create a new service with an empty map of blocks and an empty map of addresses.
   * @param referencesPerAttestation the number of blocks we want per timestamp we auto-request
   * @param fern the local timestamping service
   * @param config the Configuration settings for this Service
   */
  public TimestampExperimentNode(final JsonExperimentConfig jsonConfig,
                                 final int referencesPerAttestation,
                                 final TimestampFern fern,
                                 final Config config) {
    super(referencesPerAttestation, fern, config);
    this.jsonConfig = jsonConfig;
  }

  /**
   * Send this block to all known contacts IFF it's an integrity attestation.
   * Since each contact's sendBlock function is nonblocking, this will be done in parallel.
   * @param block the block to send
   */
  @Override
  public void broadcastBlock(final Block block) {
    if (block.hasIntegrityAttestation()) {
      // If this block is a signed timestamp deal with MULTIPLE REFERENCES, broadcast it.
      if (block.getIntegrityAttestation().hasSignedTimestampedReferences()
       && block.getIntegrityAttestation().getSignedTimestampedReferences().hasTimestampedReferences()
       && block.getIntegrityAttestation().getSignedTimestampedReferences().getTimestampedReferences().getBlockCount()>1){
      super.broadcastBlock(block);
      } else {
        // send it to people who are not Fern or Wilbur servers
        for (Entry<String, Contact> entry : getConfig().getContacts().entrySet()) {
          if (   (!jsonConfig.getFernServers().contains(entry.getKey()))
              && (!jsonConfig.getWilburServers().contains(entry.getKey()))) {
            entry.getValue().getCharlotteNodeClient().sendBlock(block);
          }
        }
      }
    }
  }

  /**
   * Start up a new server (on a new thread) using the config file (it
   *  should be a TimestampExperimentConfig) at the given file name.
   * @param filename the file name of the config file.
   */
  public static TimestampExperimentNode launchServer(String filename)  throws InterruptedException, IOException {
    final TimestampExperimentConfig config =
      (new ObjectMapper(new YAMLFactory())).readValue(Paths.get(filename).toFile(), TimestampExperimentConfig.class);
    final TimestampExperimentFern fern = new TimestampExperimentFern();
    final TimestampExperimentNode nodeService = new TimestampExperimentNode(
                                                      config,
                                                      config.getTimestampReferencesPerAttestation(),
                                                      fern,
                                                      new Config(config, Paths.get(filename).getParent()));
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
