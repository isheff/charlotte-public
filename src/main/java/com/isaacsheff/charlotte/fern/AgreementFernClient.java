package com.isaacsheff.charlotte.fern;

import static com.isaacsheff.charlotte.node.SignatureUtil.checkSignature;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.FernGrpc;
import com.isaacsheff.charlotte.proto.FernGrpc.FernBlockingStub;
import com.isaacsheff.charlotte.proto.FernGrpc.FernStub;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationResponse;
import com.isaacsheff.charlotte.yaml.Contact;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

/**
 * This client needs a local CharlotteNodeService to get blocks for it.
 * @author Isaac Sheff
 */
public class AgreementFernClient {
  /**
   * Use logger for logging events on this class.
   */
  private static final Logger logger = Logger.getLogger(AgreementFernClient.class.getName());

  /**
   * The channel through which we communicate to the server.
   */
  private final ManagedChannel channel;

  /**
   * The stub which sends messages to the Fern service within the server (this is a gRPC thing).
   */
  private final FernStub asyncStub;

  /**
   * The stub which sends messages to the Fern service within the server (this is a gRPC thing).
   * This version is blocking (waits for a response).
   */
  private final FernBlockingStub blockingStub;

  /**
   * Represents the Fern server.
   * Stores the public key, url, etc.
   */
  private final Contact contact;

  /**
   * The local CharlotteNodeService we expect to receive blocks.
   */
  private final CharlotteNodeService localService;


  /**
   * Make a new AgreementFernClient for a specific AgreementFern server.
   * This will attempt to open a channel of communication.
   * @param localService a CharlotteNodeService which can be used to receive blocks
   * @param contact the Contact representing the server.
   */
  public AgreementFernClient(final CharlotteNodeService localService, final Contact contact) {
    this.contact = contact;
    this.localService = localService;
    channel = getContact().getManagedChannel();
    asyncStub = FernGrpc.newStub(getChannel());
    blockingStub = FernGrpc.newBlockingStub(getChannel());
  }

  /** @return The channel through which we communicate to the server. **/
  public ManagedChannel getChannel() {return channel;}

  /** @return The asynchronous stub which sends messages to the Fern service within the server (this is a gRPC thing). **/
  public FernStub getAsyncStub() {return asyncStub;}
  
  /** @return The synchrnous stub which sends messages to the Fern service within the server (this is a gRPC thing). **/
  public FernBlockingStub getBlockingStub() {return blockingStub;}
  
  /** @return Represents the Fern server. Stores the public key, url, etc. **/
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
   * Check whether this Block contains a valid IntegrityAttestation.
   * Checks for a valid signature in a properly formatted SignedChainSlot.
   * @param attestation the block we're hoping contains the IntegrityAttestation
   * @return the Block input if it's valid, null otherwise.
   */
  public static Block checkAgreementIntegrityAttestation(final Block attestation) {
    if (!attestation.hasIntegrityAttestation()) {
      logger.log(Level.WARNING, "Response from Fern Server referenced block which is not an Integrity Attestation:\n" +
                                attestation);
      return null;
    }
    if (!attestation.getIntegrityAttestation().hasSignedChainSlot()) {
      logger.log(Level.WARNING, "Response from Fern Server referenced block which is not a SignedChainSlot:\n" +
                                attestation);
      return null;
    }
    if (!attestation.getIntegrityAttestation().getSignedChainSlot().hasChainSlot()) {
      logger.log(Level.WARNING, "Response from Fern Server referenced block which has no ChainSlot:\n" +
                                attestation);
      return null;
    }
    if (!attestation.getIntegrityAttestation().getSignedChainSlot().hasSignature()) {
      logger.log(Level.WARNING, "Response from Fern Server referenced block which has no Signature:\n" +
                                attestation);
      return null;
    }
    if (!checkSignature(attestation.getIntegrityAttestation().getSignedChainSlot().getChainSlot(),
                        attestation.getIntegrityAttestation().getSignedChainSlot().getSignature())) {
      logger.log(Level.WARNING, "Response from Fern Server referenced block with an incorrect signature:\n" +
                                attestation);
      return null;
    }
    return attestation;
  }

  /**
   * Check whether this Block contains a valid IntegrityAttestation.
   * Checks for a valid signature in a properly formatted SignedChainSlot.
   * @param attestation the block we're hoping contains the IntegrityAttestation
   * @return the Block input if it's valid, null otherwise.
   */
  public Block checkIntegrityAttestation(final Block attestation) {
    return checkAgreementIntegrityAttestation(attestation);
  }


  /**
   * Check whether this Response references a valid IntegrityAttestation.
   * Fetches the block from our local node.
   * This may wait until the block referenced is received.
   * Checks for a valid signature in a properly formatted SignedChainSlot.
   * @param response references the block we're hoping contains the IntegrityAttestation
   * @return the Integrity Attestation Block input if it's valid, null otherwise.
   */
  public Block checkIntegrityAttestation(final RequestIntegrityAttestationResponse response) {
    if (!response.getErrorMessage().equals("")) {
      logger.log(Level.WARNING, "Response from Fern Server has an Error Message: " + response.getErrorMessage());
    }
    if (!response.hasReference()) {
      logger.log(Level.WARNING, "Response from Fern Server has no reference:\n" + response);
      return null;
    }
    if (!response.getReference().hasHash()) {
      logger.log(Level.WARNING, "Response from Fern Server has no no Hash in its reference:\n" + response);
      return null;
    }
    return checkIntegrityAttestation(getLocalService().getBlock(response.getReference()));
  }

  /**
   * Check whether this Response references a valid IntegrityAttestation matching the request.
   * Fetches the block from our local node.
   * This may wait until the block referenced is received.
   * Checks for a valid signature in a properly formatted SignedChainSlot.
   * Checks that the Attestation is for the same ChainSlot, and the same CryptoId, as the request.
   * @param request the request we sent to the Fern server
   * @param response references the block we're hoping contains the IntegrityAttestation
   * @return the Integrity Attestation Block input if it's valid, null otherwise.
   */
  public Block checkIntegrityAttestation(final RequestIntegrityAttestationInput request,
                                         final RequestIntegrityAttestationResponse response) {
     final Block attestation = checkIntegrityAttestation(response);
     // by this point, the attestation itself is verified, so we know it has a chain slot and a valid signature and such.
     if (attestation == null) {
       return null;
     }
     if (!attestation.getIntegrityAttestation().getSignedChainSlot().getChainSlot().equals(
          request.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot())) {
       logger.log(Level.WARNING, "Response from Fern Server referenced block different chain slot. "+
                                 "Perhaps the Fern server has already commited to a conflicting block?"+
                                 "\nATTESTATION:\n"+attestation+
                                 "\nREQUEST:\n"+request);
       return null;
     }
     if (!attestation.getIntegrityAttestation().getSignedChainSlot().getSignature().getCryptoId().equals(
          request.getPolicy().getFillInTheBlank().getSignedChainSlot().getSignature().getCryptoId())) {
       logger.log(Level.WARNING, "Response from Fern Server referenced block different CryptoId than requested:" +
                                 "\nATTESTATION:\n"+attestation+
                                 "\nREQUEST:\n"+request);
       return null;
     }
     return attestation;
  }

  /**
   * Send a RequestIntegrityAttestation to the Fern Server, and await a response.
   * This corresponds directly to the gRPC call.
   * @param request the request sent to the server over the wire
   * @return the RequestIntegrityAttestationResponse returned over the wire.
   */
  public RequestIntegrityAttestationResponse requestIntegrityAttestation(final RequestIntegrityAttestationInput request) {
    return getBlockingStub().requestIntegrityAttestation(request);
  }

  /**
   * Send a RequestIntegrityAttestation to the Fern Server, and handle the response asynchronously.
   * This corresponds directly to the gRPC call.
   * @param request the request sent to the server over the wire
   * @param responseObserver the onNext method of this will be called
   *                          with the RequestIntegrityAttestationResponse returned over the wire.
   */
  public void requestIntegrityAttestation(final RequestIntegrityAttestationInput request,
                                          final StreamObserver<RequestIntegrityAttestationResponse> responseObserver) {
    getAsyncStub().requestIntegrityAttestation(request, responseObserver);
  }

  /**
   * Send a RequestIntegrityAttestation to the Fern Server, await a response, await the attestation, and check it.
   * @param request the request sent to the server over the wire
   * @return The Block with the IntegrityAttestation if all went well, null otherwise (it will log stuff).
   */
  public Block getIntegrityAttestation(final RequestIntegrityAttestationInput request) {
    return checkIntegrityAttestation(request, requestIntegrityAttestation(request));
  }
}
