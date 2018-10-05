package com.isaacsheff.charlotte.node;

import static com.isaacsheff.charlotte.node.SignatureUtil.createCryptoId;

import java.security.cert.Certificate;
import java.security.PublicKey;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.SendBlocksInput;
import com.isaacsheff.charlotte.proto.SendBlocksResponse;
import com.isaacsheff.charlotte.yaml.Contact;

import io.grpc.stub.StreamObserver;

/**
 * One of these is created whenever a CharlotteNodeService gets a SendBlocks RPC.
 * It handles the stream of incoming blocks, and streams outgoing responses.
 * By default, it calls onSendBlocksInput on its CharlotteNodeService for each new block.
 * @author Isaac Sheff
 */
public class SendBlocksObserver implements StreamObserver<SendBlocksInput> {
  /** Use logger for logging events on a CharlotteNodeService. */
  private static final Logger logger = Logger.getLogger(SendBlocksObserver.class.getName());

  /** The CharlotteNodeService for which this observer exists. */
  private final CharlotteNodeService charlotteNodeService;

  /** The StreamObserver through which we stream responses over the wire. */
  private final StreamObserver<SendBlocksResponse> responseObserver;

  /** Represents the SSL session of the channel through which this call is taking place. */
  private final SSLSession session;

  /** The Contact from which this incoming stream is being sent, or null, if we can't identify it. */
  private final Contact contact;

  /** The CryptoId of the client from which this incoming stream is being sent, or null, if we can't identify it. */
  private final CryptoId cryptoId;

  /** The Public Key of the client from which this incoming stream is being sent, or null, if we can't identify it. */
  private final PublicKey publicKey;

  /**
   * Constructor.
   * A sendBlocks request has just arrived at the service.
   * @param service the associated CharlotteNodeService. The service that is receiving the RPC this serves.
   * @param responseObserver the stream via which we send responses over the wire.
   */
  public SendBlocksObserver(final CharlotteNodeService service,
                            final StreamObserver<SendBlocksResponse> responseObserver,
                            final SSLSession session) {
    this.charlotteNodeService = service;
    this.responseObserver = responseObserver;
    this.session = session;
    PublicKey publicKey = null;
    CryptoId cryptoId = null;
    Contact contact = null;
    try {
      final Certificate[] certificates = session.getPeerCertificates();
      if (certificates.length == 0) {
        logger.log(Level.SEVERE, "Certificate chain in the SSLContext was empty.");
      } else {
        publicKey = certificates[0].getPublicKey();
        cryptoId = createCryptoId(publicKey);
        contact = service.getConfig().getContact(cryptoId);
        if (contact == null) {
          logger.log(Level.SEVERE, "SSL Channel has a cryptoId not found among my Contacts: " + cryptoId);
        }
      }
    } catch (SSLPeerUnverifiedException e) {
      logger.log(Level.SEVERE, "Unable to verify client certificate in incoming connection", e);
    }
    this.publicKey = publicKey;
    this.cryptoId = cryptoId;
    this.contact = contact;
  }

  /** @return the CharlotteNodeService associated with this Observer. */
  public CharlotteNodeService getCharlotteNodeService() {return charlotteNodeService;}

  /** @return the StreamObserver through which we send responses over the wire. */
  public StreamObserver<SendBlocksResponse> getResponseObserver() {return responseObserver;}

  /** @return Represents the SSL session of the channel through which this call is taking place. */
  public SSLSession getSession() {return session;}

  /** @return The Contact from which this incoming stream is being sent, or null, if we can't identify it. */
  public Contact getContact() {return contact;}

  /** @return The CryptoId of the client from which this incoming stream is being sent, or null, if we can't identify it.*/
  public CryptoId getCryptoId() {return cryptoId;}

  /** @return The Public Key of the client from which this incoming stream is being sent, or null, if we can't identify it.*/
  public PublicKey getPublicKey() {return publicKey;}

  /**
   * What do we do each time a block arrives over the wire?
   * Call getCharlotteNodeService().onSendBlocksInput.
   * @param input the new SendBlocksInput that has just arrived on the wire.
   */
  public void onNext(SendBlocksInput input) {
    for (SendBlocksResponse response : (getCharlotteNodeService().onSendBlocksInput(input, this))) {
      getResponseObserver().onNext(response);
    }
  }

  /**
   * What do we do when the RPC is over (input stream closes).
   * We close the output stream.
   */
  public void onCompleted() {
    getResponseObserver().onCompleted();
  }

  /**
   * Called when there is an error in the stream.
   * We log it as a warning.
   * @param t the error arising from the stream.
   */
  public void onError(Throwable t) {
    getCharlotteNodeService().sendBlocksCancelled(t);
  }
}
