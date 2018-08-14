package com.isaacsheff.charlotte.fern;

import static java.util.concurrent.ConcurrentHashMap.newKeySet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.isaacsheff.charlotte.collections.ConcurrentHolder;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.proto.IntegrityAttestation;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.ChainSlot;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.SignedChainSlot;
import com.isaacsheff.charlotte.proto.IntegrityPolicy;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationResponse;
import com.isaacsheff.charlotte.proto.Signature;
import com.isaacsheff.charlotte.yaml.Contact;

import io.grpc.stub.StreamObserver;

/**
 * A client designed to be used with AgreementChainFernService to make blockchains using Agreement.
 * Realistically, chain-makers should use the braodcastWhenReady method to submit requests.
 * @author Isaac Sheff
 */
public class AgreementChainFernClient {
  /** Use logger for logging events in this class. */
  private static final Logger logger = Logger.getLogger(AgreementChainFernClient.class.getName());

  /** The local CharlotteNodeService used for receiving / transmitting blocks */
  private final CharlotteNodeService node;

  /** Represent asynchronous clients for each Fern Server out there */
  private final ConcurrentMap<CryptoId, AgreementChainClientPerServer> handles;

  /** Represent the threads on which the asynchronous clients for each Fern Server are running */
  private final Collection<Thread> handleThreads;

  /** the known integrity attestations for each block */
  private final ConcurrentMap<Hash, Set<Hash>> knownIntegrityAttestations;

  /** The known response to each request, in a concurrent holder, so you can ask for it, and wait for it */
  private final ConcurrentMap<RequestIntegrityAttestationInput, ConcurrentHolder<Hash>> knownResponses; 

  /**
   * Make a new AgreementChainFernClient.
   * This will try and make clients to talk to Fern Services on EACH of the participants.
   * In principle, if the clients cannot contact the Fern services (such as if they don't exist),
   *  nothing bad happens, but in practice Exceptions will probably be thrown.
   * @param node The local CharlotteNodeService used for receiving / transmitting blocks 
   */
  public AgreementChainFernClient(final CharlotteNodeService node) {
    this.node = node;
    knownIntegrityAttestations = new ConcurrentHashMap<Hash, Set<Hash>>();
    knownResponses = new ConcurrentHashMap<RequestIntegrityAttestationInput, ConcurrentHolder<Hash>>();
    handles = new ConcurrentHashMap<CryptoId, AgreementChainClientPerServer>(
                   getNode().getConfig().getContacts().size());
    handleThreads = new ArrayList<Thread>(getNode().getConfig().getContacts().size());
    for (Contact contact : getNode().getConfig().getContacts().values()) {
      final AgreementChainClientPerServer handle = new AgreementChainClientPerServer(this, contact);
      handles.putIfAbsent(contact.getCryptoId(), handle);
      final Thread handleThread = new Thread(handle);
      handleThreads.add(handleThread);
      handleThread.start();
    }
  }

  /** @return The local CharlotteNodeService used for receiving / transmitting blocks */
  public CharlotteNodeService getNode() {return node;}

  /** @return Represent asynchronous clients for each Fern Server out there */
  public ConcurrentMap<CryptoId, AgreementChainClientPerServer> getHandles() {return handles;}

  /** @return Represent the threads on which the asynchronous clients for each Fern Server are running */
  public Collection<Thread> getHandleThreads() {return handleThreads;}

  /** @return The known response to each request, in a concurrent holder, so you can ask for it, and wait for it */
  public ConcurrentMap<RequestIntegrityAttestationInput, ConcurrentHolder<Hash>> getKnownResponses()
    {return knownResponses;} 

  /**
   * Send a copy of the RequestIntegrityAttestationInput to all the Fern servers.
   * This will change the CryptoId in the Siganture of the SignedChainSlot of the
   *  FillInTheBlank for each Fern server.
   * @param input the RequestIntegrityAttestationInput to be sent to each Fern server.
   */
  public void broadcast(RequestIntegrityAttestationInput input) {
    for (AgreementChainClientPerServer handle : getHandles().values()) {
      handle.queueMessage(changeCryptoId(input, handle.contact.getCryptoId()));
    }
  }

  /**
   * Send this RequestIntegrityAttestationInput to the Fern server.
   * @param destination the CryptoId of the Fern Server
   * @param input the RequestIntegrityAttestationInput to be sent to each Fern server.
   */
  public void send(CryptoId destination, RequestIntegrityAttestationInput input) {
    getHandles().get(destination).queueMessage(input);
  }

  /**
   * Add an integrity attestation to a Reference.
   * This will not change the Reference if it already has the attestation in it.
   * So it's like builder.addIntegrityAttestations(attestation), but idempotent.
   * @param builder the Reference.Builder  for the reference we start with
   * @param attestation the Reference to the Attestation we want to add
   * @return the Reference.Builder with the reference added to its attestations field.
   */
  public Reference.Builder addIntegrityAttestation(Reference.Builder builder, Reference attestation) {
    for (Reference reference : builder.getIntegrityAttestationsList()) {
      if (reference.getHash().equals(attestation.getHash())) {
        return builder;
      }
    }
    return builder.addIntegrityAttestations(attestation);
  }

  /**
   * Add all known integrity attestations to the reference.
   * This is also idempotent in a way: it will nto add multiple references to the same attestation.
   * @param builder the Reference.Builder representing the reference
   * @return builder, but with all known integrity attestations added. 
   */
  public Reference.Builder addIntegrityAttestations(Reference.Builder builder) {
    final Set<Hash> knownHashes = knownIntegrityAttestations.get(builder.getHash());
    if (null == knownHashes) {
      return builder;
    }
    for (Hash hash : knownHashes) {
      addIntegrityAttestation(builder, Reference.newBuilder().setHash(hash).build());
    }
    return builder;
  }

  /**
   * Add all known integrity attestations to the parent reference of this RequestIntegrityAttestationInput
   * @param builder a RequestIntegrityAttestationInput.Builder  with a parent element in its SignedChainSlot
   * @return builder, but with the parent reference fleshed out with all known integrity attestations.
   */
  public RequestIntegrityAttestationInput.Builder addIntegrityAttestationsParent(
           RequestIntegrityAttestationInput.Builder builder) {
    addIntegrityAttestations(builder.getPolicyBuilder().getFillInTheBlankBuilder().getSignedChainSlotBuilder().
                              getChainSlotBuilder().getParentBuilder());
    return builder;
  }

  /**
   * Wait to send this request to this destination until the relevant attestations are known. 
   * It's useless to send a request for slot n unless it references an integrity attestation for
   *  the same cryptoId for slot n-1.
   * The exception is of course for slot 0 (root).
   * @param destination the cryptoId of the relevant Fern server
   * @param input the RequestIntegrityAttestationInput (parent reference will be changed to add attestations)
   */
  public void sendWhenReady(CryptoId destination, RequestIntegrityAttestationInput input) {
    // if it's a root block
    if (input.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().getSlot() <= 0) {
      send(destination, input);
    } else { // otherwise, start a new thread, as we may have to wait until the parent attestation arrives.
      // I'm not super happy about doing this in a new thread.
      // I don't know what the overhead of starting a new thread here is.
      // In theory, we could have some kind of pending queue, so when the parent response shows up, the 
      //   sending happens in that thread.
      // However, this is easier to write.
      (new Thread(new SendWhenReady(this, destination, input))).start();
    }
  }

  /**
   * Change the destination cryptoId of this request to match this cryptoid.
   * This assumes that your input is a request with:
   * Policy - FillInTheBlank - SignedChainSlot - Signature - CryptoId (to become destination)
   * @param input the RequestIntegrityAttestationInput 
   * @param cryptoid the destination cryptid
   * @return input altered to use the cryptoid.
   */
  public static RequestIntegrityAttestationInput changeCryptoId(RequestIntegrityAttestationInput input,
                                                                CryptoId cryptoid) {
    RequestIntegrityAttestationInput.Builder builder = RequestIntegrityAttestationInput.newBuilder(input);
    builder.getPolicyBuilder().getFillInTheBlankBuilder().getSignedChainSlotBuilder().getSignatureBuilder().
            setCryptoId(cryptoid);
    return builder.build();
  }

  /**
   * Wait to send this request to each Fern server until the relevant attestations are known. 
   * It's useless to send a request for slot n unless it references an integrity attestation for
   *  the same cryptoId for slot n-1.
   * The exception is of course for slot 0 (root).
   * @param input the RequestIntegrityAttestationInput (parent reference will be changed to add
   *  attestations, and cryptoId will be changed to reflect each destination)
   */
  public void broadcastWhenReady(RequestIntegrityAttestationInput input) {
    for (CryptoId cryptoId : getHandles().keySet()) {
      sendWhenReady(cryptoId, changeCryptoId(input, cryptoId));
    }
  }

  /**
   * Strips a request to a canonicalized form featuring only Hashes for block and root, slot number, and cryptoId.
   * This assumes that your input is a request with:
   * Policy - FillInTheBlank - SignedChainSlot
   * @param input the RequestIntegrityAttestationInput  with possibly extraneous fields
   * @return a canonicalized form featuring only Hashes for block and root, slot number, and cryptoId.
   */
  public static RequestIntegrityAttestationInput stripRequest(RequestIntegrityAttestationInput input) {
    return RequestIntegrityAttestationInput.newBuilder().setPolicy(
             IntegrityPolicy.newBuilder().setFillInTheBlank(
               IntegrityAttestation.newBuilder().setSignedChainSlot(
                 SignedChainSlot.newBuilder().
                   setSignature(
                     Signature.newBuilder().setCryptoId(
                       input.getPolicy().getFillInTheBlank().getSignedChainSlot().getSignature().getCryptoId()
                     )
                   ).
                   setChainSlot(
                     ChainSlot.newBuilder().
                       setBlock(
                         Reference.newBuilder().setHash(
                           input.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().
                             getBlock().getHash()
                         )
                       ).
                       setRoot(
                         Reference.newBuilder().setHash(
                           input.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().
                             getRoot().getHash()
                         )
                       ).
                       setSlot(
                         input.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().getSlot()
                       )
                   )
                 )
               )
             ).build();
  }

  /**
   * Called when a Response comes in for a RequestIntegrityAttestation from a server.
   * Waits until the attestation referenced arrives, and checks it for validity.
   * Adds the attestation to the set of known attestations.
   * Adds the attestation to the known response holder for this request.
   * @param response the response from the wire
   * @param handle represents the client communicating with the server
   * @param request the request to which this is a response
   */
  public void onRequestIntegrityAttestationResponse(final RequestIntegrityAttestationResponse response,
                                                    final AgreementChainClientPerServer handle,
                                                    final RequestIntegrityAttestationInput request) {
    final Block attestation = handle.getClient().checkIntegrityAttestation(request, response);
    if (attestation == null) {
      logger.log(Level.WARNING, "Server seems to have given us an invalid attesation\nREQUEST:\n"+
                                request+"\nRESPONSE:\n"+response);
      return;
    }
    final Hash blockHash = attestation.getIntegrityAttestation().getSignedChainSlot().
                                       getChainSlot().getBlock().getHash();
    knownIntegrityAttestations.putIfAbsent(blockHash, newKeySet());
    knownIntegrityAttestations.get(blockHash).add(response.getReference().getHash());
    final RequestIntegrityAttestationInput index = stripRequest(request);
    knownResponses.putIfAbsent(index, new ConcurrentHolder<Hash>());
    knownResponses.get(index).put(response.getReference().getHash());
  }


  /**
   * A "Handle" representing a client for a chain Fern server.
   * This maintains a thread in which the "actual" client will run, and a blocking queue to send to that thread.
   * The reason for this is that we don't want message send time (including
   *  inability to communicate with server) to slow down our main process, so we
   *  make one of these for each client instead.
   * @author Isaac Sheff
   */
  private class AgreementChainClientPerServer implements Runnable {
    /** the queue of messages to be sent to the server */
    private final BlockingQueue<RequestIntegrityAttestationInput> queue;
    /** represents the server we're talking to */
    private final Contact contact;
    /** the AgreementChain Client of which this is a part */
    private final AgreementChainFernClient chainClient;
    /** communicates with the server */
    private AgreementFernClient client;

    /** 
     * make a new handle for a given chainClient for a given contact.
     * @param chainClient the AgreementChain Client of which this is a part 
     * @param contact represents the server we're talking to 
     */
    private AgreementChainClientPerServer(AgreementChainFernClient chainClient, Contact contact) {
      this.contact = contact;
      this.chainClient = chainClient;
      client = null;
      queue = new LinkedBlockingQueue<RequestIntegrityAttestationInput>();
    }

    /** @return communicates with the server */
    public AgreementFernClient getClient() {return client;};
    /** @return the AgreementChain Client of which this is a part */
    public AgreementChainFernClient getChainClient() {return chainClient;}
    /** @return the queue of messages to be sent to the server */
    private BlockingQueue<RequestIntegrityAttestationInput> getQueue() {return queue;}

    /** @param input the RequestIntegrityAttestationInput to be added to the queue (will be sent over the wire) */
    public void queueMessage(RequestIntegrityAttestationInput input) {
      try {
        getQueue().put(input);
      } catch (InterruptedException e) {
        //TODO: do this right!
      }
    }

    /**
     * Called when the new thread for this handle starts.
     * boots up the client (which may take time because the server may
     *  not communicate well), and then sends each element of the
     *  queue over the wire, waiting if necessary.
     * Each request gets a new AgreementChainClientCallBack to handle what happens when the server sends a response.
     */
    public void run() {
      client = new AgreementFernClient(getNode(), contact); // called in the new thread. might take a while?
      while (true) {
        try {
          final RequestIntegrityAttestationInput request = queue.take();
          client.requestIntegrityAttestation(request, new AgreementChainClientCallBack(this, request));
        } catch (InterruptedException e) {
          //TODO: do this right!
        }
      }
    }
  }

  /**
   * Handles what happens when the server sends a response to a requestIntegrityAttestation.
   * The very short answer is that it calls onRequestIntegrityAttestationResponse.
   * @author Isaac Sheff
   */
  private class AgreementChainClientCallBack implements StreamObserver<RequestIntegrityAttestationResponse> {

    /** Used in shutting down */
    private final CountDownLatch finishLatch;
    /** The handle for sending to the server that originated this response */
    private final AgreementChainClientPerServer handle;
    /** the request for which this is a response */
    private final RequestIntegrityAttestationInput request;

    /**
     * Create a new AgreementChainClientCallBack  to await a server response.
     * This will create a CountDownLatch with 1 second.
     * @param handle  The handle for sending to the server that originated this response 
     * @param request the request for which this is a response
     */
    public AgreementChainClientCallBack (
      final AgreementChainClientPerServer handle,
      final RequestIntegrityAttestationInput request
        ) {
      this.handle = handle;
      this.request = request;
      finishLatch = new CountDownLatch(1);
    }

    /**
     * Each time a new RequestIntegrityAttestationResponse comes in, this is called.
     * It just calls onRequestIntegrityAttestationResponse in the AgreementChainFernClient.
     * @param response the newly arrived RequestIntegrityAttestationResponse from the wire.
     */
    @Override
    public void onNext(RequestIntegrityAttestationResponse response) {
      handle.getChainClient().onRequestIntegrityAttestationResponse(response, handle, request);
    }

    /**
     * Each time something goes wrong with sendBlocks on the wire, this is called.
     * We just log it.
     * @param t the Throwable from gRPC representing whatever went wrong.
     */
    @Override
    public void onError(Throwable t) {
      logger.log(Level.WARNING, "RequestIntegrityAttestation Failed (will retry): ", t);
      try {
        TimeUnit.SECONDS.sleep(5); // wait 5 seconds and try again.
      } catch (InterruptedException e) {
        //TODO: do this right!
      }
      handle.queueMessage(request);
      finishLatch.countDown();
    }

    /** Called when we're done with this request. */
    @Override
    public void onCompleted() {
      finishLatch.countDown();
    }
  }


  /**
   * For use in threads which wait until an appropriate parent attestation is available, then send a request.
   * @see sendWhenReady
   * @author Isaac Sheff
   */
  private class SendWhenReady implements Runnable {
    /** the AgreementChainFernClient of which this is a part */
    private final AgreementChainFernClient client;
    /** the CryptoId of the destination Fern server */
    private final CryptoId destination;
    /** the request to be sent */
    private final RequestIntegrityAttestationInput input;

    /**
     * Make a new SendWhenReady
     * @param client the AgreementChainFernClient of which this is a part 
     * @param destination  the CryptoId of the destination Fern server 
     * @param input  the request to be sent 
     */
    public SendWhenReady(AgreementChainFernClient client,
                         CryptoId destination,
                         RequestIntegrityAttestationInput input) {
      this.input = input;
      this.client = client;
      this.destination = destination;
    }

    /**
     * Called when the thread with this SendWhenReady launches.
     * It will try to get the appropriate parent reference, and when that arrives, it will send the request
     *  (now featuring a reference to the parent attestation) to the Fern server.
     */
    public void run() {
      final RequestIntegrityAttestationInput.Builder parentBuilder =
        RequestIntegrityAttestationInput.newBuilder(input);
      parentBuilder.getPolicyBuilder().getFillInTheBlankBuilder().getSignedChainSlotBuilder().getChainSlotBuilder().
        setSlot(input.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().getSlot() - 1);
      parentBuilder.getPolicyBuilder().getFillInTheBlankBuilder().getSignedChainSlotBuilder().getChainSlotBuilder().
        getBlockBuilder().setHash(input.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().getParent().getHash());
      final RequestIntegrityAttestationInput parentIndex = stripRequest(parentBuilder.build());
      client.getKnownResponses().putIfAbsent(parentIndex, new ConcurrentHolder<Hash>());

      client.getKnownResponses().get(parentIndex).get();
      // we're assuming that if it's in getKnownResponses, it's in knownIntegrityAttestations
      client.send(destination,
        client.addIntegrityAttestationsParent(RequestIntegrityAttestationInput.newBuilder(input)).build());
    }
  }

}
