package com.isaacsheff.charlotte.node;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;

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
    try{
      serverBuilder.useTransportSecurity( getCertStream(service.getKeyPair()),
                                          getPrivateStream(service.getKeyPair().getPrivate()));
    } catch (Exception e) {
      logger.log(Level.WARNING, "something broke when we went to make the certs. TLS is not working: ", e);
    }
    this.server = serverBuilder.addService(service).build();
  }
  /**
   * Creates a private key PEM file from the key, and pipes the bytes into the InputStream it returns.
   * Literally the bytes of the PEM formatted file.
   * @param key the private key we want as a PEM file
   * @return an inputStream which will effectively read the PEM file
   * @throws IOException if something goes wrong with the streams or something.
   */
  private static PipedInputStream getPrivateStream(PrivateKey key) throws IOException {
    // In order to get from a PEMObject to an InputStream, I'm going to use a pair of Piped Output and Input Streams.
    // The bytes put into one can be read from the other.
    // These are meant to be used in two different threads, but we'll give them a big buffer, so it'll be ok.
    // I'm not sure if it would be better to get an outputstream to write to a byte[], and then pass that to 
    //  an input stream, instead of this piping thing.
    PipedInputStream stream = new PipedInputStream(10000);
    JcaPEMWriter writer = new JcaPEMWriter(new OutputStreamWriter(new PipedOutputStream(stream)));
    writer.writeObject(new PemObject("PRIVATE KEY", key.getEncoded()));
    writer.close();
    return stream;
  }

  /**
   * Creates an X509 certificte from the keypair, and pipes the bytes into the InputStream it returns.
   * Literally the bytes of the certificate file as a PEM formatted file.
   * @param keyPair the public/private key pair to become a self-signed X.509 PEM certificate
   * @return an InputStream that effectively reads the PEM file
   * @throws IOException if something goes wrong with the streams or something
   * @throws OperatorCreationException if something goes wrong with making the certificate
   */
  private static PipedInputStream getCertStream(KeyPair keyPair) throws IOException, OperatorCreationException {
    // In order to get from a PEMObject to an InputStream, I'm going to use a pair of Piped Output and Input Streams.
    // The bytes put into one can be read from the other.
    // These are meant to be used in two different threads, but we'll give them a big buffer, so it'll be ok.
    // I'm not sure if it would be better to get an outputstream to write to a byte[], and then pass that to 
    //  an input stream, instead of this piping thing.
    PipedInputStream certStream = new PipedInputStream(10000);
    JcaPEMWriter writer = new JcaPEMWriter(new OutputStreamWriter(new PipedOutputStream(certStream)));

    // make "signer," which we'll use in signing (self-signing) the certificate
    JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder("SHA256WITHECDSA");
    ContentSigner signer = csBuilder.build(keyPair.getPrivate());

    // useful for setting dates in the certificate file
    LocalDateTime startDate = LocalDate.now().atStartOfDay();

    // configure the certificate itself
    X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
        new X500Name("CN=ca"), // bullshit value we just made up
        new BigInteger("0"),   // bullshit value we just made up
        Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant()),
        Date.from(startDate.plusDays(3650).atZone(ZoneId.systemDefault()).toInstant()), // 10 years from now
        new X500Name("CN=ca"), // bullshit value we just made up
        SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));

    // make the certificate using the configuration, signed with signer
    X509CertificateHolder holder = builder.build(signer);

    // write the certificate to the stream in PEM format
    writer.writeObject(new PemObject("CERTIFICATE", holder.toASN1Structure().getEncoded()));

    writer.close();
    return certStream;
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
