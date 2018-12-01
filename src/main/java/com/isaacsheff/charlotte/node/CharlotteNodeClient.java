package com.isaacsheff.charlotte.node;

import com.isaacsheff.charlotte.proto.SendBlocksInput;
import com.isaacsheff.charlotte.proto.SendBlocksResponse;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.yaml.Contact;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Used for communicating with a CharlotteNode server.
 * On construction, this opens a sendBlocks rpc to the server.
 * It queues blocks to be sent via its sendBlock method.
 * These blocks will be sent as fast as possible, via an internal thread that dequeues and sends them.
 * Any responses that come in are handled by onSendBlocksResponse, which can be overridden, but just logs stuff by default.
 * @author Isaac Sheff
 */
public class CharlotteNodeClient {
  /** Use logger for logging events on CharlotteNodeClients. */
  private static final Logger logger = Logger.getLogger(CharlotteNodeClient.class.getName());

  /** Represents the server which this Client is contacting. */
  private final Contact contact;

  /** The Queue of Blocks waiting to be sent */
  private final BlockingQueue<SendBlocksInput> sendBlocksQueue;

  /** The Thread which reads blocks from the queue, and sends them along to the server via sendBlocksInputObserver. */
  private Thread sendBlocksThread;

  /** How many times has this channel had an error and had to reboot? */
  private int channelRebootCount;

  /** The Runnable object that pulls from sendBlocksQueue and sends blocks in the sendBlocksThread */
  private SendToObserver sendToObserver;

  /**
   * Opens a sendBlocks rpc to the server.
   * It queues blocks to be sent via its sendBlock method.
   * These blocks will be sent as fast as possible, via an internal thread that dequeues and sends them.
   * Any responses that come in are handled by onSendBlocksResponse, which can be overridden, but just logs stuff by default.
   * @param contact the Contact representing the server.
   */
  public CharlotteNodeClient(final Contact contact) {
    this.contact = contact;
    channelRebootCount = 0;
    sendBlocksQueue = new LinkedBlockingQueue<SendBlocksInput>();
    sendToObserver = null;
    reset(sendToObserver);
  }

  /** @return Represents the server which this Client is contacting. */
  public Contact getContact() {return contact;}

  /** @return How many times has this channel had an error and had to reboot? */
  public int getChannelRebootCount() {return channelRebootCount;}


  /**
   * DANGER: only SendBlocksResponseObserver should call this.
   * This causes the client to kill off its old sendToObserver and related thread, close the channel, and start again.
   * This will open a new channel in a new thread, and re-send the most recent block sent (if any). 
   * It will then continue to dequeue and send blocks in that thread.
   * @param oldSendToObserver the previous SendToObserver object that was dequeueing blocks. If this does not match the one this client is currently using, this method does nothing.
   */
  public synchronized void reset(final SendToObserver oldSendToObserver) {
    if (oldSendToObserver == sendToObserver) {
      SendBlocksInput sendFirst = null;
      if (sendToObserver != null) {
        ++channelRebootCount;
        sendToObserver.failure();
        sendFirst = sendToObserver.getMostRecent();
      }
      sendToObserver = new SendToObserver(sendBlocksQueue, sendFirst, this);
      sendBlocksThread = new Thread(sendToObserver);
      sendBlocksThread.start();
    }
  }

  /**
   * Shut down this client. 
   * Tries to close out everything, including channels used.
   */
  public void shutdown() {
    sendToObserver.onCompleted();
  }

  /**
   * Queue a block for sending along the sendBlocks rpc as soon as possible.
   * @param inputBlock the SnedBlocksInput you want to send
   * @return whether queueing was successful. If something went wrong, it will be in the logs.
   */
  public boolean sendBlock(final SendBlocksInput inputBlock) {
    try {
      sendBlocksQueue.put(inputBlock);
      return true; // all went well
    } catch (InterruptedException e) {
      logger.log(Level.WARNING, "Thread Interrupted while tyring to send block: " + inputBlock, e);
    } catch (NullPointerException e) {
      logger.log(Level.WARNING, "Tried to send a null block.", e);
    }
    return false; // we haven't returned yet, so an exception happened, so the block didn't queue correctly.
  }

  /**
   * Queue a block for sending along the sendBlocks rpc as soon as possible.
   * @param inputBlock the block you want to send
   * @return whether queueing was successful. If something went wrong, it will be in the logs.
   */
  public boolean sendBlock(final Block inputBlock) {
    return sendBlock(SendBlocksInput.newBuilder().setBlock(inputBlock).build());
  }

  /**
   * This method is called whenever a SendBlocksResponse comes back over the wire.
   * Override it to do interesting things.
   * By default, it logs a warning.
   * @param response the SendBlocksResponse that came in over the wire
   * @param sender the object that was dequeuing blocks and sending them to the server in the RPC call from which this response came.
   * @param observer the object that was receiving blocks from the server in the RPC call from which this response came. Call onError or onCompleted on this to close the RPC.
   */
  public void onSendBlocksResponse(final SendBlocksResponse response,
                                   final SendToObserver sender,
                                   final SendBlocksResponseObserver observer) {
    logger.log(Level.WARNING,"Send Blocks Response: " + response.getErrorMessage());
  }
}
