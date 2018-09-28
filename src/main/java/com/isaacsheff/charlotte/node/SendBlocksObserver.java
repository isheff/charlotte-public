package com.isaacsheff.charlotte.node;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.proto.SendBlocksInput;
import com.isaacsheff.charlotte.proto.SendBlocksResponse;

import io.grpc.stub.StreamObserver;

/**
 * One of these is created whenever a CharlotteNodeService gets a SendBlocks RPC.
 * It handles the stream of incoming blocks, and streams outgoing responses.
 * By default, it calls onSendBlocksInput on its CharlotteNodeService for each new block.
 * @author Isaac Sheff
 */
public class SendBlocksObserver implements StreamObserver<SendBlocksInput> {
  /**
   * Use logger for logging events on a CharlotteNodeService.
   */
  private static final Logger logger = Logger.getLogger(SendBlocksObserver.class.getName());

  /**
   * The CharlotteNodeService for which this observer exists.
   */
  protected final CharlotteNodeService charlotteNodeService;

  /**
   * The StreamObserver through which we stream responses over the wire.
   */
  protected final StreamObserver<SendBlocksResponse> responseObserver;

  /**
   * Constructor.
   * @param service the associated CharlotteNodeService. The service that is receiving the RPC this serves.
   * @param responseObserver the stream via which we send responses over the wire.
   */
  public SendBlocksObserver(CharlotteNodeService service, StreamObserver<SendBlocksResponse> responseObserver) {
    this.charlotteNodeService = service;
    this.responseObserver = responseObserver;
  }

  /**
   * Get the CharlotteNodeService associated with this Observer.
   * @return the CharlotteNodeService associated with this Observer.
   */
  public CharlotteNodeService getCharlotteNodeService() {
    return charlotteNodeService;
  }

  /**
   * Get the StreamObserver through which we send responses over the wire.
   * @return the StreamObserver through which we send responses over the wire.
   */
  public StreamObserver<SendBlocksResponse> getResponseObserver() {
    return responseObserver;
  }

  /**
   * What do we do each time a block arrives over the wire?
   * Call getCharlotteNodeService().onSendBlocksInput.
   * @param input the new SendBlocksInput that has just arrived on the wire.
   */
  public void onNext(SendBlocksInput input) {
    for (SendBlocksResponse response : (getCharlotteNodeService().onSendBlocksInput(input))) {
      getResponseObserver().onNext(response);
    }
  }

  /**
   * What do we do when the RPC is over (input stream closes).
   * We close the output stream.
   */
  public void onCompleted() {
    getResponseObserver().onCompleted();
  }

  /**
   * Called when there is an error in the stream.
   * We log it as a warning.
   * @param t the error arising from the stream.
   */
  public void onError(Throwable t) {
    getCharlotteNodeService().sendBlocksCancelled(t);
  }
}
