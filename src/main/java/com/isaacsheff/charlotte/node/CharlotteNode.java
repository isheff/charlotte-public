package com.isaacsheff.charlotte.node;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.Server;
import io.grpc.ServerBuilder;

/**
 * When run, a CharlotteNode boots up a server featuring a CharlotteNodeService.
 */
public class CharlotteNode implements Runnable {
  /**
   * Use logger for logging events on CharlotteNodes.
   */
  private static final Logger logger = Logger.getLogger(CharlotteNode.class.getName());

  /**
   * What port (think TCP) does this server run on?.
   * This is only actually stored so we can put it in log messages.
   * It's only other use is if we have to create a default serverBuilder.
   */
  private final int port;

  /**
   * The gRPC server which is running this CharlotteNode.
   */
  private final Server server;

  /**
   * The CharlotteNodeService used by this server.
   * This controls the actual behaviour of the server in response to RPC calls.
   */
  private final CharlotteNodeService service;

  /**
   * Construct a CharlotteNode given a service, a serverBuilder on which to run the service, and a port.
   * @param service the object which controls what the node does on each RPC call
   * @param serverBuilder makes a server which listens for RPCs, given a service
   * @param port the port (think TCP) on which the server will run. Only actually used for logging messages.
   */
  public CharlotteNode(CharlotteNodeService service, ServerBuilder<?> serverBuilder, int port) {
    this.port = port;
    this.service = service;
    this.server = serverBuilder.addService(getService()).build();
  }


  /**
   * Construct a CharlotteNode with a default service, given a serverBuilder on which to run the service, and a port.
   * The service will be a CharlotteNodeService object.
   * @param serverBuilder makes a server which listens for RPCs, given a service
   * @param port the port (think TCP) on which the server will run. Only actually used for logging messages.
   */
  public CharlotteNode(ServerBuilder<?> serverBuilder, int port) {
    this(new CharlotteNodeService(), serverBuilder, port);
  }

  /**
   * Construct a CharlotteNode with a given service, a default serverBuilder on which to run the service, and a port.
   * The ServerBuilder will be a default one for the given port.
   * @param service the object which controls what the node does on each RPC call
   * @param port the port (think TCP) on which the server will run. Used in generating the serverBuilder.
   */
  public CharlotteNode(CharlotteNodeService service, int port) {
    this(service, ServerBuilder.forPort(port), port);
  }

  /**
   * Construct a CharlotteNode with a default service, a default serverBuilder on which to run the service, and port.
   * The service will be a CharlotteNodeService object.
   * The ServerBuilder will be a default one for the given port.
   * @param port the port (think TCP) on which the server will run. Used in generating the serverBuilder.
   */
  public CharlotteNode(int port) {
    this(ServerBuilder.forPort(port), port);
  }


  /** 
   * This method will be called when a new thread spawns featuring a CharlotteNode.
   * It starts the server.
   */
  public void run() {
    try {
      server.start();
      logger.info("Server started, listening on " + port);
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
  protected void onIOException(IOException exception) {
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
