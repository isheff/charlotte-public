package com.isaacsheff.charlotte.experiments;

import static com.isaacsheff.charlotte.fern.AgreementFernClient.checkAgreementIntegrityAttestation;
import static java.lang.Integer.parseInt;
import static java.util.concurrent.ConcurrentHashMap.newKeySet;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.isaacsheff.charlotte.fern.AgreementChainFernService;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.IntegrityPolicy;
import com.isaacsheff.charlotte.proto.Reference;

import io.grpc.ServerBuilder;

import java.util.logging.Level;
import java.util.logging.Logger;

public class AgreementNFern extends AgreementChainFernService {
  /** Use logger for logging events in this class. */
  private static final Logger logger = Logger.getLogger(AgreementNFern.class.getName());

  /** the experiment config file */
  private final JsonExperimentConfig config;
  /** the number of attestations needed to consider a block committed (default > 2/3 |ferns|) */
  private final int agreementQuorumSize;
  /** the set of fern servers' cryotoIds */
  private final Set<CryptoId> ferns;

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

  /**
   * @param node a CharlotteNodeService with which we'll build a AgreementChainFernService
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final String filename) {
    try {
      final JsonExperimentConfig config =
        (new ObjectMapper(new YAMLFactory())).readValue(Paths.get(filename).toFile(), JsonExperimentConfig.class);
      final CharlotteNodeService node = new CharlotteNodeService(filename);
      return new CharlotteNode(node,
                   ServerBuilder.forPort(node.getConfig().getPort()).addService(new AgreementNFern(config, node)),
                   node.getConfig().getPort());
    } catch (IOException e) {
      logger.log(Level.SEVERE, "could not read config", e);
    }
    return null;
  }

  /**
   * @param config the experiment config file
   * @param service the local CharlotteNodeService
   */
  public AgreementNFern(JsonExperimentConfig config, CharlotteNodeService service) {
    super(service);
    this.config = config;
    agreementQuorumSize = 1 + (( 2 * this.config.getFernServers().size()) / 3);
    ferns = newKeySet();
    for (String fern : this.config.getFernServers()) {
      ferns.add(service.getConfig().getContact(fern).getCryptoId());
    }

  }

  /** the experiment config file */
  public JsonExperimentConfig getJsonConfig() {return config;}

  /**
   * Is this policy, alone, one which this server could ever accept?.
   * check that this ChainSlot actually has a block hash in it.
   * We also check that the parent has enough attestsations.
   * @return an error string if it's unacceptable, null if it's acceptable
   */
  @Override
  public String validPolicy(IntegrityPolicy policy) {
    if ( policy.getFillInTheBlank().getSignedChainSlot().getChainSlot().getSlot() == 0) { // this is a root
      return null;
    }
    if ( policy.getFillInTheBlank().getSignedChainSlot().getChainSlot().getSlot() < 0) {
      return "slot number < 0";
    }
    if (!policy.getFillInTheBlank().getSignedChainSlot().getChainSlot().hasParent()) {
      return "The ChainSlot in this RequestIntegrityAttestationInput doesn't have a Parent.";
    }
    if (!policy.getFillInTheBlank().getSignedChainSlot().getChainSlot().getParent().hasHash()) {
      return "The ChainSlot Parent reference in this RequestIntegrityAttestationInput doesn't have a Hash.";
    }
    // We expect the client to provide proof that enough Fern servers have already committed to the parent of this block.
    // Therefore, in the parent reference, enough integrity attestations referenced should be signed by ferns
    // Here we check each of those attestations, and if we know about it (which we should), then we check
    //  to see that it really is a valid integrity attesation for the parent block signed by a fern, and count.
    //
    int ferns_attested = 0;
    for (Reference reference :
         policy.getFillInTheBlank().getSignedChainSlot().getChainSlot().getParent().getIntegrityAttestationsList()) {
      if (reference.hasHash()) {
        final Block parentAttestation = getNode().getBlockMap().get(reference.getHash()); // NOT BLOCKING
        if (parentAttestation != null) {
          if(checkAgreementIntegrityAttestation(parentAttestation) != null) { 
            if (parentAttestation.getIntegrityAttestation().getSignedChainSlot().getChainSlot().hasBlock()) {
              if(parentAttestation.getIntegrityAttestation().getSignedChainSlot().getChainSlot().getBlock().hasHash()){
                if (// The parent attestation actually refers to the same block listed as parent
                    (parentAttestation.getIntegrityAttestation().getSignedChainSlot().getChainSlot().getBlock().
                       getHash().equals(
                     policy.getFillInTheBlank().getSignedChainSlot().getChainSlot().getParent().getHash())) &&
                    // and the parent attestation's signature actually uses the cryptoID for a Fern server
                    ferns.contains(parentAttestation.getIntegrityAttestation().getSignedChainSlot().getSignature().
                       getCryptoId())) {
                  ++ferns_attested;
                  if (ferns_attested >= agreementQuorumSize) {
                    return null; // all is well. We're good to go.
                  }
                }
              }
            }
          }
        }
      }
    }
    return "did not reference enough Ferns' Integrity Attestations for Parent: " + policy;
  }
}
