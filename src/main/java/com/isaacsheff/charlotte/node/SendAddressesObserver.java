package com.isaacsheff.charlotte.node;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.proto.SendAddressesInput;
import com.isaacsheff.charlotte.proto.SendAddressesResponse;

import io.grpc.stub.StreamObserver;

/**
 * One of these is created whenever a CharlotteNodeService gets a SendAddresses RPC.
 * It handles the stream of incoming addresses, and streams outgoing responses.
 * By default, it calls onSendAddressesInput on its CharlotteNodeService for each new address.
 * @author Isaac Sheff
 */
public class SendAddressesObserver implements StreamObserver<SendAddressesInput> {
  /**
   * Use logger for logging events on a CharlotteNodeService.
   */
  private static final Logger logger = Logger.getLogger(SendAddressesObserver.class.getName());

  /**
   * The CharlotteNodeService for which this observer exists.
   */
  protected final CharlotteNodeService charlotteNodeService;

  /**
   * The StreamObserver through which we stream responses over the wire.
   */
  protected final StreamObserver<SendAddressesResponse> responseObserver;

  /**
   * Constructor.
   * @param service the associated CharlotteNodeService. The service that is receiving the RPC this serves.
   * @param responseObserver the stream via which we send responses over the wire.
   */
  public SendAddressesObserver(CharlotteNodeService service, StreamObserver<SendAddressesResponse> responseObserver) {
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
  public StreamObserver<SendAddressesResponse> getResponseObserver() {
    return responseObserver;
  }

  /**
   * What do we do each time an address arrives over the wire?
   * Call getCharlotteNodeService().onSendAddressesInput.
   * @param input the new SendAddressesInput that has just arrived on the wire.
   */
  public void onNext(SendAddressesInput input) {
    for (SendAddressesResponse response : (getCharlotteNodeService().onSendAddressesInput(input))) {
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
    logger.log(Level.WARNING, "sendAddresses cancelled", t);
  }
}
