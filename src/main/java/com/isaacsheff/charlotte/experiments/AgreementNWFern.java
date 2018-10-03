package com.isaacsheff.charlotte.experiments;

import static com.isaacsheff.charlotte.node.SignatureUtil.checkSignature;
import static java.lang.Integer.parseInt;

import java.io.IOException;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.proto.IntegrityPolicy;
import com.isaacsheff.charlotte.proto.Reference;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Fern server for the AgreementNW experiment.
 * This fern server attests to the first block it sees for a
 *  particular slot iff its parent reference has enough integrity
 *  attestations, and all references have enough wilbur attestations.
 * The number of wilbur servers representing "sufficiently available"
 *  for a block is set in the JsonExperimentConfig.
 * This experiment uses the JsonExperimentConfig.blockSize parameter to determind block payload size.
 * @author Isaac Sheff
 */
public class AgreementNWFern extends AgreementNFern {

  /** Use logger for logging events in this class. */
  private static final Logger logger = Logger.getLogger(AgreementNWFern.class.getName());

  /**
   * Make a new fern service.
   * @param config the experiment config file
   * @param service the local CharlotteNodeService
   */
  public AgreementNWFern(JsonExperimentConfig config, CharlotteNodeService service) {
    super(config, service);
  }

  /**
   * Does this reference have enough valid availability attestations?.
   * @param config the raw experimental configuration
   * @param service the local CharlotteNodeService used for sending blocks and such.
   * @param threshold how many Wilbur servers do you need to be sufficiently available? (> threshold)
   * @param reference the reference you want to judge the availability of
   */
  public static boolean sufficientAvailability(final JsonExperimentConfig config,
                                               final CharlotteNodeService service,
                                               final int threshold,
                                               final Reference reference)  {
    int count = 0;
    for (Hash hash : reference.getAvailabilityAttestationsList()) {
      Block availabilityAttestation = service.getBlockMap().get(hash);
      if (availabilityAttestation != null) {
        if (availabilityAttestation.hasAvailabilityAttestation()
            && availabilityAttestation.getAvailabilityAttestation().hasSignedStoreForever()
            && availabilityAttestation.getAvailabilityAttestation().getSignedStoreForever().hasSignature()
            && availabilityAttestation.getAvailabilityAttestation().getSignedStoreForever().hasStoreForever()
            && checkSignature(
                availabilityAttestation.getAvailabilityAttestation().getSignedStoreForever().getStoreForever(),
                availabilityAttestation.getAvailabilityAttestation().getSignedStoreForever().getSignature())) {
          // if a known Wilbur server signed it
          for (String wilburName : config.getWilburServers()) {
            if (availabilityAttestation.getAvailabilityAttestation().getSignedStoreForever().getSignature().
                  getCryptoId().equals(service.getConfig().getContact(wilburName).getCryptoId())) {
              // if it actually references this reference
              for (Reference r : availabilityAttestation.getAvailabilityAttestation().
                                 getSignedStoreForever().getStoreForever().getBlockList()) {
                if (reference.getHash().equals(r.getHash())) {
                  ++count;
                }
              }
            }
          }
        }
      }
    }
    return (count > threshold);
  }

  /**
   * Is this policy, alone, one which this server could ever accept?.
   * check that this ChainSlot actually has a block hash in it.
   * We also check that the parent has enough attestsations.
   * We also check whether (basically everything) is sufficiently available.
   * @return an error string if it's unacceptable, null if it's acceptable
   */
  @Override
  public String validPolicy(IntegrityPolicy policy) {
    final String integrityValid = super.validPolicy(policy);
    if (integrityValid != null) {
      return integrityValid;
    }
    final int threshold = getJsonConfig().getWilburThreshold();
    if (!sufficientAvailability(getJsonConfig(), getNode(), threshold,
          policy.getFillInTheBlank().getSignedChainSlot().getChainSlot().getBlock())) {
      return "insufficiently available block";
    }
    for (Reference integrityAttestation : policy.getFillInTheBlank().getSignedChainSlot().getChainSlot().
                                          getParent().getIntegrityAttestationsList()) {
      if (!sufficientAvailability(getJsonConfig(), getNode(), threshold, integrityAttestation)) {
        return "insufficiently available parent attestation";
      }
    }
    return null;
 }


  /**
   * Create a new CharlotteNode featuring a AgreementNWFern Fern service and a CharlotteNodeService.
   * @param node a CharlotteNodeService with which we'll build a AgreementChainFernService
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final String filename) {
    try {
      final JsonExperimentConfig config =
        (new ObjectMapper(new YAMLFactory())).readValue(Paths.get(filename).toFile(), JsonExperimentConfig.class);
      final CharlotteNodeService node = new CharlotteNodeService(filename);
      return new CharlotteNode(node, new AgreementNWFern(config, node));
    } catch (IOException e) {
      logger.log(Level.SEVERE, "could not read config", e);
    }
    return null;
  }

  /**
   * Run as a main class with an arg specifying a config file name to run a Fern Agreement server.
   * creates and runs a new CharlotteNode which runs a Wilbur Service and a CharlotteNodeService, in a new thread.
   * @param args command line args. args[0] should be the name of the config file, and args[1] is auto-shutdown time in seconds
   */
  public static void main(String[] args) throws InterruptedException{
    if (args.length < 1) {
      System.out.println("Correct Usage: FernService configFileName.yaml");
      return;
    }
    final Thread thread = new Thread(getFernNode(args[0]));
    thread.start();
    logger.info("Fern service started on new thread");
    thread.join();
    if (args.length < 2) {
      TimeUnit.SECONDS.sleep(Integer.MAX_VALUE);
    } else {
      TimeUnit.SECONDS.sleep(parseInt(args[1]));
    }
  }


}
