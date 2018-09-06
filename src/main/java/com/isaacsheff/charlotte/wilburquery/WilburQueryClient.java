package com.isaacsheff.charlotte.wilburquery;


import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.proto.WilburQueryGrpc;
import com.isaacsheff.charlotte.proto.WilburQueryGrpc.WilburQueryBlockingStub;
import com.isaacsheff.charlotte.proto.WilburQueryGrpc.WilburQueryStub;
import com.isaacsheff.charlotte.proto.WilburQueryInput;
import com.isaacsheff.charlotte.proto.WilburQueryResponse;
import com.isaacsheff.charlotte.yaml.Contact;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

/**
 * A client designed to request, fetch, and check availability attestations from a Wilbur node.
 * This client needs a local CharlotteNodeService to get blocks for it.
 * @author Isaac Sheff
 */
public class WilburQueryClient {
  /**
   * Use logger for logging events on this class.
   */
  private static final Logger logger = Logger.getLogger(WilburQueryClient.class.getName());

  /**
   * The channel through which we communicate to the server.
   */
  private final ManagedChannel channel;

  /**
   * The stub which sends messages to the Wilbur service within the server (this is a gRPC thing).
   */
  private final WilburQueryStub asyncStub;

  /**
   * The stub which sends messages to the Wilbur service within the server (this is a gRPC thing).
   * This version is blocking (waits for a response).
   */
  private final WilburQueryBlockingStub blockingStub;

  /**
   * Represents the Wilbur server.
   * Stores the public key, url, etc.
   */
  private final Contact contact;



  /**
   * Make a new WilburClient for a specific Wilbur server.
   * This will attempt to open a channel of communication.
   * @param localService a CharlotteNodeService which can be used to receive blocks
   * @param contact the Contact representing the server.
   */
  public WilburQueryClient(final Contact contact) {
    this.contact = contact;
    channel = getContact().getManagedChannel();
    asyncStub = WilburQueryGrpc.newStub(getChannel());
    blockingStub = WilburQueryGrpc.newBlockingStub(getChannel());
  }

  /** @return The channel through which we communicate to the server. **/
  public ManagedChannel getChannel() {return channel;}

  /** @return The asynchronous stub which sends messages to the Wilbur service within the server (this is a gRPC thing). **/
  public WilburQueryStub getAsyncStub() {return asyncStub;}
  
  /** @return The synchrnous stub which sends messages to the Wilbur service within the server (this is a gRPC thing). **/
  public WilburQueryBlockingStub getBlockingStub() {return blockingStub;}
  
  /** @return Represents the Wilbur server. Stores the public key, url, etc. **/
  public Contact getContact() {return contact;}

  /**
   * Shut down this client. 
   * Tries to close out everything, but I think some sending / pending threads may get zombied.
   * @throws InterruptedException  if the thread was interrupted while trying to shut down the channel to the server.
   */
  public void shutdown() throws InterruptedException {
    getChannel().shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  public void wilburQuery(final WilburQueryInput query,
                          final StreamObserver<WilburQueryResponse> responseObserver) {
    getAsyncStub().wilburQuery(query, responseObserver);
  }

  public WilburQueryResponse wilburQuery(final WilburQueryInput query) {
    return getBlockingStub().wilburQuery(query);
  }
}
