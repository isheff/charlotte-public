package com.isaacsheff.charlotte.wilburquery;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.MessageOrBuilder;

import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.WilburQueryGrpc;
import com.isaacsheff.charlotte.proto.WilburQueryInput;
import com.isaacsheff.charlotte.proto.WilburQueryResponse;
import com.isaacsheff.charlotte.wilbur.WilburService;

import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;



/**
 * A gRPC service for the WilburQuery API.
 * gRPC separates "service" from "server."
 * One Server can run multiple Serivices.
 * This is a Service implementing the wilbur gRPC API.
 * It can be extended for more interesting implementations.
 * Run as a main class with an arg specifying a config file name to run a Wilbur server.
 * @author Isaac Sheff
 */
public class WilburQueryService extends WilburQueryGrpc.WilburQueryImplBase {
  /**
   * Use logger for logging events on a WilburQueryService.
   */
  private static final Logger logger = Logger.getLogger(WilburQueryService.class.getName());

  /** 
   * The CharlotteNodeService running on the same server as this WilburQuery service (there must be one).
   */
  private final CharlotteNodeService node;

  /**
   * Run as a main class with an arg specifying a config file name to run a Wilbur server.
   * creates and runs a new CharlotteNode which runs a Wilbur Service and a CharlotteNodeService, in a new thread.
   * @param args command line args. args[0] should be the name of the config file
   */
  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("Correct Usage: WilburQueryService configFileName.yaml");
      return;
    }
    (new Thread(getWilburQueryNode(args[0]))).start();
    logger.info("Wilbur service started on new thread");
  }

  /**
   * @param node a CharlotteNodeService with which we'll build a WilburService
   * @return a new CharlotteNode which runs a WilburQueryService, a Wilbur Service, and a CharlotteNodeService
   */
  public static CharlotteNode getWilburQueryNode(final CharlotteNodeService node) {
    return new CharlotteNode(node,
                             ServerBuilder.forPort(node.getConfig().getPort()).
                               addService(new WilburService(node)).
                               addService(new WilburQueryService(node)),
                             node.getConfig().getPort());
  }

  /**
   * @param configFilename the name of the configuration file for this CharlotteNode
   * @return a new CharlotteNode which runs a Wilbur Service and a CharlotteNodeService
   */
  public static CharlotteNode getWilburQueryNode(final Path configFilename) {
    return getWilburQueryNode(new CharlotteNodeService(configFilename));
  }

  /**
   * @param configFilename the name of the configuration file for this CharlotteNode
   * @return a new CharlotteNode which runs a Wilbur Service and a CharlotteNodeService
   */
  public static CharlotteNode getWilburQueryNode(final String configFilename) {
    return getWilburQueryNode(new CharlotteNodeService(configFilename));
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

  private static boolean fillInTheBlankMatchFieldNonRepeated(final FieldDescriptor fieldDescriptor,
                                                            final Object query,
                                                            final Object potential) {
    if (fieldDescriptor.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
      if (query instanceof MessageOrBuilder) { // should be unnecessary, as field should be Message
        if (potential instanceof MessageOrBuilder) { // should be unnecessary, as field should be Message
          try { // should be unnecessary, as field should be Message
            // I HATE these hard typecasts, but we did literally just check
            return fillInTheBlankMatch((MessageOrBuilder) query, (MessageOrBuilder) potential);
          } catch (ClassCastException e) {
            logger.log(Level.SEVERE, "A non-repeated message field could not cast to MessageOrBuilder", e);
          }
        } else {
          logger.log(Level.SEVERE,
            "a non-repeated message field was not an instance of MessageOrBuilder:\n" + potential);
        }
      } else {
          logger.log(Level.SEVERE,
            "a non-repeated message field was not an instance of MessageOrBuilder:\n" + query);
      }
      return false; // when non-repeated message field isn't an instance of MessageOrBuilder, which should be never.
    } // otherwise, if the field is non-message, we just check equality:
    return query.equals(potential);
  }
  

  private static boolean fillInTheBlankMatchFieldRepeated(final FieldDescriptor fieldDescriptor,
                                                          final Iterator<Object> query,
                                                          final Iterator<Object> potential) {
    Object queryElement;
    while (query.hasNext()) {
      if (!potential.hasNext()) {
        return false;
      }
      queryElement = query.next();
      while (! fillInTheBlankMatchFieldNonRepeated(fieldDescriptor, queryElement, potential.next())) {
        if (!potential.hasNext()) {
          return false;
        }
      }
    }
    return true;
  }
  
  @SuppressWarnings("unchecked") // Repeated fields should be java.util.Lists, so we have to cast them.
  private static boolean fillInTheBlankMatchField(final FieldDescriptor fieldDescriptor,
                                                  final Object query,
                                                  final Object potential) {
    if (fieldDescriptor.isRepeated()) { // means the field should be a java.util.List
      if (query instanceof Iterable) { // should be unnecessary, as Repeated fields should be lists
        if (potential instanceof Iterable) { // should be unnecessary, as Repeated fields should be lists
          try { // should be unnecessary, as Repeated fields should be lists
            return fillInTheBlankMatchFieldRepeated(fieldDescriptor,
                                                    // I HATE these hard typecasts, but we did literally just check
                                                    ((Iterable<Object>) query).iterator(),
                                                    ((Iterable<Object>) potential).iterator());
          } catch (ClassCastException e) {
            logger.log(Level.SEVERE, "A repeated field could not cast to Iterable", e);
          }
        } else {
          logger.log(Level.SEVERE, "a repeated field was not an instance of Iterable:\n" + potential);
        }
      } else {
        logger.log(Level.SEVERE, "a repeated field was not an instance of Iterable:\n" + query);
      }
      return false; // we only reach here if a repeated field could not cast to Iterable, which should never happen.
    }
    return fillInTheBlankMatchFieldNonRepeated(fieldDescriptor, query, potential);
  }



  /**
   * Must match order of repeated items (but can have other items in between)
   */
  public static boolean fillInTheBlankMatch(MessageOrBuilder query, MessageOrBuilder potential) {
    for (Map.Entry<FieldDescriptor, Object> entry : query.getAllFields().entrySet()) {
      if (! entry.getKey().isRepeated()) {
        if (! potential.hasField(entry.getKey())) {
          return false;
        }
      }
      if (! fillInTheBlankMatchField(entry.getKey(), entry.getValue(), potential.getField(entry.getKey()))) {
        return false;
      }
    }
    return true;
  }



  /**
   * Called for each rpc over the wire requesting a block.
   * This queries only stuff the server already knows; it will never wait.
   * I'm not sure if gRPC will give this call its own thread.
   * If not, we might want to launch the contents of this in a new thread.
   * It would be safe to do so.
   * @param request details the desired block
   * @param responseObserver this observer will get a single response, which
   *                          will have either an error string or a Block.
   */
  public WilburQueryResponse wilburQuery(final WilburQueryInput request) {
    final WilburQueryResponse.Builder builder = WilburQueryResponse.newBuilder();

    // For requests by reference
    if (request.hasReference()) {
      if (!request.getReference().hasHash()) {
        return(builder.setErrorMessage("Request has a Reference with no Hash.").build());
      }
      final Block block = getNode().getBlockMap().get(request.getReference().getHash());
      if (block == null) {
        return(builder.setErrorMessage("I do not have a block with that Hash.").build());
      }
      return(builder.addBlock(block).build());
    }

    if (! request.hasFillInTheBlank()) {
      return(builder.setErrorMessage("Request has neither Reference nor FillInTheBlank.").build());
    }

    // For requests by fillInTheBlank
    // Parallelize this loop?
    // This is a naive linear search. There is probably a way to do better.
    for (Block block : getNode().getBlockMap().values()) {
      if (fillInTheBlankMatch(request.getFillInTheBlank(), block)) {
        builder.addBlock(block);
      }
    }
    return builder.build();
  }

  /**
   * Called when an rpc comes in over the wire requesting a block.
   * I'm not sure if gRPC will give this call its own thread.
   * If not, we might want to launch the contents of this in a new thread.
   * It would be safe to do so.
   * @param request details the desired block
   * @param responseObserver this observer will get a single response, which
   *                          will have either an error string or a Block.
   */
  public void wilburQuery(final WilburQueryInput request,
                          final StreamObserver<WilburQueryResponse> responseObserver) {
    responseObserver.onNext(wilburQuery(request));
    responseObserver.onCompleted();
  }
}
