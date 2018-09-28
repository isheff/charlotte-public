package com.isaacsheff.charlotte.experiments;


import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.isaacsheff.charlotte.node.CharlotteNodeClient;
import com.isaacsheff.charlotte.proto.SendBlocksInput;


/**
 * Send each element queued to the StreamObserver.
 * This is Runnable, so it can be run in a seperate Thread, if the StreamObserver's onNext function might be slow.
 * @author Isaac Sheff
 */
public class SendToObserverLogging implements Runnable {
  /** Use logger for logging events involving SendToObserver. */
  private static final Logger logger = Logger.getLogger(SendToObserverLogging.class.getName());

  private final CharlotteNodeClient client;

  private final String loggingString;

  /**
   * Create a Runnable which will send each element queued to the StreamObserver.
   * It can be run in a seperate Thread, if the StreamObserver's onNext function might be slow.
   * @param queue The queue from which we pull elements to give to the StreamObserver.
   * @param observer The StreamObserver which will receive all the things queued.
   */
  public SendToObserverLogging(final CharlotteNodeClient client) {
    this.client = client;
    loggingString=",\n \"originUrl\":\"" + client.getContact().getParentConfig().getUrl() + "\"" +
                  ",\n \"originPort\":\"" + client.getContact().getParentConfig().getPort() + "\"" +
                  ",\n \"destinationUrl\":\"" + client.getContact().getUrl() + "\"" +
                  ",\n \"destinationPort\":\"" + client.getContact().getPort() + "\"";
  }

  private void sendToGrpc(final SendBlocksInput element) {
    try {
      client.getSendBlocksInputObserver().onNext(element);
      logger.info("{ \"SentBlock\":"+JsonFormat.printer().print(sha3Hash(element.getBlock()))+
                  loggingString +
                  ",\n \"size\":" + element.getSerializedSize() + " }");
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
    client.createChannel();
    if ((!client.getSendBlocksResponseObserver().hasFailed()) && (null != client.getMostRecentSent())) {
      sendToGrpc(client.getMostRecentSent());
    }
    while (!client.getSendBlocksResponseObserver().hasFailed()) {
      sendToGrpc(client.pullFromQueue());
    }
  }
}
