package com.isaacsheff.charlotte.node;

import com.isaacsheff.charlotte.proto.SendBlocksResponse;

import io.grpc.stub.StreamObserver;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Each time a CharlotteNode Client calls SendBlocks, one of these is created to watch all the responses it gets back.
 * On an error or completion, it will close out the SendToObserver, which in turn closes out the Channel.
 * Furthermore, on an error, it will reset the Client, which will cause it to open a new channel and try again.
 * @author Isaac Sheff
 */
public class SendBlocksResponseObserver implements StreamObserver<SendBlocksResponse> {
  /** Use logger for logging events on SendBlocksResponseObserver. */
  private static final Logger logger = Logger.getLogger(SendBlocksResponseObserver.class.getName());

  /** Represents the local handle for talking to a particular server */
  private final CharlotteNodeClient client;

  /** The object dequeueing messages and sending them to the rRPC on which this object watches responses. */
  private final SendToObserver sendToObserver;

  /** Is this RPC over (encountered a failure or something) ? */
  private boolean failed;

  /**
   * Make a new SendBlocksResponseObserver.
   * On an error or completion, it will close out the SendToObserver, which in turn closes out the Channel.
   * Furthermore, on an error, it will reset the Client, which will cause it to open a new channel and try again.
   * @param client Represents the local handle for talking to a particular server 
   * @param sendToObserver The object dequeueing messages and sending them to the rRPC on which this object watches responses.
   */
  public SendBlocksResponseObserver(final CharlotteNodeClient client, final SendToObserver sendToObserver) {
    this.client = client;
    this.sendToObserver = sendToObserver;
    failed = false;
  }

  /** @return Is this RPC over (encountered a failure or something) ? */
  public boolean hasFailed() {return failed;}

  /**
   * Each time a new SendBlocksResponse comes in, this is called.
   * We pass this back up to the Client, which by default just logs a warning.
   * @param response the newly arrived SendBlocksResponse from the wire.
   */
  @Override
  public void onNext(final SendBlocksResponse response) {
    client.onSendBlocksResponse(response, sendToObserver, this);
  }

  /**
   * Each time something goes wrong with sendBlocks on the wire, this is called.
   * If we haven't already failed, we:
   * <ul>
   *   <li> Set hasFailed() to return true </li>
   *   <li> Tell sendToObserver to fail, which closes out the channel and such </li>
   *   <li> log it as a warning (the first 10 times), or as fine after that. </li>
   *   <li> Tell the client to reset, so it opens a new channel and tries again.</li>
   * </ul>
   * @param t the Throwable from gRPC representing whatever went wrong.
   */
  @Override
  public synchronized void onError(final Throwable t) {
    if (!hasFailed()) {
      sendToObserver.failure();
      failed = true;
      logger.log((client.getChannelRebootCount() > 10 ? Level.FINE : Level.WARNING), "SendBlocks from " +
        client.getContact().getParentConfig().getUrl() + ":" + client.getContact().getParentConfig().getPort() +
        " to "+ client.getContact().getUrl() + ":" + client.getContact().getPort() +
        " Failed: ", t);
      client.reset(sendToObserver);
    }
  }

  /**
   * Called when we're done with this sendBlocks.
   * This differs from onError in that no error has occurred, so we don't tell the client to reset.
   * We do set hasFailed() to return true.
   * We do tell the sendToObserver to fail, which closes the channel and such.
   * We log to INFO that SendBlocks Finished.
   */
  @Override
  public void onCompleted() {
    if (!hasFailed()) {
      failed = true;
      sendToObserver.failure();
      logger.info("SendBlocks Finished.");
    }
  }
}
