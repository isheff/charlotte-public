package com.isaacsheff.charlotte.experiments;


import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;

import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.proto.SendBlocksInput;

import io.grpc.stub.StreamObserver;

/**
 * Send each element queued to the StreamObserver.
 * This is Runnable, so it can be run in a seperate Thread, if the StreamObserver's onNext function might be slow.
 * @author Isaac Sheff
 */
public class SendToObserverLogging<T extends SendBlocksInput> implements Runnable {
  /** Use logger for logging events involving SendToObserver. */
  private static final Logger logger = Logger.getLogger(SendToObserverLogging.class.getName());

  /** The queue from which we pull elements to give to the StreamObserver. */
  private final BlockingQueue<T> queue;

  /** The StreamObserver which will receive all the things queued. */
  private final StreamObserver<T> observer;

  /**
   * Create a Runnable which will send each element queued to the StreamObserver.
   * It can be run in a seperate Thread, if the StreamObserver's onNext function might be slow.
   * @param queue The queue from which we pull elements to give to the StreamObserver.
   * @param observer The StreamObserver which will receive all the things queued.
   */
  public SendToObserverLogging(final BlockingQueue<T> queue, final StreamObserver<T> observer) {
    this.queue = queue;
    this.observer = observer;
  }

  /**
   * Run (which a Thread will call) will loop.
   * Take from the queue (blocking operation).
   * Give whatever it got from the queue to the StreamObserver's onNext method.
   * This will then log a hash of the thing sent with the json key "SentBlock"
   */
  public void run() {
    T element;
    while (true) {
      try {
        element = queue.take();
        observer.onNext(element);
        logger.info("{ \"SentBlock\":"+JsonFormat.printer().print(sha3Hash(element.getBlock()))+
                    ", \"size\":" + element.getSerializedSize() + " }");
      } catch (InvalidProtocolBufferException e) {
        logger.log(Level.SEVERE, "Invalid protocol buffer parsed as Block", e);
      } catch (InterruptedException e) {
        logger.log(Level.WARNING, "SendToObserver was interrupted while trying to pull from queue", e);
      }
    }
  }
}
