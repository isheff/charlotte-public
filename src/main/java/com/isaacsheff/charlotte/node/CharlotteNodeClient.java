package com.isaacsheff.charlotte.node;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.proto.CharlotteNodeGrpc;
import com.isaacsheff.charlotte.proto.CharlotteNodeGrpc.CharlotteNodeStub;
import com.isaacsheff.charlotte.proto.SendBlocksInput;
import com.isaacsheff.charlotte.proto.SendBlocksResponse;
import com.isaacsheff.charlotte.experiments.SendToObserverLogging;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.yaml.Contact;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

/**
 * Used for communicating with a CharlotteNode server.
 * On construction, this opens a sendBlocks rpc to the server.
 * It queues blocks to be sent via its sendBlock method.
 * These blocks will be sent as fast as possible, via an internal thread that dequeues and sends them.
 * Any responses that come in are handled by a SendBlocksResponseObserver, which can be overridden, but just logs stuff.
 * @author Isaac Sheff
 */
public class CharlotteNodeClient {
  /**
   * Use logger for logging events on CharlotteNodeClients.
   */
  private static final Logger logger = Logger.getLogger(CharlotteNodeClient.class.getName());

  private final Contact contact;

  private Level channelRebootLoggingLevel;
  private int channelRebootCount;

  /**
   * The channel through which we communicate to the server.
   */
  private ManagedChannel channel;

  /**
   * The stub which sends messages to the CharlotteNode service within the server (this is a gRPC thing).
   * Never actually accessed in this class outside the constructor.
   */
  private CharlotteNodeStub asyncStub;

  /**
   * The Queue of Blocks waiting to be sent
   */
  private final BlockingQueue<SendBlocksInput> sendBlocksQueue;

  /**
   * The Thread which reads blocks from the queue, and sends them along to the server via sendBlocksInputObserver.
   * Never actually accessed in this class outside the constructor.
   */
  private Thread sendBlocksThread;

  /**
   * Runs in the thread: reads blocks from the queue, and sends them along to the server via sendBlocksInputObserver.
   * Never actually accessed in this class outside the constructor.
   */
  private SendToObserverLogging sendBlocksRunnable;

  private SendBlocksInput mostRecentSent;

  /**
   * Represents the single call to sendBlocks which this Client wraps.
   * This is used to stream blocks along that call.
   */
  private StreamObserver<SendBlocksInput> sendBlocksInputObserver;

  /**
   * Used in shutting down SendBlocksResponseObserver.
   */
  private CountDownLatch sendBlocksCountDownLatch;

  /**
   * This object handles each response sent back over the wire from our single call to SendBlocks.
   * This just logs error messages.
   * Override it or something if you want some other functionality.
   */
  private SendBlocksResponseObserver sendBlocksResponseObserver;



  /**
   * Opens a sendBlocks rpc to the server.
   * It queues blocks to be sent via its sendBlock method.
   * These blocks will be sent as fast as possible, via an internal thread that dequeues and sends them.
   * Responses that come in are handled by a SendBlocksResponseObserver, which can be overridden, but just logs stuff.
   * @param contact the Contact representing the server.
   */
  public CharlotteNodeClient(Contact contact) {
    this.contact = contact;
    channelRebootCount = 0;
    sendBlocksQueue = new LinkedBlockingQueue<SendBlocksInput>();
    mostRecentSent = null;
    channelRebootLoggingLevel = Level.WARNING;
    reset();
  }


  /** DANGER: only SendBlocksResponseObserver should call this */
  public void reset() {
    synchronized (this) {
      if (channel != null) {
        channel.shutdown(); // we don't await termination. If the server dies before this does, too bad.
        ++channelRebootCount;
        if (channelRebootCount > 10) {
          channelRebootLoggingLevel = Level.FINE;
        logger.log(getChannelRebootLoggingLevel(), "rebooting channel");
        }
      }
      channel = contact.getManagedChannel();
      asyncStub = CharlotteNodeGrpc.newStub(channel);
      sendBlocksCountDownLatch = new CountDownLatch(1);
      sendBlocksResponseObserver = new SendBlocksResponseObserver(getSendBlocksCountDownLatch(), this);
      sendBlocksInputObserver = asyncStub.sendBlocks(getSendBlocksResponseObserver());
      sendBlocksRunnable = new SendToObserverLogging(this, mostRecentSent, sendBlocksInputObserver, sendBlocksResponseObserver);
      sendBlocksThread = new Thread(sendBlocksRunnable);
      sendBlocksThread.start();
    }
  }

  public Level getChannelRebootLoggingLevel() {return channelRebootLoggingLevel;}

  /** DANGER: only SendToObserver should ever call this **/
  public SendBlocksInput pullFromQueue() {
    try {
      mostRecentSent = sendBlocksQueue.take();
      return mostRecentSent;
    } catch (InterruptedException e) {
      logger.log(Level.SEVERE, "interrupted while taking from sendblocks queue");
      return null;
    }
  }

  /**
   * Shut down this client. 
   * Tries to close out everything, but I think the dequeueing/sending thread may get zombied.
   * @throws InterruptedException  if the thread was interrupted while trying to shut down the channel to the server.
   */
  public void shutdown() throws InterruptedException {
    // Mark the end of requests
    sendBlocksInputObserver.onCompleted();

    // Receiving happens asynchronously
    getSendBlocksCountDownLatch().await(1, TimeUnit.MINUTES);

    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  /**
   * Queue a block for sending along the sendBlocks rpc as soon as possible.
   * @param inputBlock the SnedBlocksInput you want to send
   * @return whether queueing was successful. If something went wrong, it will be in the logs.
   */
  public boolean sendBlock(SendBlocksInput inputBlock) {
    try {
      sendBlocksQueue.put(inputBlock);
      return true; // all went well
    } catch (InterruptedException e) {
      logger.log(Level.WARNING, "Thread Interrupted while tyring to send block: " + inputBlock, e);
    } catch (ClassCastException e) {
      logger.log(Level.WARNING, "Tried to send a block that didn't class cast correctly: " + inputBlock, e);
    } catch (NullPointerException e) {
      logger.log(Level.WARNING, "Tried to send a null block.", e);
    } catch (IllegalArgumentException e) {
      logger.log(Level.WARNING, "Something is wrong with this block; I can't queue it for sending: " + inputBlock, e);
    }
    return false; // we haven't returned yet, so an exception happened, so the block didn't queue correctly.
  }

  /**
   * Queue a block for sending along the sendBlocks rpc as soon as possible.
   * @param inputBlock the block you want to send
   * @return whether queueing was successful. If something went wrong, it will be in the logs.
   */
  public boolean sendBlock(Block inputBlock) {
    return sendBlock(SendBlocksInput.newBuilder().setBlock(inputBlock).build());
  }

  /**
   * The countdownLatch used by the StreamObserver observing responses for the sendBlocks rpc.
   */
  protected CountDownLatch getSendBlocksCountDownLatch() {return sendBlocksCountDownLatch;}

  /**
   * Override this if you want different functionality each time a SendBlocksResponse comes in.
   * This will be called in the constructor.
   * It's onNext method is called each time the sendBlocks call gets a response in its stream.
   * Note that it's expected to use the countDownLatch obtainable using getSendBlocksCountDownLatch().
   * You can use that, or override getSendBlocksCountDownLatch() too.
   * @return the StreamObserver which will observe the responses from the sendBlocks rpc.
   */
  protected StreamObserver<SendBlocksResponse> getSendBlocksResponseObserver() {return sendBlocksResponseObserver;}
}
