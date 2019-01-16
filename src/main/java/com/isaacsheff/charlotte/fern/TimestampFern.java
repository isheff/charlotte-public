package com.isaacsheff.charlotte.fern;

import static com.google.protobuf.util.Timestamps.fromMillis;
import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
import static com.isaacsheff.charlotte.node.SignatureUtil.signBytes;
import static java.lang.Integer.parseInt;
import static java.lang.System.currentTimeMillis;

import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.TimestampNode;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.FernGrpc.FernImplBase;
import com.isaacsheff.charlotte.proto.IntegrityAttestation;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.SignedTimestampedReferences;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.TimestampedReferences;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationResponse;
import com.isaacsheff.charlotte.proto.SendBlocksResponse;
import com.isaacsheff.charlotte.yaml.Config;

import io.grpc.stub.StreamObserver;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A Fern server that runs timestamping.
 *
 * <p>
 * Can be run as a main class with: TimestampFern configFileName.yaml BlocksPerTimestamp
 * </p>
 *
 * <p>
 * BlocksPerTimestamp is an integer.
 * Everyn BlocksPerTimestamp blocks the node receives, it
 *  automatically requests a timestamp for those blocks from this Fern
 *  server.
 * </p>
 *
 * <p>
 * Beyond that, anyone can request timestamps for any collection of
 *  blocks.
 * The node will wait until it has all the blocks in the collection,
 *  and then issue a signed timestamp.
 * </p>
 *
 * @author Isaac Sheff
 */
public class TimestampFern extends FernImplBase {

  /** Use logger for logging events in this class. */
  private static final Logger logger = Logger.getLogger(TimestampFern.class.getName());

  /** The local CharlotteNodeService used to send and receive blocks */
  protected CharlotteNodeService node;

  /**
   * Run as a main class with an arg specifying a config file name to run a Fern Timestamp server.
   * creates and runs a new CharlotteNode which runs a Fern Service
   *  and a TimestampNode service (which is a CharlotteNode Service), in a new thread.
   * @param args command line args. args[0] should be the name of the config file, args[1] is BlocksPerTimestamp, args[2] (optional) is timeout until the server shuts down, in seconds
   */
  public static void main(String[] args) throws InterruptedException{
    if (args.length < 1) {
      System.out.println("Correct Usage: FernService configFileName.yaml");
      return;
    }
    final Thread thread = new Thread(getFernNode(args[0], parseInt(args[1])));
    thread.start();
    logger.info("Fern service started on new thread");
    thread.join();
    if (args.length < 3) {
      TimeUnit.SECONDS.sleep(Integer.MAX_VALUE);
    } else {
      TimeUnit.SECONDS.sleep(parseInt(args[2]));
    }

  }


  /**
   * @param node a TimestampFern with which we'll build a TimestampFern Service
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final TimestampFern fern) {
    return new CharlotteNode(fern.getNode(), fern);
  }

  /**
   * @param configFilename the name of the configuration file for this CharlotteNode
   * @param referencesPerTimestamp the number of references the node acquires to automatically request a timestamp
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final String configFilename, final int referencesPerTimestamp) {
    return getFernNode(new TimestampFern(configFilename, referencesPerTimestamp));
  }

  /**
   * @param config the name of the configuration file for this CharlotteNode
   * @param referencesPerTimestamp the number of references the node acquires to automatically request a timestamp
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final Config config, final int referencesPerTimestamp) {
    return getFernNode(new TimestampFern(config, referencesPerTimestamp));
  }

  /**
   * Generate a new Timestamp Fern service.
   * This will generate an underlying CharlotteNodeService, specifically a TimestampNode.
   * @param config the name of the configuration file for this CharlotteNode
   * @param referencesPerTimestamp the number of references the node acquires to automatically request a timestamp
   */
  public TimestampFern(final Config config, final int referencesPerTimestamp) {
    node = new TimestampNode(referencesPerTimestamp, this, config);
  }

  /**
   * Generate a new Timestamp Fern service.
   * This will generate an underlying CharlotteNodeService, specifically a TimestampNode.
   * @param configFilename the name of the configuration file for this CharlotteNode
   * @param referencesPerTimestamp the number of references the node acquires to automatically request a timestamp
   */
  public TimestampFern(final String configFileName, final int referencesPerTimestamp) {
    node = new TimestampNode(referencesPerTimestamp, this, configFileName);
  }

  /**
   * Generate a new Timestamp Fern service.
   * This will use the given underlying CharlotteNodeService
   * @param node the CharlotteNodeService on this server
   */
  public TimestampFern(final CharlotteNodeService node) {
    this.node = node;
  }

  /** The default constructor should only be used by extending classes. */
  protected TimestampFern() {}

  /** @return The local CharlotteNodeService used to send and receive blocks */
  public CharlotteNodeService getNode() {return node;}


  /**
   * Called whenever a requestIntegrityAttestation comes in over the wire.
   * This checks whether the incoming request is for a timestamp, and
   *  then creates a timestamp for those blocks, and returns a
   *  reference to that attestation.
   * @param request the request that came in over the wire
   * @return the RequestIntegrityAttestationResponse to be sent back over the wire.
   */
  public RequestIntegrityAttestationResponse requestIntegrityAttestation(final RequestIntegrityAttestationInput request) {
    final RequestIntegrityAttestationResponse.Builder builder = RequestIntegrityAttestationResponse.newBuilder(); 
    if (!request.hasPolicy()) {
      return builder.setErrorMessage("Integrity Attestation Request has no Policy").build();
    }
    if (!request.getPolicy().hasFillInTheBlank()) {
      return builder.setErrorMessage("Integrity Attestation Request has a Policy that isn't FillInTheBlank").build();
    }
    if (!request.getPolicy().getFillInTheBlank().hasSignedTimestampedReferences()) {
      return builder.setErrorMessage(
        "Integrity Attestation Request has a Policy with a FillInTheBlank that isn't SignedTimestampedReferences").build();
    }
    if (!request.getPolicy().getFillInTheBlank().getSignedTimestampedReferences().hasTimestampedReferences()) {
      return builder.setErrorMessage(
        "Integrity Attestation Request has a Policy with a SignedTimestampedReferences but no TimestampedReferences").build();
    }
    final TimestampedReferences.Builder referencesBuilder = TimestampedReferences.newBuilder();
    for (Reference reference :
         request.getPolicy().getFillInTheBlank().getSignedTimestampedReferences().getTimestampedReferences().getBlockList()) {
      if (!reference.hasHash()) {
        return builder.setErrorMessage("Reference with no Hash").build();
      }
      referencesBuilder.addBlock(Reference.newBuilder().setHash(reference.getHash()));
    }

    referencesBuilder.setTimestamp(fromMillis(currentTimeMillis())); // actually gets the current time.

    final TimestampedReferences references = referencesBuilder.build();
    final Block attestation = Block.newBuilder().setIntegrityAttestation(
          IntegrityAttestation.newBuilder().setSignedTimestampedReferences(
            SignedTimestampedReferences.newBuilder().setTimestampedReferences(references).setSignature(
              signBytes(getNode().getConfig().getKeyPair(), references)
            )
          )
        ).build();
    for (SendBlocksResponse response : getNode().onSendBlocksInput(attestation)) {
      if (!response.getErrorMessage().equals("")) {
        return builder.setErrorMessage("Problem with newly created attestation:\n"+response.getErrorMessage()).build();
      }
    }
    return builder.setReference(Reference.newBuilder().setHash(sha3Hash(attestation))).build();
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
