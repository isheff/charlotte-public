package com.isaacsheff.charlotte.wilburquery;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.AvailabilityAttestation;
import com.isaacsheff.charlotte.proto.AvailabilityAttestation.SignedStoreForever;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.WilburQueryGrpc;
import com.isaacsheff.charlotte.proto.WilburQueryInput;
import com.isaacsheff.charlotte.proto.WilburQueryResponse;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestAvailabilityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestAvailabilityAttestationResponse;

import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

/**
 * A gRPC service for the Wilbur API.
 * gRPC separates "service" from "server."
 * One Server can run multiple Serivices.
 * This is a Service implementing the wilbur gRPC API.
 * It can be extended for more interesting implementations.
 * Run as a main class with an arg specifying a config file name to run a Wilbur server.
 * @author Isaac Sheff
 */
public class WilburQueryService extends WilburQueryGrpc.WilburQueryImplBase {
  /**
   * Use logger for logging events on a WilburService.
   */
  private static final Logger logger = Logger.getLogger(WilburQueryService.class.getName());

  /** 
   * The CharlotteNodeService running on the same server as this Wilbur service (there must be one).
   */
  private final CharlotteNodeService node;

  /**
   * Run as a main class with an arg specifying a config file name to run a Wilbur server.
   * creates and runs a new CharlotteNode which runs a Wilbur Service and a CharlotteNodeService, in a new thread.
   * @param args command line args. args[0] should be the name of the config file
   */
  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("Correct Usage: WilburService configFileName.yaml");
      return;
    }
    (new Thread(getWilburNode(args[0]))).start();
    logger.info("Wilbur service started on new thread");
  }

  /**
   * @param node a CharlotteNodeService with which we'll build a WilburService
   * @return a new CharlotteNode which runs a Wilbur Service and a CharlotteNodeService
   */
  public static CharlotteNode getWilburNode(final CharlotteNodeService node) {
    return new CharlotteNode(node,
                             ServerBuilder.forPort(node.getConfig().getPort()).addService(new WilburQueryService(node)),
                             node.getConfig().getPort());
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
  public WilburQueryService(final CharlotteNodeService node) {
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
  public void wilburQuery(final WilburQueryInput request,
                final StreamObserver<WilburQueryResponse> responseObserver) {
    return;
  }
}
