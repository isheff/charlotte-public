package com.isaacsheff.charlotte.experiments;

import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
import static com.isaacsheff.charlotte.wilbur.WilburService.getWilburNode;
import static java.lang.Integer.parseInt;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.yaml.Config;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AgreementNWilbur extends CharlotteNodeService {
  /** for logging events in this class **/
  private static final Logger logger = Logger.getLogger(AgreementNWilbur.class.getName());

  /**
   * Constructor: make a new nodeservice.
   * @param configfile the filename of the config file
   */
  public AgreementNWilbur(String configfile) {
    super(configfile);
  }

  /**
   * Constructor: make a new nodeservice.
   * @param config the Config
   */
  public AgreementNWilbur(Config config) {
    super(config);
  }

  /**
   * Only broadcasts my own attestations.
   * @param block the block to be broadcast
   */
  @Override
  public void broadcastBlock(final Block block) {
    if ( // For reasons unknown ( https://github.com/isheff/charlotte-java/issues/5 ),
         // we get sendBlocks failures unless the Wilburs flood the root block.
        block.getStr().equals("block content 0")
        || ( block.hasAvailabilityAttestation()
          && block.getAvailabilityAttestation().hasSignedStoreForever()
          && block.getAvailabilityAttestation().getSignedStoreForever().hasSignature()
          && block.getAvailabilityAttestation().getSignedStoreForever().getSignature().hasCryptoId()
          && getConfig().getCryptoId().equals(
              block.getAvailabilityAttestation().getSignedStoreForever().getSignature().getCryptoId()))
        ) {
      super.broadcastBlock(block);
    }
  }

  /**
   * Stores a block in the services blockMap, and returns whether it was already known to this service.
   * Logs (INFO) whenever a block is received, whether it was new or repeat.
   * This will be a JSON, with either "NewBlockHash" or "RepeatBlockHash"
   * unlike a regular CharlotteNode, this DOES NOT LOG THE WHOLE BLOCK
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

  /**
   * Run as main.
   */
  public static void main(String[] args) throws InterruptedException {
    if (args.length < 1) {
      System.out.println("Correct Usage: WilburService configFileName.yaml");
      return;
    }
    (new Thread(getWilburNode(new AgreementNWilbur(args[0])))).start();
    logger.info("Wilbur service started on new thread");
    if (args.length < 2) {
      TimeUnit.SECONDS.sleep(Integer.MAX_VALUE);
    } else {
      TimeUnit.SECONDS.sleep(parseInt(args[1]));
    }
  }
}
