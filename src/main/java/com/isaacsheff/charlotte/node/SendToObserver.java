package com.isaacsheff.charlotte.node;

import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.grpc.stub.StreamObserver;

/**
 * Send each element queued to the StreamObserver.
 * This is Runnable, so it can be run in a seperate Thread, if the StreamObserver's onNext function might be slow.
 * @author Isaac Sheff
 */
public class SendToObserver<T> implements Runnable {
  /**
   * Use logger for logging events involving SendToObserver.
   */
  private static final Logger logger = Logger.getLogger(SignatureUtil.class.getName());

  /**
   * The queue from which we pull elements to give to the StreamObserver.
   */
  private final BlockingQueue<T> queue;

  /**
   * The StreamObserver which will receive all the things queued.
   */
  private final StreamObserver<T> observer;

  /**
   * Create a Runnable which will send each element queued to the StreamObserver.
   * It can be run in a seperate Thread, if the StreamObserver's onNext function might be slow.
   * @param queue The queue from which we pull elements to give to the StreamObserver.
   * @param observer The StreamObserver which will receive all the things queued.
   */
  public SendToObserver(BlockingQueue<T> queue, StreamObserver<T> observer) {
    this.queue = queue;
    this.observer = observer;
  }

  /**
   * Run (which a Thread will call) will loop.
   * Take from the queue (blocking operation).
   * Give whatever it got from the queue to the StreamObserver's onNext method.
   */
  public void run() {
    while (true) {
      try {
        observer.onNext(queue.take());
      } catch (InterruptedException e) {
        logger.log(Level.WARNING, "SendToObserver was interrupted while trying to pull from queue", e);
      }
    }
  }
}
