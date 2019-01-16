package com.isaacsheff.charlotte.experiments;

import static com.google.protobuf.util.Timestamps.fromMillis;
import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
import static java.lang.System.currentTimeMillis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.IntegrityAttestation;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.SignedTimestampedReferences;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.TimestampedReferences;
import com.isaacsheff.charlotte.proto.IntegrityPolicy;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationResponse;
import com.isaacsheff.charlotte.proto.Signature;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the timestamp experiment.
 * This will divide (2 * blocksPerExperiment) blocks amongst all known Timestamp fern servers,
 *  and then send each server blocks as fast as it can.
 * It will pause after having sent blocksPerExperiment blocks, so the system can catch up
 *  before it begins the "real experiment," which is the last blocksPerExperiment blocks.
 * @author Isaac Sheff
 */
public class TimestampExperimentClient extends AgreementNClient {
  /** used for logging events in this class **/
  private static final Logger logger = Logger.getLogger(TimestampExperimentClient.class.getName());

  /**
   * Start up a new client.
   * This does not initiate the experiment.
   * To initiate the experiment, FIXME
   * @param service the local CharlotteNodeService (for receiving blocks and such)
   * @param config the experimental config.
   */
  public TimestampExperimentClient(final CharlotteNodeService service, final JsonExperimentConfig config) {
    super(service, config);
  }

  @Override
  public void onFernResponse(final RequestIntegrityAttestationResponse response,
                             final RequestIntegrityAttestationInput request) {
    // Do nothing. 
  }

  /**
   * Request a timestamp from a fern server for a single block (referenced).
   * This will be queued to be sent asynchronously. 
   * Expect onFernResponse to be called when a Fern server responds.
   * @param client the TimestampExperimentClient making this request
   * @param fernCryptoId the Crypto Id of the fern server you'd like to request from
   * @param block the block you want timestamped.
   */
  private static void requestTimestamp(final TimestampExperimentClient client,
                                       final CryptoId fernCryptoId,
                                       final Block block)
    throws InterruptedException {
    client.getRequestQueues().get(fernCryptoId).put(
        RequestIntegrityAttestationInput.newBuilder().setPolicy(
          IntegrityPolicy.newBuilder().setFillInTheBlank(
            IntegrityAttestation.newBuilder().setSignedTimestampedReferences(
              SignedTimestampedReferences.newBuilder().
                setSignature(
                  Signature.newBuilder().setCryptoId(fernCryptoId)). // signed by me
                setTimestampedReferences(
                  TimestampedReferences.newBuilder().addBlock(Reference.newBuilder().setHash(sha3Hash(block))).
                    setTimestamp(fromMillis(currentTimeMillis()))
              )))).build());
  }

  /**
   * Runs the timestamp experiment.
   * This will divide (2 * blocksPerExperiment) blocks amongst all known Timestamp fern servers,
   *  and then send each server blocks as fast as it can.
   * It will pause after having sent blocksPerExperiment blocks, so the system can catch up
   *  before it begins the "real experiment," which is the last blocksPerExperiment blocks.
   * @param args command line arguments args[0] must be the config yaml file encoding JsonExperimentConfig.
   */
  public static void main(String[] args) throws InterruptedException, FileNotFoundException, IOException {
    // start the client's CharlotteNode
    if (args.length < 1) {
      logger.log(Level.SEVERE, "no config file name given as argument");
      return;
    }
    final TimestampExperimentConfig config =
      (new ObjectMapper(new YAMLFactory())).readValue(Paths.get(args[0]).toFile(), TimestampExperimentConfig.class);
    final TimestampExperimentClient client = new TimestampExperimentClient(new SilentBroadcastNode(args[0]), config);
    (new Thread(new CharlotteNode(client.getService()))).start();

    Block[] blocks = new Block[config.getBlocksPerExperiment()*2];

    for (int i = 0; i < (config.getBlocksPerExperiment() * 2); ++i) {
      blocks[i] = Block.newBuilder().setStr("block contents "+i).build();
    }
    TimeUnit.SECONDS.sleep(1); // wait a second for the server to start up

    logger.info("Begin Experiment");
    final int fernCount = config.getFernServers().size();
    for (int i = 0; i < config.getBlocksPerExperiment(); ++i) {
      requestTimestamp(client,
                       client.getService().getConfig().getContact(config.getFernServers().get(i % fernCount)).getCryptoId(),
                       blocks[i]);
    }

    TimeUnit.SECONDS.sleep(30); // wait a second for the servers to warm up
    logger.info("SECOND ROUND");
    for (int i = config.getBlocksPerExperiment(); i < (config.getBlocksPerExperiment() * 2); ++i) {
      requestTimestamp(client,
                       client.getService().getConfig().getContact(config.getFernServers().get(i % fernCount)).getCryptoId(),
                       blocks[i]);
    }
    logger.info("All blocks sent");

    TimeUnit.SECONDS.sleep(60); // wait for everything to be done.
    client.done();
    TimeUnit.SECONDS.sleep(15); // wait for threads to shut down and such.
    System.exit(0);
  }
}
