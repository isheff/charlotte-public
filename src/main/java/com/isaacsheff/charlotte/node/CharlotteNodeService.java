package com.isaacsheff.charlotte.node;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.collections.BlockingConcurrentHashMap;
import com.isaacsheff.charlotte.collections.BlockingMap;

import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.CharlotteNodeGrpc;
import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.SendBlocksInput;
import com.isaacsheff.charlotte.proto.SendBlocksResponse;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.Contact;

import io.grpc.stub.StreamObserver;

/**
 * A gRPC service for the Charlotte API.
 * gRPC separates "service" from "server."
 * One Server can run multiple Serivices.
 * This is a Service implementing the charlotte gRPC API.
 * It can be extended for more interesting implementations.
 * @author Isaac Sheff
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
   * @param reference a reference to the desired block
   * @return the block corresponding to the hash in this reference. Warning: WILL WAIT until such a block arrives
   */
  public Block getBlock(Reference reference) {
    return getBlock(reference.getHash());
  }

  /**
   * @return The configuration of this service, parsed from a yaml config file, and some x509 key files.
   */
  public Config getConfig() {
    return config;
  }


  /**
   * Send this block to the server with this CryptoId.
   * Shorthand for getConfig().getContact( - ).getCharlotteNodeClient().sendBlock( - ).
   * @param cryptoid identifies the server we want to send to 
   * @param block the block we want to send
   * @return true if we sent the block successfully, false otherwise
   * @throws NullPointerException if no contact was found with this identity
   */
  public boolean sendBlock(CryptoId cryptoid, Block block) {
    return getConfig().getContact(cryptoid).getCharlotteNodeClient().sendBlock(block);
  }

  /**
   * Send this block to the server with this CryptoId.
   * Shorthand for getConfig().getContact( - ).getCharlotteNodeClient().sendBlock( - ).
   * @param cryptoid identifies the server we want to send to 
   * @param block the block we want to send
   * @return true if we sent the block successfully, false otherwise
   * @throws NullPointerException if no contact was found with this identity
   */
  public boolean sendBlock(CryptoId cryptoid, SendBlocksInput block) {
    return getConfig().getContact(cryptoid).getCharlotteNodeClient().sendBlock(block);
  }

  /**
   * Send this block to the server with this Name (in the config file).
   * Shorthand for getConfig().getContact( - ).getCharlotteNodeClient().sendBlock( - ).
   * @param name identifies the server we want to send to 
   * @param block the block we want to send
   * @return true if we sent the block successfully, false otherwise
   * @throws NullPointerException if no contact was found with this identity
   */
  public boolean sendBlock(String name, Block block) {
    return getConfig().getContact(name).getCharlotteNodeClient().sendBlock(block);
  }

  /**
   * Send this block to the server with this Name (in the config file).
   * Shorthand for getConfig().getContact( - ).getCharlotteNodeClient().sendBlock( - ).
   * @param name identifies the server we want to send to 
   * @param block the block we want to send
   * @return true if we sent the block successfully, false otherwise
   * @throws NullPointerException if no contact was found with this identity
   */
  public boolean sendBlock(String name, SendBlocksInput block) {
    return getConfig().getContact(name).getCharlotteNodeClient().sendBlock(block);
  }

  /**
   * Send this block to the server with this URL and port.
   * Shorthand for getConfig().getContact( - ).getCharlotteNodeClient().sendBlock( - ).
   * @param url identifies the server we want to send to 
   * @param port identifies the port on that server
   * @param block the block we want to send
   * @return true if we sent the block successfully, false otherwise
   * @throws NullPointerException if no contact was found with this identity
   */
  public boolean sendBlock(String url, int port, Block block) {
    return getConfig().getContact(url, port).getCharlotteNodeClient().sendBlock(block);
  }

  /**
   * Send this block to the server with this URL and port.
   * Shorthand for getConfig().getContact( - ).getCharlotteNodeClient().sendBlock( - ).
   * @param url identifies the server we want to send to 
   * @param port identifies the port on that server
   * @param block the block we want to send
   * @return true if we sent the block successfully, false otherwise
   * @throws NullPointerException if no contact was found with this identity
   */
  public boolean sendBlock(String url, int port, SendBlocksInput block) {
    return getConfig().getContact(url, port).getCharlotteNodeClient().sendBlock(block);
  }

  /**
   * Send this block to all known contacts.
   * Since each contact's sendBlock function is nonblocking, this will be done in parallel.
   * @param block the block to send
   */
  public void broadcastBlock(Block block) {
    for (Contact contact : getConfig().getContacts().values()) {
      contact.getCharlotteNodeClient().sendBlock(block);
    }
  }

  /**
   * Send this block to all known contacts.
   * Since each contact's sendBlock function is nonblocking, this will be done in parallel.
   * @param block the block to send
   */
  public void broadcastBlock(SendBlocksInput block) {
    for (Contact contact : getConfig().getContacts().values()) {
      contact.getCharlotteNodeClient().sendBlock(block);
    }
  }

  /**
   * Stores a block in the services blockMap, and returns whether it was already known to this service.
   * @param block the block to be stored
   * @return whether or not the block was already known to this CharlotteNodeService
   */
  public boolean storeNewBlock(Block block) {
    return (getBlockMap().putIfAbsent(HashUtil.sha3Hash(block), block) == null);
  }

  /**
   * Called after a new block has been received, and set to be broadcast to all other nodes.
   * Override this to make this Node do useful things.
   * @param block the newly received block
   * @return any SendBlockResponses (including error messages) to be sent back over the wire to the block's sender.
   */
  public Iterable<SendBlocksResponse> afterBroadcastNewBlock(Block block) {
    return emptySet();
  }

  /**
   * Unless sendBlocks or onSendBlocksInput(SendBlocksInput -) have been overridden with a handler that does
   *  otherwise, this will be called for every block which arrives via any stream.
   * If the block is not yet seen, broadcasts the block to all contacts and calls afterBroadcastNewBlock().
   * Otherwise, returns an empty list of response messages.
   * Logs (INFO) whenever a block is received, whether it was new or repeat.
   * <p>
   * This can also be used to SEND a block. 
   * This would represent broadcasting the block, and then receiving the block yourself, even before you've marshaled it.
   * </p>
   * @param block the newly arrived blcok
   * @return any SendBlocksResponse s you want to send back over the wire
   */
  public Iterable<SendBlocksResponse> onSendBlocksInput(Block block) {
    if (storeNewBlock(block)) {
      logger.info("New Block received: " + block);
      broadcastBlock(block);
      return afterBroadcastNewBlock(block);
    } 
    logger.info("Repeat Block received: " + block);
    return emptySet();
  }

  /**
   * Unless sendBlocks has been overridden with a handler that does
   *  otherwise, this will be called for every block which arrives
   *  via any stream.
   * Logs a warning and sends back an error message if there is no block in the input.
   * If the block is not yet seen, broadcasts the block to all contacts and calls afterBroadcastNewBlock().
   * Otherwise, returns an empty list of response messages.
   * Logs (INFO) whenever a block is received, whether it was new or repeat.
   * @param input the newly arrived block
   * @return any SendBlocksResponse s you want to send back over the wire
   */
  public Iterable<SendBlocksResponse> onSendBlocksInput(SendBlocksInput input) {
    if (!input.hasBlock()) {
      logger.log(Level.WARNING, "No Block in this SendBlocksInput");
      return singleton(SendBlocksResponse.newBuilder().
               setErrorMessage("No Block in this SendBlocksInput").build());
    }
    return onSendBlocksInput(input.getBlock());
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
