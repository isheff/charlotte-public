package com.isaacsheff.charlotte.fern;

import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
import static com.isaacsheff.charlotte.node.SignatureUtil.signBytes;

import com.isaacsheff.charlotte.collections.ConcurrentHolder;
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * A Fern server that runs agreement.
 * That is to say: this Fern server will, when asked, commit to a block in a slot on a chain, and never contradict itself.
 * If you ask it for commitments to the same slot with different blocks, it will keep referring you to the same attestation,
 *  where it commits to one block.
 *
 * <p>
 * Future extensions may wish to override validPolicy, as it designates
 *  (in a vacuum, so to speak), what policies this Fern server
 *   considers acceptable.
 * Alternatively, requestIntegrityAttestation contains almost all the
 *  functionality (it calls validPolicy), so you could override that
 *  to completely change what the server does.
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
   * Checks to see if all is well with this request, then checks if a conflicting request has already been answered,
   *  and if not, makes a new Integrity Attestation.
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

    // simple block, just a copy of the fill-in-the-blank, with the signature filled in.
    // there may be a more efficient way to build this.
    final Block newBlock = Block.newBuilder().setIntegrityAttestation(
        IntegrityAttestation.newBuilder(request.getPolicy().getFillInTheBlank()).
          setSignedChainSlot(SignedChainSlot.newBuilder(request.getPolicy().getFillInTheBlank().getSignedChainSlot()).
                               setSignature(signBytes(
                                   getNode().getConfig().getKeyPair(),
                                   request.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot())
        ))).build();

    // simple reference, no availability attestations
    final RequestIntegrityAttestationResponse newResponse = RequestIntegrityAttestationResponse.newBuilder().
      setReference(Reference.newBuilder().setHash(sha3Hash(newBlock))).build();

    // receive (and broadcast) our new block
    getNode().onSendBlocksInput(newBlock);
    newHolder.put(newResponse);
    return newResponse;
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
