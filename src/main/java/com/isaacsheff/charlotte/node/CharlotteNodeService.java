package com.isaacsheff.charlotte.node;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.LinkedList;
import java.util.ServiceConfigurationError;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.collections.BlockingConcurrentHashMap;
import com.isaacsheff.charlotte.collections.BlockingMap;

import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.CharlotteNodeGrpc;
import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.proto.SendBlocksInput;
import com.isaacsheff.charlotte.proto.SendBlocksResponse;

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
   * This line is required to use bouncycastle encryption libraries.
   */
  static {Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());}

  /**
   * Use logger for logging events on a CharlotteNodeService.
   */
  private static final Logger logger = Logger.getLogger(CharlotteNodeService.class.getName());

  /**
   * The public/private key pair for this service.
   */
  private final KeyPair  keyPair;

  /**
   * The map of all known blocks.
   * We use a blocking map, so that we can request blocks which haven't yet arrived, and then wait for them to arrive.
   */
  private final BlockingMap<Hash, Block> blockMap;


  /**
   * Create a new service with the given map of blocks, and the given map of addresses.
   * No input is checked for correctness.
   * @param blockMap a map of known hashes and blocks
   * @param keyPair the public/private key pair for this service
   */
  public CharlotteNodeService(BlockingMap<Hash, Block> blockMap,
                              KeyPair keyPair) {
    this.blockMap = blockMap;
    this.keyPair = keyPair;
  }

  /**
   * Create a new service with an empty map of blocks and an empty map of addresses.
   * @param keyPair the public/private key pair for this service
   */
  public CharlotteNodeService(KeyPair keyPair) {
    this(new BlockingConcurrentHashMap<Hash, Block>(),
         keyPair);
  }

  /**
   * Create a new service with an empty map of blocks and an empty map of addresses, and generate crypto keys for it.
   */
  public CharlotteNodeService() {
    this(new BlockingConcurrentHashMap<Hash, Block>(),
         generateDefaultKeyPair());
  }

  /**
   * Make a default key pair.
   * This will be an eliptic curve key with BouncyCastle as the provider.
   * The curve is P-256.
   * @return the key pair
   */
  private static KeyPair generateDefaultKeyPair() {
    try {
      KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC", "BC");
      keyGen.initialize(new ECGenParameterSpec("P-256"));
      return keyGen.generateKeyPair();
    } catch(GeneralSecurityException e) {
      // actually throws NoSuchProviderException, NoSuchAlgorithmException, or InvalidAlgorithmParameterException
      logger.log(Level.SEVERE, "Key generation exception popped up when it really should not have: ", e);
      throw (new ServiceConfigurationError("Key generation exception popped up when it really should not have: "+e));
    }
  }

  /**
   * @return the map of blocks maintained by this service
   */
  public BlockingMap<Hash, Block> getBlockMap() {
    return blockMap;
  }


  /**
   * @return the KeyPair associated with this service
   */
  public KeyPair getKeyPair() {
    return keyPair;
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
