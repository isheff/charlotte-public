package com.isaacsheff.charlotte.node;

import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.Hash;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A gRPC service for the Charlotte API.
 * gRPC separates "service" from "server."
 * One Server can run multiple Services.
 * This is a Service implementing the charlotte gRPC API.
 * It can be extended for more interesting implementations.
 *
 * In this extension, only block Hashes, and not whole blocks, are
 *  logged when a new block is stored. 
 * @author Isaac Sheff
 */
public class LogHashService extends CharlotteNodeService {

  /** used for logging events in this class **/
  private static final Logger logger = Logger.getLogger(LogHashService.class.getName());

  /**
   * Create a new service with an empty map of blocks and an empty map of addresses, parse configuration.
   * @param filename the file name for the configuration file
   */
  public LogHashService(final String filename) {
    super(filename);
  }
  
  /**
   * Do not log whole blocks.
   * Logs (INFO) whenever a block is received, whether it was new or repeat.
   * This will be a JSON, with fields "block" and either "NewBlockHash" or "RepeatBlockHash"
   * @param block the block to be stored
   * @return whether or not the block was already known to this CharlotteNodeService
   */
  @Override
  public boolean storeNewBlock(final Block block) {
    final Hash hash = sha3Hash(block);
    if (getBlockMap().putIfAbsent(hash, block) == null) {
      try {
        logger.info("{ \"NewBlockHash\":"+JsonFormat.printer().print(hash)+"}");
      } catch (InvalidProtocolBufferException e) {
        logger.log(Level.SEVERE, "Invalid protocol buffer parsed as Block", e);
      }
      return true;
    }
    try {
      logger.info("{ \"RepeatBlockHash\":"+JsonFormat.printer().print(hash)+"}");
    } catch (InvalidProtocolBufferException e) {
      logger.log(Level.SEVERE, "Invalid protocol buffer parsed as Block", e);
    }
    return false;
  }
}
