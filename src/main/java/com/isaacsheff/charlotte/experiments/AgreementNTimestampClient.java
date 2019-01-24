package com.isaacsheff.charlotte.experiments;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.LogHashService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.JsonContact;

public class AgreementNTimestampClient {
  /** used for logging events in this class **/
  private static final Logger logger = Logger.getLogger(AgreementNTimestampClient.class.getName());

  /**
   * Run the experiment.
   * @param args command line arguments. args[0] must be the config yaml file for the Agreement Chain.
   *                                     args[1] must be the config yaml file for the Timestamping.
   */
  public static void main(String[] args) throws InterruptedException, FileNotFoundException, IOException {
    // start the client's CharlotteNode
    if (args.length < 2) {
      logger.log(Level.SEVERE, "Requires 2 config files given as arguments.");
      return;
    }
    final JsonExperimentConfig agreementConfig =
      (new ObjectMapper(new YAMLFactory())).readValue(Paths.get(args[0]).toFile(), JsonExperimentConfig.class);
    final TimestampExperimentConfig timestampConfig =
      (new ObjectMapper(new YAMLFactory())).readValue(Paths.get(args[1]).toFile(), TimestampExperimentConfig.class);
    final Set<String> fernServers = new HashSet<String>(agreementConfig.getFernServers());
    fernServers.addAll(timestampConfig.getFernServers());
    final Set<String> wilburServers = new HashSet<String>(agreementConfig.getWilburServers());
    wilburServers.addAll(timestampConfig.getWilburServers());
    final Map<String, JsonContact> contacts = new HashMap<String, JsonContact>(agreementConfig.getContacts());
    contacts.putAll(timestampConfig.getContacts());
    final JsonExperimentConfig combinedConfig = new JsonExperimentConfig(
      new ArrayList<String>(fernServers),
      new ArrayList<String>(wilburServers),
      agreementConfig.getBlocksPerExperiment(),
      agreementConfig.getWilburThreshold(),
      agreementConfig.getPrivateKey(),
      agreementConfig.getMe(),
      contacts,
      agreementConfig.getBlocksize()
      );

    final CharlotteNodeService service = new LogHashService(new Config(combinedConfig, Paths.get(args[0]).getParent())) {
      @Override
      public void broadcastBlock(final Block block) {
        // Broadcast the block IFF it's not a timestamp
        // Timestamp requests don't involve sending blocks
        if ((!block.hasIntegrityAttestation()) || (!block.getIntegrityAttestation().hasSignedTimestampedReferences())) {
          // broadcast the block only to the agreement servers.
          for (String contact : agreementConfig.getContacts().keySet()) {
            sendBlock(contact, block);
          }
        }
      }
    };


    final TimestampExperimentClient timestampClient = new TimestampExperimentClient(service, timestampConfig);

    final AgreementNClient agreementClient = new AgreementNClient(service, agreementConfig) {
      @Override
      public void broadcastRequest(final Reference.Builder parentBuilder, final int slot) {
        try {
          if (slot == timestampConfig.getBlocksPerExperiment()) {
            logger.info("SECOND ROUND"); // used in the timestamping experiment to denote when we're done warming up.
          }
          timestampClient.requestTimestamp(
            service.getConfig().getContact(
                timestampConfig.getFernServers().get(slot % timestampConfig.getFernServers().size())
              ).getCryptoId(),
            getBlocks()[slot]);
        } catch (InterruptedException e) {
          logger.log(Level.SEVERE, "interrupted while requesting timestamp", e);
        }
        super.broadcastRequest(parentBuilder, slot);
      }
    };


    (new Thread(new CharlotteNode(service))).start();

    TimeUnit.SECONDS.sleep(1); // wait for servers to start up
    agreementClient.broadcastRequest(Reference.newBuilder(), 0); // send out the root block
    agreementClient.waitUntilDone();
    logger.info("All blocks sent"); // used in timestamping experiment
    System.exit(0);
  }
}
