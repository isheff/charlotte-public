package com.isaacsheff.charlotte.node;

import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.isaacsheff.charlotte.proto.CharlotteNodeGrpc;
import com.isaacsheff.charlotte.proto.SendBlocksInput;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

/**
 * In a new thread, opens a sendBlocks RPC to a server, and pulls elements from a queue, sending them to the RPC.
 * This is Runnable, so it can be run in a seperate Thread, since the StreamObserver's onNext function might be slow.
 * This will launch and hold a StreamObserver for the response stream of the RPC.
 * @author Isaac Sheff
 */
public class SendToObserver implements Runnable {
  /** Use logger for logging events involving SendToObserver. */
  private static final Logger logger = Logger.getLogger(SendToObserver.class.getName());

  /** The queue from which we pull elements to give to the StreamObserver. */
  private final BlockingQueue<SendBlocksInput> queue;

  /** Represents the local handle for talking to a particular server */
  private final CharlotteNodeClient client;

  /** A string added to log messages for sent message logging statements */
  private final String loggingString;

  /** The most recently dequeued item, which makes it the next item to be sent. */
  private SendBlocksInput mostRecent;

  /** Has this RPC failed (had an error or something and died) ? */
  private boolean failed;

  /**
   * Produced by the RPC call.
   * We give each new message to this, so it can be sent on the wire.
   */
  private StreamObserver<SendBlocksInput> observer;

  /** The channel through which we communicate to the server. */
  private ManagedChannel channel;

  /**
   * Create a Runnable which will send each element queued to the StreamObserver.
   * It can be run in a seperate Thread, if the StreamObserver's onNext function might be slow.
   * @param queue The queue from which we pull elements to give to the StreamObserver.
   * @param observer The StreamObserver which will receive all the things queued.
   */
  public SendToObserver(final BlockingQueue<SendBlocksInput> queue,
                        final SendBlocksInput sendMeFirst,
                        final CharlotteNodeClient client) {
    this.queue = queue;
    this.client = client;
    mostRecent = sendMeFirst;
    failed = false;
    channel = null;
    observer = null;
    loggingString=",\n \"originUrl\":\"" + client.getContact().getParentConfig().getUrl() + "\"" +
                  ",\n \"originPort\":\"" + client.getContact().getParentConfig().getPort() + "\"" +
                  ",\n \"destinationUrl\":\"" + client.getContact().getUrl() + "\"" +
                  ",\n \"destinationPort\":\"" + client.getContact().getPort() + "\"";
  }

  /** @return The most recently dequeued item, which makes it the next item to be sent. */
  public SendBlocksInput getMostRecent() {return mostRecent;}

  /** @return Has this RPC failed (had an error or something and died) ? */
  public boolean hasFailed() {return failed;}

  /** @return The channel through which we communicate to the server. */
  public ManagedChannel getChannel() {return channel;}

  /** @return Represents the local handle for talking to a particular server */
  public CharlotteNodeClient getClient() {return client;}

  /**
   * Called when this RPC has encountered an error or something and died.
   * It is assumed that the StreamObserver contained herin has already completed.
   * This is idempotent: if hasFailed() is already true, it does nothing.
   * Sets hasFailed() to return true.
   * Shuts down the channel (if there is one) to the server.
   * Waits up to 5 seconds for that to terminate.
   */
  public synchronized void failure() {
    if (!failed) {
      failed = true;
      // if this object's channel somehow changes, we don't want to change the one we're working on here
      final ManagedChannel channel = getChannel(); 
      if (channel != null) {
        channel.shutdown();
        try {
          channel.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          logger.log(Level.WARNING, "SendToObserver was interrupted while trying to Terminate a channel", e);
        }
      }
    }
  }

  /**
   * Call this to end the RPC.
   * This differs from failed() in that it does shut down the internal StreamObserver watching server responses, if any.
   * This is idempotent: if hasFailed() is already true, it does nothing.
   * Sets hasFailed() to return true.
   * Shuts down the channel (if there is one) to the server.
   * Waits up to 5 seconds for that to terminate.
   */
  public void onCompleted() {
    if (observer != null) {
      observer.onCompleted();
    } else {
      failure();
    }
  }

  /** 
   * Send the message getMostRecent() onto the wire.
   * This also logs the sent block's hash and size.
   */
  private void send() {
    observer.onNext(getMostRecent());
//    try {
//      logger.info("{ \"SentBlock\":"+JsonFormat.printer().print(sha3Hash(getMostRecent().getBlock()))+
//              (getMostRecent().getBlock().hasHetconsBlock() ? ("\n Message Type: " + getMostRecent().getBlock().getHetconsBlock().getHetconsMessage().getType()) : "") +
//                  loggingString +
//                  ",\n \"size\":" + getMostRecent().getSerializedSize() + " }");
//    } catch (InvalidProtocolBufferException e) {
//      logger.log(Level.SEVERE, "Invalid protocol buffer parsed as Block", e);
//    }
  }

  /**
   * Run (which a Thread will call) will loop.
   * At start, it opens a channel and an RPC to the server.
   * It sends the SendBlocksInput given in the constructor (if any).
   * Then it loops so long as hasFailed() is false.
   * Take from the queue (blocking operation).
   * Give whatever it got from the queue to the StreamObserver's onNext method.
   */
  public void run() {
    // exponential backoff in pseudo-randomized channel opening wait times.
    channel = getClient().getContact().getManagedChannel(1000000000l /** 1 second */ << (client.getChannelRebootCount() > 9 ? 10 : client.getChannelRebootCount()));
    observer = CharlotteNodeGrpc.newStub(getChannel()).sendBlocks(new SendBlocksResponseObserver(getClient(), this));
    if (null != getMostRecent() && !hasFailed()) {
      send();
    }
    while (!hasFailed()) {
      try {
        mostRecent = queue.take();
        send();
      } catch (InterruptedException e) {
        logger.log(Level.WARNING, "SendToObserver was interrupted while trying to pull from queue", e);
      }
    }
  }
}
