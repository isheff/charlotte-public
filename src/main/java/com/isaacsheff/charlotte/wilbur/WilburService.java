package com.isaacsheff.charlotte.wilbur;

import static java.lang.Integer.parseInt;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.AvailabilityAttestation;
import com.isaacsheff.charlotte.proto.AvailabilityAttestation.SignedStoreForever;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.WilburGrpc;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestAvailabilityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestAvailabilityAttestationResponse;

import io.grpc.stub.StreamObserver;

/**
 * A gRPC service for the Wilbur API.
 * gRPC separates "service" from "server."
 * One Server can run multiple Services.
 * This is a Service implementing the wilbur gRPC API.
 * It can be extended for more interesting implementations.
 * Run as a main class with an arg specifying a config file name to run a Wilbur server.
 * @author Isaac Sheff
 */
public class WilburService extends WilburGrpc.WilburImplBase {
  /**
   * Use logger for logging events on a WilburService.
   */
  private static final Logger logger = Logger.getLogger(WilburService.class.getName());

  /** 
   * The CharlotteNodeService running on the same server as this Wilbur service (there must be one).
   */
  private final CharlotteNodeService node;

  /**
   * Run as a main class with an arg specifying a config file name to run a Wilbur server.
   * creates and runs a new CharlotteNode which runs a Wilbur Service and a CharlotteNodeService, in a new thread.
   * @param args command line args. args[0] should be the name of the config file. args[1] (optional) is auto-shutdown time in secodns
   */
  public static void main(String[] args) throws InterruptedException {
    if (args.length < 1) {
      System.out.println("Correct Usage: WilburService configFileName.yaml");
      return;
    }
    (new Thread(getWilburNode(args[0]))).start();
    logger.info("Wilbur service started on new thread");
    if (args.length < 2) {
      TimeUnit.SECONDS.sleep(Integer.MAX_VALUE);
    } else {
      TimeUnit.SECONDS.sleep(parseInt(args[1]));
    }
  }

  /**
   * @param node a CharlotteNodeService with which we'll build a WilburService
   * @return a new CharlotteNode which runs a Wilbur Service and a CharlotteNodeService
   */
  public static CharlotteNode getWilburNode(final CharlotteNodeService node) {
    return new CharlotteNode(node, new WilburService(node));
  }

  /**
   * @param configFilename the name of the configuration file for this CharlotteNode
   * @return a new CharlotteNode which runs a Wilbur Service and a CharlotteNodeService
   */
  public static CharlotteNode getWilburNode(final Path configFilename) {
    return getWilburNode(new CharlotteNodeService(configFilename));
  }

  /**
   * @param configFilename the name of the configuration file for this CharlotteNode
   * @return a new CharlotteNode which runs a Wilbur Service and a CharlotteNodeService
   */
  public static CharlotteNode getWilburNode(final String configFilename) {
    return getWilburNode(new CharlotteNodeService(configFilename));
  }


  /**
   * @param node The CharlotteNodeService running on the same server as this Wilbur service (there must be one).
   */
  public WilburService(final CharlotteNodeService node) {
    this.node = node;
  }

  /**
   * @return The CharlotteNodeService running on the same server as this Wilbur service (there must be one).
   */
  public CharlotteNodeService getNode() { return node; }

  /**
   * Called when an rpc comes in over the wire requesting an availability attestation.
   * Since the default CharlotteNode stores all blocks anyway, this just waits
   *  to be sure all listed blocks have arrived.
   * Since it can wait, this call can take a while.
   * I'm not sure if gRPC will give this call its own thread.
   * If not, we might want to launch the contents of this in a new thread.
   * It would be safe to do so.
   * @param request details the desired attestation block
   * @param responseObserver this observer will get a single response, which
   *                          will have either an error string or a reference to
   *                          a newly-minted availability attestation.
   */
  public void requestAvailabilityAttestation(final RequestAvailabilityAttestationInput request,
                final StreamObserver<RequestAvailabilityAttestationResponse> responseObserver) {
    // First, check to see all the necessary fields are present.
    // Technically, we can do without this, if no one ever submits a botched request.
    if (!request.hasPolicy()) {
      responseObserver.onNext(RequestAvailabilityAttestationResponse.newBuilder().setErrorMessage(
            "There is no policy in this RequestAvailabilityAttestationInput.").build());
      responseObserver.onCompleted();
      return;
    }
    if (!request.getPolicy().hasFillInTheBlank()) {
      responseObserver.onNext(RequestAvailabilityAttestationResponse.newBuilder().setErrorMessage(
            "The policy in this RequestAvailabilityAttestationInput isn't FillInTheBlank.").build());
      responseObserver.onCompleted();
      return;
    }
    if (!request.getPolicy().getFillInTheBlank().hasSignedStoreForever()) {
      responseObserver.onNext(RequestAvailabilityAttestationResponse.newBuilder().setErrorMessage(
            "The policy in this RequestAvailabilityAttestationInput isn't SignedStoreForever.").build());
      responseObserver.onCompleted();
      return;
    }
    if (!request.getPolicy().getFillInTheBlank().getSignedStoreForever().hasStoreForever()) {
      responseObserver.onNext(RequestAvailabilityAttestationResponse.newBuilder().setErrorMessage(
            "The SignedStoreForever has no StoreForever field.").build());
      responseObserver.onCompleted();
      return;
    }
    if (request.getPolicy().getFillInTheBlank().getSignedStoreForever().getStoreForever().getBlockCount() < 1) {
      responseObserver.onNext(RequestAvailabilityAttestationResponse.newBuilder().setErrorMessage(
            "The policy in this RequestAvailabilityAttestationInput lists no blocks.").build());
      responseObserver.onCompleted();
      return;
    }
    for (Reference reference :
         request.getPolicy().getFillInTheBlank().getSignedStoreForever().getStoreForever().getBlockList()) {
      if (!reference.hasHash()) {
        responseObserver.onNext(RequestAvailabilityAttestationResponse.newBuilder().setErrorMessage(
              "A reference has no hash: " + reference).build());
        responseObserver.onCompleted();
        return;
      }
    }

    // Wait until we've received each of the specified blocks.
    for (Reference reference :
         request.getPolicy().getFillInTheBlank().getSignedStoreForever().getStoreForever().getBlockList()) {
      getNode().getBlock(reference.getHash());
    }

    // Now we create an attestation, "receive" that ourselves (which will involve broadcasting it), and
    //  return a reference
    final Block availabilityAttestation = Block.newBuilder().setAvailabilityAttestation(
      AvailabilityAttestation.newBuilder().setSignedStoreForever(
        SignedStoreForever.newBuilder().setStoreForever(
            request.getPolicy().getFillInTheBlank().getSignedStoreForever().getStoreForever())
          .setSignature(SignatureUtil.signBytes(getNode().getConfig().getKeyPair(),
                        request.getPolicy().getFillInTheBlank().getSignedStoreForever().getStoreForever())))
      ).build();
    // receive (and broadcast) that attestation
    getNode().onSendBlocksInput(availabilityAttestation);
    // send a reference to the attestation back over the wire.
    responseObserver.onNext(RequestAvailabilityAttestationResponse.newBuilder().setReference(
      Reference.newBuilder().setHash(HashUtil.sha3Hash(availabilityAttestation))).build());
    responseObserver.onCompleted();
    logger.log(Level.INFO, "Issued signed attestation for: \n" +
      request.getPolicy().getFillInTheBlank().getSignedStoreForever().getStoreForever());
  }
}
