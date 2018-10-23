package com.isaacsheff.charlotte.node;

import static io.grpc.Grpc.TRANSPORT_ATTR_SSL_SESSION;

import javax.net.ssl.SSLSession;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * A ServerInterceptor which for each call, adds to the current context the sslContext featuring client authentication stuff.
 * The Key for this is SSL_SESSION_CONTEXT, and its type is SSLSession.
 * Based on documentation from gRPC README.
 * @see https://github.com/grpc/grpc-java/issues/4905
 * @author Isaac Sheff
 */
public class MutualTLSContextInterceptor implements ServerInterceptor {

  /** The key for the SSLSession (featuring client authentication stuff) in the gRPC current context */
  public final static Context.Key<SSLSession> SSL_SESSION_CONTEXT = Context.key("SSLSession");

  /**
   * For each call made to the server, extract the sslSession, and add it to the gRPC current context.
   * @param call object to receive response messages
   * @param  headers which can contain extra call metadata from
   *                 ClientCall.start(io.grpc.ClientCall.Listener<RespT>, io.grpc.Metadata),
   *                 e.g. authentication credentials.
   * @param next - next processor in the interceptor chain
   * @return listener for processing incoming messages for call, never null.
   */
  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(final ServerCall<ReqT, RespT> call, 
                                                               final Metadata headers,
                                                               final ServerCallHandler<ReqT, RespT> next) {
    final SSLSession sslSession = call.getAttributes().get(TRANSPORT_ATTR_SSL_SESSION);
    if (sslSession == null) { // I'm not really sure what this condition means, but it's in the example code.
      return next.startCall(call, headers);
    }
    return Contexts.interceptCall(Context.current().withValue(SSL_SESSION_CONTEXT, sslSession), call, headers, next);
  }
}
