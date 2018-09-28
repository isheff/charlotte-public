package com.isaacsheff.charlotte.node;

import com.isaacsheff.charlotte.proto.SendBlocksResponse;

import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Each time a CharlotteNode Client calls SendBlocks, one of these is created to watch all the responses it gets back.
 * @author Isaac Sheff
 */
public class SendBlocksResponseObserver implements StreamObserver<SendBlocksResponse>{
  /**
   * Use logger for logging events on SendBlocksResponseObserver.
   */
  private static final Logger logger = Logger.getLogger(CharlotteNodeClient.class.getName());

  /**
   * Used in shutting down
   */
  private final CountDownLatch finishLatch;

  private boolean failed;
  private CharlotteNodeClient client;

  /**
   * @param finishLatch Used in shutting down
   */
  public SendBlocksResponseObserver(CountDownLatch finishLatch, CharlotteNodeClient client) {
    this.finishLatch = finishLatch;
    this.client = client;
    failed = false;
  }

  public boolean hasFailed() {return failed;}

  /**
   * Each time a new SendBlocksResponse comes in, this is called.
   * We just log a warning with the error message.
   * @param response the newly arrived SendBlocksResponse from the wire.
   */
  @Override
  public void onNext(SendBlocksResponse response) {
    logger.log(Level.WARNING,"Send Blocks Response: " + response.getErrorMessage());
  }

  /**
   * Each time something goes wrong with sendBlocks on the wire, this is called.
   * We just log it.
   * @param t the Throwable from gRPC representing whatever went wrong.
   */
  @Override
  public void onError(Throwable t) {
    synchronized(this) {
      if (!failed) {
        failed = true;
        logger.log(client.getChannelRebootLoggingLevel(), "SendBlocks from " +
          client.getContact().getParentConfig().getUrl() + ":" + client.getContact().getParentConfig().getPort() +
          " to "+ client.getContact().getUrl() + ":" + client.getContact().getPort() +
          " Failed: ", t);
        client.reset();
      }
    }
    finishLatch.countDown();
  }

  /**
   * Called when we're done with this sendBlocks.
   */
  @Override
  public void onCompleted() {
    this.failed = true;
    logger.info("SendBlocks Finished.");
    finishLatch.countDown();
  }
}
