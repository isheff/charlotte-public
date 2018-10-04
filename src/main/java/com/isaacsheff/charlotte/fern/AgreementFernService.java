package com.isaacsheff.charlotte.fern;

import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
import static com.isaacsheff.charlotte.node.SignatureUtil.signBytes;

import com.isaacsheff.charlotte.collections.ConcurrentHolder;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.FernGrpc.FernImplBase;
import com.isaacsheff.charlotte.proto.IntegrityAttestation;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.ChainSlot;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.SignedChainSlot;
import com.isaacsheff.charlotte.proto.IntegrityPolicy;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationResponse;

import io.grpc.stub.StreamObserver;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * A Fern server that runs agreement.
 * That is to say: this Fern server will, when asked, commit to a block
 * in a slot on a chain, and never contradict itself.
 * If you ask it for commitments to the same slot with different blocks,
 * it will keep referring you to the same attestation, where it commits
 * to one block.
 *
 * <p>
 * Can be run as a main class with: AgreementFernService configFileName.yaml
 * </p>
 *
 * <p>
 * Future extensions may wish to override validPolicy, as it designates
 *  (in a vacuum, so to speak), what policies this Fern server
 *   considers acceptable.
 * Alternatively, requestIntegrityAttestation contains almost all the
 *  functionality (it calls validPolicy), so you could override that
 *  to completely change what the server does.
 * </p>
 *
 * <p>
 * Furthermore, future extensions may wish to override newResponse and
 *  newAttestation (called by requestIntegrityAttestation), which govern
 *  how responses are made to new requests (which don't conflict with anything
 *  seen so far). 
 * By default, newResponse just calls newAttestation and makes a simple
 *  reference to the attestation block.
 * </p>
 * @author Isaac Sheff
 */
public class AgreementFernService extends FernImplBase {

  /** Use logger for logging events in this class. */
  private static final Logger logger = Logger.getLogger(AgreementFernService.class.getName());

  /** If we've seen a request for a given ChainSlot, this stores the response (or a standin that will be filled appropriately) */
  private final ConcurrentMap<ChainSlot, ConcurrentHolder<RequestIntegrityAttestationResponse>> commitments;

  /** The local CharlotteNodeService used to send and receive blocks */
  private final CharlotteNodeService node;

  /**
   * Run as a main class with an arg specifying a config file name to
   * run a Fern Agreement server.
   * Creates and runs a new CharlotteNode which runs a Wilbur Service
   * and a CharlotteNodeService, in a new thread.
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
   * Get a new one of these Fern services using this local node.
   * @param node the local CharlotteNodeService
   * @return a new AgreementFernService
   */
  public static FernImplBase newFern(final CharlotteNodeService node) {
    return new AgreementFernService(node);
  }

  /**
   * @param node a CharlotteNodeService with which we'll build a AgreementFernService
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final CharlotteNodeService node) {
    return new CharlotteNode(node, newFern(node));
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
  public AgreementFernService(final CharlotteNodeService node,
                              final ConcurrentMap<ChainSlot, ConcurrentHolder<RequestIntegrityAttestationResponse>> commitments){
    this.node = node;
    this.commitments = commitments;
  }

  /**
   * Make a new Fern with this node and no known commitments.
   * @param node the local CharlotteNodeService used to send and receive blocks 
   */
  public AgreementFernService(final CharlotteNodeService node) {
    this(node, new ConcurrentHashMap<ChainSlot, ConcurrentHolder<RequestIntegrityAttestationResponse>>());
  }

  /** @return The local CharlotteNodeService used to send and receive blocks */
  public CharlotteNodeService getNode() {return node;}

  /** @return If we've seen a request for a given ChainSlot, this stores the response  */
  public ConcurrentMap<ChainSlot, ConcurrentHolder<RequestIntegrityAttestationResponse>> getCommitments() {return commitments;}

  /**
   * Is this policy, alone, one which this server could ever accept?.
   * For now, we just check that this ChainSlot actually has a block hash in it.
   * @return an error string if it's unacceptable, null if it's acceptable
   */
  public String validPolicy(IntegrityPolicy policy) {
    if (!policy.getFillInTheBlank().getSignedChainSlot().getChainSlot().getBlock().hasHash()) {
      return "The ChainSlot Block reference in this RequestIntegrityAttestationInput doesn't have a Hash.";
    }
    return null;
  }

  /**
   * Actually constructs the block featuring the IntegrityAttestation in response to a given policy.
   * It's a simple block, just a copy of the fill-in-the-blank, with the signature filled in.
   * There may be a more efficient way to build this.
   * @param policy the Integrity Policy
   * @return the block featuring the IntegrityAttestation in response to a given policy.
   */
  public Block makeNewAttestation(final IntegrityPolicy policy) {
    return Block.newBuilder().setIntegrityAttestation(
        IntegrityAttestation.newBuilder(policy.getFillInTheBlank()).
          setSignedChainSlot(SignedChainSlot.newBuilder(policy.getFillInTheBlank().getSignedChainSlot()).
                               setSignature(signBytes(
                                   getNode().getConfig().getKeyPair(),
                                   policy.getFillInTheBlank().getSignedChainSlot().getChainSlot())
        ))).build();
  }


  /**
   * Called when a new attestation is warranted in response to a given policy.
   * Constructs the block, then receives (and broadcasts) it via the local CharlotteNodeService.
   * Will only be called with a policy that:
   * <ul>
   * <li> has a FillInTheBlank SignedChainSlot type </li>
   * <li> the ChainSlot has a root element          </li>
   * <li> the ChainSlot has a slot number           </li>
   * <li> the chainSlot has a block reference       </li>
   * <li> passes validPolicy (returns null)         </li>
   * <li> we haven't attested to any other block with this root/slot combo before. </li>
   * </ul>
   * @param policy the Integrity Policy
   * @return the block featuring the IntegrityAttestation in response to a given policy.
   */
  public Block newAttestation(final IntegrityPolicy policy) {
    final Block block = makeNewAttestation(policy);
    getNode().onSendBlocksInput(block);
    return block;
  }

  /**
   * If we've just got a new request, and have just created (and
   * broadcast) an attestation which fulfills that request.
   * This response will be remembered, so it must not be an error.
   * We need to make a RequestIntegrityAttestationResponse to tell the client about the attestation.
   * @param block the Integrity Attestation
   * @return RequestIntegrityAttestationResponse to tell the client about the attestation.
   */
  public RequestIntegrityAttestationResponse newResponse(final Block block) {
    // simple reference, no availability attestations
    return RequestIntegrityAttestationResponse.newBuilder().setReference(Reference.newBuilder().setHash(sha3Hash(block))).build();
  }

  /**
   * Called when a new request warrants a response (so none of our old responses will do).
   * This response will be remembered, so it must not be an error.
   * Creates a new attestation for the given policy.
   * This constructs the block, then receives (and broadcasts) it via the local CharlotteNodeService.
   * Will only be called with a policy that:
   * <ul>
   * <li> has a FillInTheBlank SignedChainSlot type </li>
   * <li> the ChainSlot has a root element          </li>
   * <li> the ChainSlot has a slot number           </li>
   * <li> the chainSlot has a block reference       </li>
   * <li> passes validPolicy (returns null)         </li>
   * <li> we haven't attested to any other block with this root/slot combo before. </li>
   * </ul>
   * @param policy the Integrity Policy
   * @return the RequestIntegrityAttestationResponse to send to the client over the wire.
   */
  public RequestIntegrityAttestationResponse newResponse(final IntegrityPolicy policy) {
    return newResponse(newAttestation(policy));
  }

  /**
   * Checks to see if all is well with this request, then checks if a
   * conflicting request has already been answered, and if not, makes a
   * new Integrity Attestation.
   * This only handles ChainedSlot type Integrity Policies.
   * @param request details what we want attested to
   * @return RequestIntegrityAttestationResponse featues an error message or a reference to an attestation.
   */
  public RequestIntegrityAttestationResponse requestIntegrityAttestation(final RequestIntegrityAttestationInput request) {
    if (!request.hasPolicy()) {
      return RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(
               "There is no policy in this RequestIntegrityAttestationInput.").build();
    }
    if (!request.getPolicy().hasFillInTheBlank()) {
      return RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(
               "The policy in this RequestIntegrityAttestationInput isn't FillInTheBlank.").build();
    }
    if (!request.getPolicy().getFillInTheBlank().hasSignedChainSlot()) {
      return RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(
               "The policy in this RequestIntegrityAttestationInput isn't SignedChainSlot.").build();
    }
    if (!request.getPolicy().getFillInTheBlank().getSignedChainSlot().hasChainSlot()) {
      return RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(
               "The SignedChainSlot in this RequestIntegrityAttestationInput doesn't have a ChainSlot.").build();
    }
    if (!request.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().hasRoot()) {
      return RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(
               "The ChainSlot in this RequestIntegrityAttestationInput doesn't have a root.").build();
    }
    if (!request.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().hasBlock()) {
      return RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(
               "The ChainSlot in this RequestIntegrityAttestationInput doesn't have a block reference in it.").build();
    }
    final String isAcceptable = validPolicy(request.getPolicy());
    if (isAcceptable != null) {
      return RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(isAcceptable).build();
    }

    // By the time we get here, we have to return a commitment: either an old one, or a new one.
    
    // we're indexing strictly by root and slot.
    // that means different parents or whatever conflict.
    final ChainSlot indexableChainSlot = ChainSlot.newBuilder().
      setRoot(request.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().getRoot()).
      setSlot(request.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().getSlot()).
      build();

    // If there is a known response (or another thread is makign one), return that (possibly waiting for it).
    // Otherwise, put in a ConcurrentHolder which indicates that this thread is making a response, and others should wait.
    final ConcurrentHolder<RequestIntegrityAttestationResponse> newHolder =
      new ConcurrentHolder<RequestIntegrityAttestationResponse>(); // empty, for now
    final ConcurrentHolder<RequestIntegrityAttestationResponse> cachedResponse =
      getCommitments().putIfAbsent(indexableChainSlot, newHolder); // putIfAbsent returns null if it's previously unfilled.
    if (cachedResponse != null) {
      return cachedResponse.get();
    }

    // The only way we get here is if we have no commitment, and no other thread is working on making one.
    // Therefore we have to make a commitment here, and put it into newHolder, and return it.
    // Making a new block may be slow and involve IO.
    // This simplistic implementation does not.

    // make, receive (and broadcast) our new block
    final RequestIntegrityAttestationResponse response = newResponse(request.getPolicy());

    newHolder.put(response);
    return response;
  }

  /**
   * Grpc calls this whenever a RequestIntegrityAttestation rpc comes in over the wire.
   * It calls requestIntegrityAttestation(final RequestIntegrityAttestationInput request), which returns a
   *  RequestIntegrityAttestationResponse, which it gives to responseObserver.
   * @param request the request from the client sent over the wire
   * @param responseObserver used for sending a RequestIntegrityAttestationResponse back to the client over the wire
   */
  @Override
  public void requestIntegrityAttestation(final RequestIntegrityAttestationInput request,
                                          final StreamObserver<RequestIntegrityAttestationResponse> responseObserver) {
    responseObserver.onNext(requestIntegrityAttestation(request));
    responseObserver.onCompleted();
  }
}
