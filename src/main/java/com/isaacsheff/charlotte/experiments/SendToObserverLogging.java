package com.isaacsheff.charlotte.experiments;


import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.isaacsheff.charlotte.node.CharlotteNodeClient;
import com.isaacsheff.charlotte.node.SendBlocksResponseObserver;
import com.isaacsheff.charlotte.proto.SendBlocksInput;

import io.grpc.stub.StreamObserver;

/**
 * Send each element queued to the StreamObserver.
 * This is Runnable, so it can be run in a seperate Thread, if the StreamObserver's onNext function might be slow.
 * @author Isaac Sheff
 */
public class SendToObserverLogging implements Runnable {
  /** Use logger for logging events involving SendToObserver. */
  private static final Logger logger = Logger.getLogger(SendToObserverLogging.class.getName());

  private final CharlotteNodeClient client;

  /** The StreamObserver which will receive all the things queued. */
  private final StreamObserver<SendBlocksInput> observer;

  private final SendBlocksResponseObserver responseObserver;
  private final SendBlocksInput thingToSendFirst;

  /**
   * Create a Runnable which will send each element queued to the StreamObserver.
   * It can be run in a seperate Thread, if the StreamObserver's onNext function might be slow.
   * @param queue The queue from which we pull elements to give to the StreamObserver.
   * @param observer The StreamObserver which will receive all the things queued.
   */
  public SendToObserverLogging(final CharlotteNodeClient client, final SendBlocksInput thingToSendFirst, final StreamObserver<SendBlocksInput> observer, SendBlocksResponseObserver responseObserver) {
    this.client = client;
    this.observer = observer;
    this.responseObserver = responseObserver;
    this.thingToSendFirst = thingToSendFirst;
  }

  private void sendToGrpc(final SendBlocksInput element) {
    try {
      observer.onNext(element);
      logger.info("{ \"SentBlock\":"+JsonFormat.printer().print(sha3Hash(element.getBlock()))+
                  ", \"size\":" + element.getSerializedSize() + " }");
    } catch (InvalidProtocolBufferException e) {
      logger.log(Level.SEVERE, "Invalid protocol buffer parsed as Block", e);
    }
  }

  /**
   * Run (which a Thread will call) will loop.
   * Take from the queue (blocking operation).
   * Give whatever it got from the queue to the StreamObserver's onNext method.
   * This will then log a hash of the thing sent with the json key "SentBlock"
   */
  public void run() {
    if ((!responseObserver.hasFailed()) && (null != thingToSendFirst)) {
      sendToGrpc(thingToSendFirst);
    }
    while (!responseObserver.hasFailed()) {
      sendToGrpc(client.pullFromQueue());
    }
  }
}
