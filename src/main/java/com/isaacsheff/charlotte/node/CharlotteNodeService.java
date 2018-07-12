package com.isaacsheff.charlotte.node;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.collections.BlockingConcurrentHashMap;
import com.isaacsheff.charlotte.collections.BlockingMap;

import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.CharlotteNodeGrpc;
import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.proto.SendBlocksInput;
import com.isaacsheff.charlotte.proto.SendBlocksResponse;
import com.isaacsheff.charlotte.yaml.Config;

import io.grpc.stub.StreamObserver;

/**
 * A gRPC service for the Charlotte API.
 * gRPC separates "service" from "server."
 * One Server can run multiple Serivices.
 * This is a Service implementing the charlotte gRPC API.
 * It can be extended for more interesting implementations.
 */
public class CharlotteNodeService extends CharlotteNodeGrpc.CharlotteNodeImplBase {
  /**
   * Use logger for logging events on a CharlotteNodeService.
   */
  private static final Logger logger = Logger.getLogger(CharlotteNodeService.class.getName());

  /**
   * The map of all known blocks.
   * We use a blocking map, so that we can request blocks which haven't yet arrived, and then wait for them to arrive.
   */
  private final BlockingMap<Hash, Block> blockMap;

  /**
   * The configuration of this service, parsed from a yaml config file, and some x509 key files.
   */
  private final Config config;


  /**
   * Create a new service with the given map of blocks, and the given map of addresses.
   * No input is checked for correctness.
   * @param blockMap a map of known hashes and blocks
   * @param config the Configuration settings for this Service
   */
  public CharlotteNodeService(BlockingMap<Hash, Block> blockMap,
                              Config config) {
    this.blockMap = blockMap;
    this.config = config;
  }

  /**
   * Create a new service with an empty map of blocks and an empty map of addresses.
   * @param config the Configuration settings for this Service
   */
  public CharlotteNodeService(Config config) {
    this(new BlockingConcurrentHashMap<Hash, Block>(), config);
  }

  /**
   * Create a new service with an empty map of blocks and an empty map of addresses, parse configuration.
   * @param path the file path for the configuration file
   */
  public CharlotteNodeService(Path path) {
    this(new BlockingConcurrentHashMap<Hash, Block>(), new Config(path));
  }

  /**
   * Create a new service with an empty map of blocks and an empty map of addresses, parse configuration.
   * @param filename the file name for the configuration file
   */
  public CharlotteNodeService(String filename) {
    this(new BlockingConcurrentHashMap<Hash, Block>(), new Config(filename));
  }


  /**
   * @return the map of blocks maintained by this service
   */
  public BlockingMap<Hash, Block> getBlockMap() {
    return blockMap;
  }

  /**
   * @param hash the hash of the desired block
   * @return the block corresponding to this hash. Warning: WILL WAIT until such a block arrives
   */
  public Block getBlock(Hash hash) {
    return getBlockMap().blockingGet(hash);
  }


  /**
   * @return The configuration of this service, parsed from a yaml config file, and some x509 key files.
   */
  public Config getConfig() {
    return config;
  }



  /**
   * TODO: do this right!.
   * Unless sendBlocks has been overridden with a handler that does
   *  otherwise, this will be called for every block which arrives
   *  via any stream.
   * Right now, this dumps the input and returns an empty list.
   * @param input the newly arrived address
   * @return any SendBlocksResponse s you want to send back over the wire
   */
  public Iterable<SendBlocksResponse> onSendBlocksInput(SendBlocksInput input) {
    return (new LinkedList<SendBlocksResponse>());
  }


  /**
   * Spawns a new SendBlocksObserver whenever the server receives a sendBlocks RPC.
   * Override this to use a different kind of observer (and thereby change sendBlocks behaviour).
   * @param responseObserver used to stream back responses to the RPC caller over the wire.
   * @return the SendBlocksObserver which will receive all the blocks streamed in this RPC call.
   */
  public StreamObserver<SendBlocksInput> sendBlocks(StreamObserver<SendBlocksResponse> responseObserver) {
    return(new SendBlocksObserver(this, responseObserver));
  }
  
}
