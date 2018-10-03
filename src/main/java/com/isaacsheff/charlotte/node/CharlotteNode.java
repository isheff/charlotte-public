package com.isaacsheff.charlotte.node;

import static io.netty.handler.ssl.ClientAuth.REQUIRE;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;

/**
 * When run, a CharlotteNode boots up a server featuring a CharlotteNodeService,
 *  and optionally other services.
 * @author Isaac Sheff
 */
public class CharlotteNode implements Runnable {

  /** Use logger for logging events on CharlotteNodes. */
  private static final Logger logger = Logger.getLogger(CharlotteNode.class.getName());

  /** The gRPC server which is running this CharlotteNode. */
  private final Server server;

  /**
   * The CharlotteNodeService used by this server.
   * This controls the actual behaviour of the server in response to RPC calls.
   */
  private final CharlotteNodeService service;

  /**
   * Construct a CharlotteNode with a CharloteNodeService, and other services to be run on this server.
   * The CharlotteNodeService's Config will determine the port this runs on.
   * @param nodeService the CharlotteNodeService to be run on this server.
   * @param services the other services to be run on this server (e.g. Fern, Wilbur)
   */
  public CharlotteNode(final CharlotteNodeService nodeService, final Iterable<BindableService> services) {
    service = nodeService;
    final NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(getPort());
    try {
      serverBuilder.sslContext(GrpcSslContexts.forServer(service.getConfig().getX509Stream(),
                                                         service.getConfig().getPrivateKeyStream()).
                                               trustManager(service.getConfig().getTrustCertStream()).
                                               clientAuth(REQUIRE).
                                               build());
    } catch (SSLException e) {
      logger.log(Level.SEVERE, "Problems setting the SSL Context for the serverBuilder", e);
    }
    serverBuilder.addService(service);
    for (BindableService bindableService : services) {
      serverBuilder.addService(bindableService);
    }
    server = serverBuilder.build();
  }

  /**
   * Construct a CharlotteNode with a CharloteNodeService, and another service to be run on this server.
   * The CharlotteNodeService's Config will determine the port this runs on.
   * Equivalent to CharlotteNode(nodeService, java.util.Collections.singleton(otherService));
   * @param nodeService the CharlotteNodeService to be run on this server.
   * @param otherService the other service to be run on this server (e.g. Fern, Wilbur)
   */
  public CharlotteNode(final CharlotteNodeService nodeService, final BindableService otherService) {
    this(nodeService, singleton(otherService));
  }

  /**
   * Construct a CharlotteNode with a given service, a default serverBuilder on which to run the service.
   * Uses the port from the config file for this contact.
   * @param service the object which controls what the node does on each RPC call
   */
  public CharlotteNode(final CharlotteNodeService service) {
    this(service, emptySet());
  }

  /**
   * Construct a CharlotteNode configured by the file with the given filename.
   * @param filename name of the config file
   */
  public CharlotteNode(final String filename) {
    this(new CharlotteNodeService(filename));
  }

  /** @return the port on which this server operates, as set by the CharlotteNodeService's Config */
  public int getPort() {return getService().getConfig().getPort();}

  /** 
   * This method will be called when a new thread spawns featuring a CharlotteNode.
   * It starts the server.
   */
  public void run() {
    try {
      server.start();
      logger.info("Server started, listening on " + getPort());
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          CharlotteNode.this.onShutdown();
        }
      });
    } catch (IOException e) {
      onIOException(e);
    }

  }

  /**
   * Called only when the JVM is shut down, and so the gRPC server must die.
   * This calls stop(), as should any overrides of this method.
   */
  protected void onShutdown() {
    System.err.println("*** shutting down gRPC server since JVM is shutting down");
    this.stop();
    System.err.println("*** server shut down");
  }

  /**
   * This method is called only when there is an IOException while running the server.
   * It logs the exception as SEVERE, since this is a bad thing that hopefully never happens.
   * @param exception the IOException which was thrown while running the server.
   */
  protected void onIOException(final IOException exception) {
    logger.log(Level.SEVERE, "IOException thrown while running server.", exception);
  }
    

  /**
   * Stop the server from responding to any more RPCs.
   * I'm not actually sure if this will result in thread termination.
   */
  public void stop() {
    if (server != null) {
      server.shutdown();
    }
  }


  /**
   * Returns the service object which actually controls the behaviour in response to RPC calls.
   * This is called when starting up the server.
   * @return the CharlotteNodeService object which actually controls the behaviour in response to RPC calls.
   */
  public CharlotteNodeService getService() {
    return this.service;
  }



}
