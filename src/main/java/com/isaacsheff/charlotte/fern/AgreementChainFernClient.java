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

public class AgreementChainFernClient {
  /** Use logger for logging events in this class. */
  private static final Logger logger = Logger.getLogger(AgreementChainFernClient.class.getName());

  private final CharlotteNodeService node;
  private final ConcurrentMap<CryptoId, AgreementChainClientPerServer> handles;
  private final Collection<Thread> handleThreads;
  private final ConcurrentMap<Hash, Set<Hash>> knownIntegrityAttestations;
  private final ConcurrentMap<RequestIntegrityAttestationInput, ConcurrentHolder<Hash>> knownResponses; 

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

  public CharlotteNodeService getNode() {return node;}
  public ConcurrentMap<CryptoId, AgreementChainClientPerServer> getHandles() {return handles;}
  public Collection<Thread> getHandleThreads() {return handleThreads;}
  public ConcurrentMap<RequestIntegrityAttestationInput, ConcurrentHolder<Hash>> getKnownResponses()
    {return knownResponses;} 

  public void broadcast(RequestIntegrityAttestationInput input) {
    for (AgreementChainClientPerServer handle : getHandles().values()) {
      handle.queueMessage(changeCryptoId(input, handle.contact.getCryptoId()));
    }
  }

  public void send(CryptoId destination, RequestIntegrityAttestationInput input) {
    getHandles().get(destination).queueMessage(input);
  }

  public Reference.Builder addIntegrityAttestation(Reference.Builder builder, Reference attestation) {
    for (Reference reference : builder.getIntegrityAttestationsList()) {
      if (reference.getHash().equals(attestation.getHash())) {
        return builder;
      }
    }
    return builder.addIntegrityAttestations(attestation);
  }

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

  public RequestIntegrityAttestationInput.Builder addIntegrityAttestationsParent(
           RequestIntegrityAttestationInput.Builder builder) {
    addIntegrityAttestations(builder.getPolicyBuilder().getFillInTheBlankBuilder().getSignedChainSlotBuilder().
                              getChainSlotBuilder().getParentBuilder());
    return builder;
  }

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

  public static RequestIntegrityAttestationInput changeCryptoId(RequestIntegrityAttestationInput input,
                                                                CryptoId cryptoid) {
    RequestIntegrityAttestationInput.Builder builder = RequestIntegrityAttestationInput.newBuilder(input);
    builder.getPolicyBuilder().getFillInTheBlankBuilder().getSignedChainSlotBuilder().getSignatureBuilder().
            setCryptoId(cryptoid);
    return builder.build();
  }









  public void broadcastWhenReady(RequestIntegrityAttestationInput input) {
    for (CryptoId cryptoId : getHandles().keySet()) {
      sendWhenReady(cryptoId, changeCryptoId(input, cryptoId));
    }
  }

  /**
   * Strips a request to a canonicalized form featuring only Hashes for block and root, slot number, and cryptoId.
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
    logger.info("RESPONSE TO INDEX ADDED TO KNOWN RESPONSES:\n"+index);
    knownResponses.get(index).put(response.getReference().getHash());
  }


  private class AgreementChainClientPerServer implements Runnable {
    private final BlockingQueue<RequestIntegrityAttestationInput> queue;
    private final Contact contact;
    private final AgreementChainFernClient chainClient;
    private AgreementFernClient client;

    private AgreementChainClientPerServer(AgreementChainFernClient chainClient, Contact contact) {
      this.contact = contact;
      this.chainClient = chainClient;
      client = null;
      queue = new LinkedBlockingQueue<RequestIntegrityAttestationInput>();
    }

    public AgreementFernClient getClient() {return client;};
    public AgreementChainFernClient getChainClient() {return chainClient;}
    private BlockingQueue<RequestIntegrityAttestationInput> getQueue() {return queue;}

    public void queueMessage(RequestIntegrityAttestationInput input) {
      try {
        getQueue().put(input);
      } catch (InterruptedException e) {
        //TODO: do this right!
      }
    }

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

  private class AgreementChainClientCallBack implements StreamObserver<RequestIntegrityAttestationResponse> {

    /**
     * Used in shutting down
     */
    private final CountDownLatch finishLatch;
    private final AgreementChainClientPerServer handle;
    private final RequestIntegrityAttestationInput request;

    /**
     * @param finishLatch Used in shutting down
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

    /**
     * Called when we're done with this request.
     */
    @Override
    public void onCompleted() {
      finishLatch.countDown();
    }
  }


  private class SendWhenReady implements Runnable {
    private final AgreementChainFernClient client;
    private final CryptoId destination;
    private final RequestIntegrityAttestationInput input;
    public SendWhenReady(AgreementChainFernClient client,
                         CryptoId destination,
                         RequestIntegrityAttestationInput input) {
      this.input = input;
      this.client = client;
      this.destination = destination;
    }
    public void run() {
      final RequestIntegrityAttestationInput.Builder parentBuilder =
        RequestIntegrityAttestationInput.newBuilder(input);
      parentBuilder.getPolicyBuilder().getFillInTheBlankBuilder().getSignedChainSlotBuilder().getChainSlotBuilder().
        setSlot(input.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().getSlot() - 1);
      parentBuilder.getPolicyBuilder().getFillInTheBlankBuilder().getSignedChainSlotBuilder().getChainSlotBuilder().
        getBlockBuilder().setHash(input.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().getParent().getHash());
      final RequestIntegrityAttestationInput parentIndex = stripRequest(parentBuilder.build());
      client.getKnownResponses().putIfAbsent(parentIndex, new ConcurrentHolder<Hash>());

      // this right here waits. This is why we're in a new thread.
      logger.info("WAITING FOR PARENT RESPOSE TO INDEX:\n"+parentIndex);
      client.getKnownResponses().get(parentIndex).get();
      logger.info("parent known response retrieved ...");
      // we're assuming that if it's in getKnownResponses, it's in knownIntegrityAttestations
      client.send(destination,
        client.addIntegrityAttestationsParent(RequestIntegrityAttestationInput.newBuilder(input)).build());
    }
  }

}
