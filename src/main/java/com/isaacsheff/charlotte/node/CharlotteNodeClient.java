package com.isaacsheff.charlotte.node;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.proto.Challenge;
import com.isaacsheff.charlotte.proto.ChallengeInput;
import com.isaacsheff.charlotte.proto.CharlotteNodeGrpc;
import com.isaacsheff.charlotte.proto.CharlotteNodeGrpc.CharlotteNodeBlockingStub;
import com.isaacsheff.charlotte.proto.CharlotteNodeGrpc.CharlotteNodeStub;
import com.isaacsheff.charlotte.proto.ResponseToChallenge;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;

public class CharlotteNodeClient {
  /**
   * Use logger for logging events on CharlotteNodeClients.
   */
  private static final Logger logger = Logger.getLogger(CharlotteNodeClient.class.getName());

  protected final ManagedChannel channel;
  private final CharlotteNodeBlockingStub blockingStub;
  private final CharlotteNodeStub asyncStub;

  public CharlotteNodeClient(ManagedChannel channel) {
    this.channel = channel;
    blockingStub = CharlotteNodeGrpc.newBlockingStub(channel);
    asyncStub = CharlotteNodeGrpc.newStub(channel);
  }

  public CharlotteNodeClient(InputStream cert, NettyChannelBuilder channelBuilder) {
    this(channelBuilder.useTransportSecurity().enableRetry().sslContext(getContext(cert)).build());
  }

  private static SslContext getContext(InputStream cert) {
    SslContext context = null;
    try {
      context = GrpcSslContexts.forClient().trustManager(cert).build();
    } catch (Exception e) {
      logger.log(Level.WARNING, "something went wrong setting trust manager", e);
    }
    return context;
  }


  public CharlotteNodeClient(InputStream cert, String url, int port) {
    this(cert, NettyChannelBuilder.forAddress(url,port));
  }

  public void shutdown() throws InterruptedException {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
  }

  public void testChallengeResponseBlocking() {
    ChallengeInput challenge = ChallengeInput.newBuilder().setChallenge(
                                 Challenge.newBuilder().setStr("this is a challenge")).build();
    try {
      ResponseToChallenge response = blockingStub.challengeResponse(challenge);
      logger.info("response retrieved from server in blocking Challenge RPC");
    } catch (StatusRuntimeException e) {
      logger.log(Level.WARNING, "RPC failed: ", e);
    }
  }


}


