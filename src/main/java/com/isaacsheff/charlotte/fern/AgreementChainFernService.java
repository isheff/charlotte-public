package com.isaacsheff.charlotte.fern;

import static com.isaacsheff.charlotte.fern.AgreementFernClient.checkAgreementIntegrityAttestation;

import com.isaacsheff.charlotte.collections.ConcurrentHolder;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.FernGrpc.FernImplBase;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.ChainSlot;
import com.isaacsheff.charlotte.proto.IntegrityPolicy;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationResponse;
import io.grpc.ServerBuilder;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

public class AgreementChainFernService extends AgreementFernService {
  /** Use logger for logging events in this class. */
  private static final Logger logger = Logger.getLogger(AgreementChainFernService.class.getName());

  /**
   * Get a new one of these Fern services using this local node.
   * @param node the local CharlotteNodeService
   * @return a new AgreementChainFernService
   */
  public static FernImplBase newFern(final CharlotteNodeService node) {
    return new AgreementChainFernService(node);
  }

  /**
   * Run as a main class with an arg specifying a config file name to run a Fern Agreement server.
   * creates and runs a new CharlotteNode which runs a Wilbur Service and a CharlotteNodeService, in a new thread.
   * @param args command line args. args[0] should be the name of the config file
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
  }

  /**
   * @param node a CharlotteNodeService with which we'll build a AgreementChainFernService
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final CharlotteNodeService node) {
    return new CharlotteNode(node,
                             ServerBuilder.forPort(node.getConfig().getPort()).addService(newFern(node)),
                             node.getConfig().getPort());
  }

  /**
   * @param configFilename the name of the configuration file for this CharlotteNode
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final Path configFilename) {
    return getFernNode(new CharlotteNodeService(configFilename));
  }

  /**
   * @param configFilename the name of the configuration file for this CharlotteNode
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final String configFilename) {
    return getFernNode(new CharlotteNodeService(configFilename));
  }

  /**
   * Make a new Fern with these attributes.
   * @param node the local CharlotteNodeService used to send and receive blocks 
   * @param commitments If we've seen a request for a given ChainSlot, this stores the response 
   */
  public AgreementChainFernService(final CharlotteNodeService node,
                                   final ConcurrentMap<ChainSlot,
                                                       ConcurrentHolder<RequestIntegrityAttestationResponse>>
                                           commitments){
    super(node, commitments);
  }

  /**
   * Make a new Fern with this node and no known commitments.
   * @param node the local CharlotteNodeService used to send and receive blocks 
   */
  public AgreementChainFernService(final CharlotteNodeService node) {
    super(node);
  }

  /**
   * Is this policy, alone, one which this server could ever accept?.
   * check that this ChainSlot actually has a block hash in it.
   * We also check that the parent reference is one we have previously attested to.
   * @return an error string if it's unacceptable, null if it's acceptable
   */
  @Override
  public String validPolicy(IntegrityPolicy policy) {
    super.validPolicy(policy);
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
    // We expect the client to provide proof that this Fern server has already committed to the parent of this block.
    // Therefore, in the parent reference, one of the integrity attestations referenced should be signed by this server
    // Here we check each of those attestations, and if we know about it (which we should), then we check
    //  to see that it really is a valid integrity attesation for the parent block signed by this server.
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
                    // and the parent attestation's signature actually uses the cryptoID for this Fern server
                    (parentAttestation.getIntegrityAttestation().getSignedChainSlot().getSignature().
                       getCryptoId().equals(
                     getNode().getConfig().getCryptoId()))){
                  return null; // all is well. We're good to go.
                }
              }
            }
          }
        }
      }
    }
    return "did not reference this node's Integrity Attestation for Parent";
  }
}
