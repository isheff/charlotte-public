package com.isaacsheff.charlotte.wilbur;

import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
import static com.isaacsheff.charlotte.node.SignatureUtil.checkSignature;
import static java.util.Collections.singleton;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.AvailabilityAttestation;
import com.isaacsheff.charlotte.proto.AvailabilityAttestation.SignedStoreForever;
import com.isaacsheff.charlotte.proto.AvailabilityAttestation.StoreForever;
import com.isaacsheff.charlotte.proto.AvailabilityPolicy;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestAvailabilityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestAvailabilityAttestationResponse;
import com.isaacsheff.charlotte.proto.Signature;
import com.isaacsheff.charlotte.proto.WilburGrpc;
import com.isaacsheff.charlotte.proto.WilburGrpc.WilburBlockingStub;
import com.isaacsheff.charlotte.proto.WilburGrpc.WilburStub;
import com.isaacsheff.charlotte.yaml.Contact;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

/**
 * A client designed to request, fetch, and check availability attestations from a Wilbur node.
 * This client needs a local CharlotteNodeService to get blocks for it.
 * @author Isaac Sheff
 */
public class WilburClient {
  /** Use logger for logging events on this class. */
  private static final Logger logger = Logger.getLogger(WilburClient.class.getName());

  /** The channel through which we communicate to the server. */
  private final ManagedChannel channel;

  /** The stub which sends messages to the Wilbur service within the server (this is a gRPC thing). */
  private final WilburStub asyncStub;

  /**
   * The stub which sends messages to the Wilbur service within the server (this is a gRPC thing).
   * This version is blocking (waits for a response).
   */
  private final WilburBlockingStub blockingStub;

  /**
   * Represents the Wilbur server.
   * Stores the public key, url, etc.
   */
  private final Contact contact;

  /** The local CharlotteNodeService we expect to receive blocks. */
  private final CharlotteNodeService localService;


  /**
   * Make a new WilburClient for a specific Wilbur server.
   * This will attempt to open a channel of communication.
   * @param localService a CharlotteNodeService which can be used to receive blocks
   * @param contact the Contact representing the server.
   */
  public WilburClient(final CharlotteNodeService localService, final Contact contact) {
    this.contact = contact;
    this.localService = localService;
    channel = getContact().getManagedChannel();
    asyncStub = WilburGrpc.newStub(getChannel());
    blockingStub = WilburGrpc.newBlockingStub(getChannel());
  }

  /** @return The channel through which we communicate to the server. **/
  public ManagedChannel getChannel() {return channel;}

  /** @return The asynchronous stub which sends messages to the Wilbur service within the server (this is a gRPC thing). **/
  public WilburStub getAsyncStub() {return asyncStub;}
  
  /** @return The synchrnous stub which sends messages to the Wilbur service within the server (this is a gRPC thing). **/
  public WilburBlockingStub getBlockingStub() {return blockingStub;}
  
  /** @return Represents the Wilbur server. Stores the public key, url, etc. **/
  public Contact getContact() {return contact;}

  /** @return The local CharlotteNodeService we expect to receive blocks. **/
  public CharlotteNodeService getLocalService() {return localService;}

  /**
   * Shut down this client. 
   * Tries to close out everything, but I think some sending / pending threads may get zombied.
   * @throws InterruptedException  if the thread was interrupted while trying to shut down the channel to the server.
   */
  public void shutdown() throws InterruptedException {
    getChannel().shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  /**
   * Wait to receive the block referenced in the
   *  RequestAvailabilityAttestationResponse, then check whether it's
   *  a valid availability attestation. 
   * This will log a Warning with the problem if the block referenced is invalid.
   * @param response the response from the Wilbur Server
   * @return the block featuring the valid availability attestation, or null if it's invalid.
   */
  public Block checkAvailabilityAttestation(final RequestAvailabilityAttestationResponse response) {
    if (!response.hasReference()) {
      logger.log(Level.WARNING, "got a RequestAvailabilityAttestationResponse with no reference in it: \n" +
                                response);
      return null;
    }
    if (!response.getReference().hasHash()) {
      logger.log(Level.WARNING, "got a RequestAvailabilityAttestationResponse with a reference with no hash: \n" +
                                response);
      return null;
    }
    final Block availabilityAttestationBlock = getLocalService().getBlock(response.getReference());
    if (!availabilityAttestationBlock.hasAvailabilityAttestation()) {
      logger.log(Level.WARNING, "the RequestAvailabilityAttestationResponse references a block that isn't "+
                                "an availabilityAttestation.\nRESPONSE: \n" + response +
                                " \nBLOCK: \n" + availabilityAttestationBlock );
      return null;
    }
    if (!availabilityAttestationBlock.getAvailabilityAttestation().hasSignedStoreForever()) {
      logger.log(Level.WARNING, "got a RequestAvailabilityAttestationResponse that isn't a SignedStoreForever: \n" +
                                response);
      return null;
    }
    if (!availabilityAttestationBlock.getAvailabilityAttestation().getSignedStoreForever().hasStoreForever()) {
      logger.log(Level.WARNING, "got a RequestAvailabilityAttestationResponse doesn't have a StoreForever: \n" +
                                response);
      return null;
    }
    if (!availabilityAttestationBlock.getAvailabilityAttestation().getSignedStoreForever().hasSignature()) {
      logger.log(Level.WARNING, "got a RequestAvailabilityAttestationResponse doesn't have a Signature: \n" +
                                response);
      return null;
    }
    if (!availabilityAttestationBlock.getAvailabilityAttestation().getSignedStoreForever().getSignature().
         hasCryptoId()) {
      logger.log(Level.WARNING, "got a RequestAvailabilityAttestationResponse doesn't have a CryptoId: \n" +
                                response);
      return null;
    }
    if (!availabilityAttestationBlock.getAvailabilityAttestation().getSignedStoreForever().getSignature().
         getCryptoId().equals(getContact().getCryptoId())) {
      logger.log(Level.WARNING, "got a RequestAvailabilityAttestationResponse has a different CryptoId: \n" +
                                "THIS CLIENT's CRYPTOID: \n" + getContact().getCryptoId() +
                                " \nATTESTATION: \n" + availabilityAttestationBlock);
      return null;
    }
    if (!checkSignature(
          availabilityAttestationBlock.getAvailabilityAttestation().getSignedStoreForever().getStoreForever(),
          availabilityAttestationBlock.getAvailabilityAttestation().getSignedStoreForever().getSignature())) {
      logger.log(Level.WARNING, "got a RequestAvailabilityAttestationResponse with an invalid Signature: \n" +
                                response);
      return null;
    }
    return availabilityAttestationBlock; 
  }

  /**
   * Check whether the availabiltiy atteststion referenced attests to the references given.
   * Logs a warning and returns null if it does not.
   * @param references references to blocks you want the availability attestation to cover.
   * @param response the response from the Wilbur Server
   * @return the availability attestation block if it's valid and covers all the references, null otherwise.
   */
  public Block checkAvailabilityAttestation(final Iterable<Reference> references,
                                            final RequestAvailabilityAttestationResponse response) {
    final Block availabilityAttestationBlock = checkAvailabilityAttestation(response);
    if (availabilityAttestationBlock == null) {
      return null;
    }
    // everything is good so long as the desired hash is actually in here.
    // Run through the hashes, and if it's in here, return.
    // Otherwise, log a warning and return null
    // TODO: maybe consider doing this search not the stupid n^2 way we're doing it here.
    for (Reference reference : references) {
      if (!hashInAvailabilityAttestationBlock(reference.getHash(), availabilityAttestationBlock)) {
        logger.log(Level.WARNING, "got a RequestAvailabilityAttestationResponse doesn't reference the desired hash:"+
                                  " \nHASH: \n" + reference.getHash() +
                                  " \nRESPONSE: \n" + response);
        return null;
      }
    }
    return availabilityAttestationBlock; 
  }

  /**
   * Check whether the availabiltiy atteststion referenced attests to the reference given.
   * Logs a warning and returns null if it does not.
   * @param reference reference to the block you want the availability attestation to cover.
   * @param response the response from the Wilbur Server
   * @return the availability attestation block if it's valid and covers the reference, null otherwise.
   */
  public Block checkAvailabilityAttestation(final Reference reference,
                 final RequestAvailabilityAttestationResponse response) {
    return checkAvailabilityAttestation(singleton(reference), response);
  }

  /**
   * Check whether the availabiltiy atteststion referenced attests to the reference given.
   * Logs a warning and returns null if it does not.
   * @param hash reference to the block you want the availability attestation to cover.
   * @param response the response from the Wilbur Server
   * @return the availability attestation block if it's valid and covers the reference, null otherwise.
   */
  public Block checkAvailabilityAttestation(final Hash hash,
                 final RequestAvailabilityAttestationResponse response) {
    return checkAvailabilityAttestation(Reference.newBuilder().setHash(hash).build(), response);
  }

  /**
   * Check whether the availabiltiy atteststion referenced attests to the block given.
   * Logs a warning and returns null if it does not.
   * @param block block you want the availability attestation to cover.
   * @param response the response from the Wilbur Server
   * @return the availability attestation block if it's valid and covers the block given, null otherwise.
   */
  public Block checkAvailabilityAttestation(final Block block,
                 final RequestAvailabilityAttestationResponse response) {
    return checkAvailabilityAttestation(sha3Hash(block), response);
  }


  /**
   * Test whether a hash is in the list in an availability atteststation block.
   * @param hash the hash you're searching for.
   * @param availabilityAttestationBlock the block in which you're searching.
   * @return whether a hash is in the list in the availability atteststation block
   */
  public static boolean hashInAvailabilityAttestationBlock(final Hash hash, final Block availabilityAttestationBlock) {
    for (Reference reference : availabilityAttestationBlock.getAvailabilityAttestation().getSignedStoreForever().
                               getStoreForever().getBlockList()) {
      if (reference.hasHash()) {
        if(reference.getHash().equals(hash)) {
          return true; 
        }
      }
    }
    return false;
  }

  /**
   * Fetch an availability attestation for the given references.
   * Blocking.
   * @param references references to the blocks you want attested.
   * @return the block featuring the availability attestation.
   */
  public Block getAvailabilityAttestation(final Iterable<Reference> references) {
    return checkAvailabilityAttestation(references, requestAvailabilityAttestation(references));
  }

  /**
   * Fetch an availability attestation for the given reference.
   * Blocking.
   * @param reference reference to the block you want attested.
   * @return the block featuring the availability attestation.
   */
  public Block getAvailabilityAttestation(final Reference reference) {
    return checkAvailabilityAttestation(reference, requestAvailabilityAttestation(reference));
  }

  /**
   * Fetch an availability attestation for the given reference.
   * Blocking.
   * @param hash reference to the block you want attested.
   * @return the block featuring the availability attestation.
   */
  public Block getAvailabilityAttestation(final Hash hash) {
    return checkAvailabilityAttestation(hash, requestAvailabilityAttestation(hash));
  }

  /**
   * Fetch an availability attestation for the given block.
   * Blocking.
   * @param block the block you want attested.
   * @return the block featuring the availability attestation.
   */
  public Block getAvailabilityAttestation(final Block block) {
    return checkAvailabilityAttestation(block, requestAvailabilityAttestation(block));
  }

  /**
   * Fetch an availability attestation for the given references.
   * Blocking.
   * @param hashes references to the blocks you want attested.
   * @return the block featuring the availability attestation.
   */
  public Block getAvailabilityAttestationHashes(final Iterable<Hash> hashes) {
    final HashSet<Reference> references = new HashSet<Reference>();
    for (Hash hash : hashes) {
      references.add(Reference.newBuilder().setHash(hash).build());
    }
    return checkAvailabilityAttestation(references, requestAvailabilityAttestation(references));
  }

  /**
   * Fetch an availability attestation for the given blocks.
   * Blocking.
   * @param blocks blocks you want attested.
   * @return the block featuring the availability attestation.
   */
  public Block getAvailabilityAttestationBlocks(final Iterable<Block> blocks) {
    // we could do a set of hashes, but this is more efficient
    final HashSet<Reference> references = new HashSet<Reference>();
    for (Block block : blocks) {
      references.add(Reference.newBuilder().setHash(sha3Hash(block)).build());
    }
    return checkAvailabilityAttestation(references, requestAvailabilityAttestation(references));
  }

  /**
   * Request an availability attestation from this contact.
   * @param request the availability attestation request
   * @param responseObserver an observer which waits for a response from the server.
   */
  public void requestAvailabilityAttestation(final RequestAvailabilityAttestationInput request,
                final StreamObserver<RequestAvailabilityAttestationResponse> responseObserver) {
    getAsyncStub().requestAvailabilityAttestation(request, responseObserver);
  }

  /**
   * Request an availability attestation from this contact.
   * @param request the availability policy for the desired attestation
   * @param responseObserver an observer which waits for a response from the server.
   */
  public void requestAvailabilityAttestation(final AvailabilityPolicy request,
                final StreamObserver<RequestAvailabilityAttestationResponse> responseObserver) {
    requestAvailabilityAttestation(
        RequestAvailabilityAttestationInput.newBuilder().setPolicy(request).build(),
        responseObserver);
  }

  /**
   * Request an availability attestation from this contact.
   * @param request the availability attestation with blanks to be filled in
   * @param responseObserver an observer which waits for a response from the server.
   */
  public void requestAvailabilityAttestation(final AvailabilityAttestation request,
                final StreamObserver<RequestAvailabilityAttestationResponse> responseObserver) {
    requestAvailabilityAttestation(
        AvailabilityPolicy.newBuilder().setFillInTheBlank(request).build(),
        responseObserver);
  }

  /**
   * Request an availability attestation from this contact.
   * @param request a collection of references for which we want a signedstoreforever attestation
   * @param responseObserver an observer which waits for a response from the server.
   */
  public void requestAvailabilityAttestation(final Iterable<Reference> request,
                final StreamObserver<RequestAvailabilityAttestationResponse> responseObserver) {
    requestAvailabilityAttestation(
        AvailabilityAttestation.newBuilder().setSignedStoreForever(SignedStoreForever.newBuilder().
          setStoreForever(StoreForever.newBuilder().addAllBlock(request)).
          setSignature(Signature.newBuilder().setCryptoId(getContact().getCryptoId()))
        ).build(),
        responseObserver);
  }

  /**
   * Request an availability attestation from this contact.
   * @param request a reference for which we want a signedstoreforever attestation
   * @param responseObserver an observer which waits for a response from the server.
   */
  public void requestAvailabilityAttestation(final Reference request,
                final StreamObserver<RequestAvailabilityAttestationResponse> responseObserver) {
    requestAvailabilityAttestation(singleton(request), responseObserver);
  }

  /**
   * Request an availability attestation from this contact.
   * @param request a hash of the block for which we want a signedstoreforever attestation
   * @param responseObserver an observer which waits for a response from the server.
   */
  public void requestAvailabilityAttestation(final Hash request,
                final StreamObserver<RequestAvailabilityAttestationResponse> responseObserver) {
    requestAvailabilityAttestation(Reference.newBuilder().setHash(request).build(), responseObserver);
  }

  /**
   * Request an availability attestation from this contact.
   * @param request a block for which we want a signedstoreforever attestation
   * @param responseObserver an observer which waits for a response from the server.
   */
  public void requestAvailabilityAttestation(final Block request,
                final StreamObserver<RequestAvailabilityAttestationResponse> responseObserver) {
    requestAvailabilityAttestation(sha3Hash(request), responseObserver);
  }

  /**
   * Request an availability attestation from this contact.
   * @param request the availability attestation request
   * @return a response from the server.
   */
  public RequestAvailabilityAttestationResponse requestAvailabilityAttestation(
           final RequestAvailabilityAttestationInput request) {
    return getBlockingStub().requestAvailabilityAttestation(request);
  }

  /**
   * Request an availability attestation from this contact.
   * @param request the availability policy for the desired attestation
   * @return a response from the server.
   */
  public RequestAvailabilityAttestationResponse requestAvailabilityAttestation(
           final AvailabilityPolicy request) {
    return requestAvailabilityAttestation(
             RequestAvailabilityAttestationInput.newBuilder().setPolicy(request).build());
  }

  /**
   * Request an availability attestation from this contact.
   * @param request the availability attestation with blanks to be filled in
   * @return a response from the server.
   */
  public RequestAvailabilityAttestationResponse requestAvailabilityAttestation(
           final AvailabilityAttestation request) {
    return requestAvailabilityAttestation(AvailabilityPolicy.newBuilder().setFillInTheBlank(request).build());
  }

  /**
   * Request an availability attestation from this contact.
   * @param request a collection of References for which to obtain a signed store forever attestation
   * @return a response from the server.
   */
  public RequestAvailabilityAttestationResponse requestAvailabilityAttestation(final Iterable<Reference> request) {
    return requestAvailabilityAttestation(
             AvailabilityAttestation.newBuilder().setSignedStoreForever(SignedStoreForever.newBuilder().
               setStoreForever(StoreForever.newBuilder().addAllBlock(request)).
               setSignature(Signature.newBuilder().setCryptoId(getContact().getCryptoId()))
             ).build());
  }

  /**
   * Request an availability attestation from this contact.
   * @param request a Reference for which to obtain a signed store forever attestation
   * @return a response from the server.
   */
  public RequestAvailabilityAttestationResponse requestAvailabilityAttestation(final Reference request) {
    return requestAvailabilityAttestation(singleton(request));
  }

  /**
   * Request an availability attestation from this contact.
   * @param request a Hash of a block for which to obtain a signed store forever attestation
   * @return a response from the server.
   */
  public RequestAvailabilityAttestationResponse requestAvailabilityAttestation(final Hash request) {
    return requestAvailabilityAttestation(Reference.newBuilder().setHash(request).build());
  }

  /**
   * Request an availability attestation from this contact.
   * @param request a block for which to obtain a signed store forever attestation
   * @return a response from the server.
   */
  public RequestAvailabilityAttestationResponse requestAvailabilityAttestation(final Block request) {
    return requestAvailabilityAttestation(sha3Hash(request));
  }
}
