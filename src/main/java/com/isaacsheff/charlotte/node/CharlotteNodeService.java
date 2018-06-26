package com.isaacsheff.charlotte.node;

import java.util.Collections;
import java.util.LinkedList;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.collections.BlockingConcurrentHashMap;
import com.isaacsheff.charlotte.collections.BlockingMap;

import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.Challenge;
import com.isaacsheff.charlotte.proto.CharlotteNodeGrpc;
import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.proto.ResponseToChallenge;
import com.isaacsheff.charlotte.proto.SendBlocksInput;
import com.isaacsheff.charlotte.proto.SendBlocksResponse;
import com.isaacsheff.charlotte.proto.SendAddressesInput;
import com.isaacsheff.charlotte.proto.SendAddressesResponse;
import com.isaacsheff.charlotte.proto.SignedAddress;

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
   * The map of all known addresses.
   * We use a blocking map, so that we can request addresses which haven't yet arrived, and then wait.
   */
  private final BlockingMap<CryptoId, SignedAddress> addressMap;

  /**
   * Create a new service with the given map of blocks, and the given map of addresses.
   * Neither input is checked for correctness.
   * @param blockMap a map of known hashes and blocks
   * @param addressMap a map of known cryptoIds to ip addresses
   */
  public CharlotteNodeService(BlockingMap<Hash, Block> blockMap, BlockingMap<CryptoId, SignedAddress> addressMap) {
    this.blockMap = blockMap;
    this.addressMap = addressMap;
  }

  /**
   * Create a new service with an empty map of blocks and an empty map of addresses.
   */
  public CharlotteNodeService() {
    this(new BlockingConcurrentHashMap<Hash, Block>(), new BlockingConcurrentHashMap<CryptoId, SignedAddress>());
  }

  /**
   * @return the map of blocks maintained by this service
   */
  public BlockingMap<Hash, Block> getBlockMap() {
    return blockMap;
  }

  /**
   * @return the map of cryptoIds to IP addresses maintained by this service
   */
  public BlockingMap<CryptoId, SignedAddress> getAddressMap() {
    return addressMap;
  }


  /**
   * TODO: do this right!.
   * Unless sendAddresses has been overridden with a handler that does
   *  otherwise, this will be called for every address which arrives
   *  via any stream.
   * Right now, this dumps the input and returns an empty list.
   * @param input the newly arrived address
   * @return any SendAddressesResponse s you want to send back over the wire
   */
  public Iterable<SendAddressesResponse> onSendAddressesInput(SendAddressesInput input) {
    return (new LinkedList<SendAddressesResponse>());
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
   * TODO: store crypto private key somewhere and use it here.
   * Proves our identity over the network.
   * <pre>
   * Using the hash algorithm provided,
   * hash( bytestring"Response to Challenge with Hash: " concat hash(challenge.str))
   * then sign that and return the signature.
   * used to guarangee that an open channel (possibly TLS) corresponds to a crypto ID
   * When using crypto IDs to do your TLS, this would not be necessary.
   * </pre>
   * We calculate this using a utility function in ChallengeResponseCalculator.
   * @param challenge the incomming challenge
   * @param responseObserver used to send back the response
   */
  public void challengeResponse(Challenge request, StreamObserver<ResponseToChallenge> responseObserver) {
    responseObserver.onNext(ChallengeResponseCalculator.challengeResponse(request));
    responseObserver.onCompleted();
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
  
  /**
   * Spawns a new SendAddressesObserver whenever the server receives a sendAddresses RPC.
   * Override this to use a different kind of observer (and thereby change sendAddresses behaviour).
   * @param responseObserver used to stream back responses to the RPC caller over the wire.
   * @return the SendAddressesObserver which will receive all the SignedAddresses streamed in this RPC call.
   */
  public StreamObserver<SendAddressesInput> sendAddresses(StreamObserver<SendAddressesResponse> responseObserver) {
    return(new SendAddressesObserver(this, responseObserver));
  }
}
