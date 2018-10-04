package com.isaacsheff.charlotte.experiments;

import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
import static java.util.concurrent.ConcurrentHashMap.newKeySet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.AvailabilityAttestation;
import com.isaacsheff.charlotte.proto.AvailabilityAttestation.SignedStoreForever;
import com.isaacsheff.charlotte.proto.AvailabilityAttestation.StoreForever;
import com.isaacsheff.charlotte.proto.AvailabilityPolicy;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.proto.IntegrityAttestation;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.ChainSlot;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.SignedChainSlot;
import com.isaacsheff.charlotte.proto.IntegrityPolicy;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestAvailabilityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestAvailabilityAttestationResponse;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.Signature;
import com.isaacsheff.charlotte.wilbur.WilburClient;
import com.isaacsheff.charlotte.yaml.Contact;

/**
 * Client for the AgreementNW experiment, which gathers N fern
 *  attestations, and some Wilbur attestations, before each block.
 * The objective of this experiment is to demonstrate that we can save
 *  bandwidth by storing large blocks only on Wilbur servers, and then
 *  getting Fern servers to agree only on hashes.
 * The number of wilbur servers representing "sufficiently available"
 *  for a block is set in the JsonExperimentConfig.
 * This experiment uses the JsonExperimentConfig.blockSize parameter to determind block payload size.
 * @author Isaac Sheff
 */
public class AgreementNWClient extends AgreementNClient {
  /** used for logging events in this class **/
  private static final Logger logger = Logger.getLogger(AgreementNWClient.class.getName());

  /** For each wilbur, a Queue of requests to be sent out to that wilbur (in case sending takes time) **/
  private final Map<CryptoId, BlockingQueue<RequestAvailabilityAttestationInput>> wilburQueues;

  /**
   * The set of availability attestations known for each block.
   * The existence of an entry in this map (even the empty set)
   *  indicates that this client has already requested availability
   *  attestations for this block.
   */
  private final Map<Hash, Set<Hash>> pendingRefs;

  /**
   * Start up a new client.
   * This does not initiate the experiment.
   * To start the experiment, you have to broadcast block 0 (root).
   * @param service the local CharlotteNodeService (for sendign blocks and such)
   * @param config the experimental config.
   */
  public AgreementNWClient(final CharlotteNodeService service, final JsonExperimentConfig config) {
    super(service, config);
    wilburQueues = new ConcurrentHashMap<CryptoId, BlockingQueue<RequestAvailabilityAttestationInput>>();
    pendingRefs = new ConcurrentHashMap<Hash, Set<Hash>>();

    // For each queue, start a thread that just takes from the queue, and sends the request via the client.
    // The response is handled asynchronously by the AgreementNObserver, which just calls onFernResponse.
    for (String wilbur : config.getWilburServers()) {
      final Contact contact = service.getConfig().getContact(wilbur);
      final WilburClient client = new WilburClient(service, contact);
      final BlockingQueue<RequestAvailabilityAttestationInput> queue =
        new LinkedBlockingQueue<RequestAvailabilityAttestationInput>();
      (new Thread(() -> {
        RequestAvailabilityAttestationInput input;
        while (true) {
          try {
            input = queue.take();
            client.requestAvailabilityAttestation(input, new AgreementNWObserver(this, client, input));
          } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Interrupted while trying to send to Fern", e);
          }
        }
      })).start();
      wilburQueues.put(contact.getCryptoId(), queue);
    }
  }


  /** 
   * Send a request to all the server clients (and so to all the wilbur servers, and then the fern servers).
   * This will broadcast the block from blocks[slot], and then enqueue requests built for each fern.
   * @param parentBuilder represents the reference to the parent block
   * @param slot the slot number of this new block
   */
  @Override
  public void broadcastRequest(final Reference.Builder parentBuilder, final int slot) {
    logger.info("Beginning slot " + slot);
    if (slot >= getTotalBlocks()) {
      done(); // we've finished all the blocks, and we're done.
      return; // unreachable, I'm pretty sure
    }
    if (slot < 0) {
      // For reasons unknown ( https://github.com/isheff/charlotte-java/issues/5 ),
      // Wilbur servers have to flood the root block.
      // Therefore we broadcast it here, since it will be broadcast anyway.
      getService().onSendBlocksInput(getBlocks()[slot]); // send out the block the attestations reference
    } else { // otherwise, send blocks only to the Wilbur servers
      for (String wilbur : getJsonConfig().getWilburServers()) {
        getService().sendBlock(wilbur, getBlocks()[slot]);
      }
    }
    final Hash blockHash = sha3Hash(getBlocks()[slot]);
    final Set<Hash> hashesToBeMadeAvailable = newKeySet();
    hashesToBeMadeAvailable.add(blockHash);
    hashesToBeMadeAvailable.add(getRootHash());
    for (Reference r : parentBuilder.getIntegrityAttestationsList()) {
      hashesToBeMadeAvailable.add(r.getHash());
    }
    makeAvailable(hashesToBeMadeAvailable);
    for (Reference.Builder r : parentBuilder.getIntegrityAttestationsBuilderList()) {
      r.addAllAvailabilityAttestations(pendingRefs.get(r.getHash()));
    }
    final Reference.Builder blockReference =
      Reference.newBuilder().setHash(blockHash).addAllAvailabilityAttestations(pendingRefs.get(blockHash));
    final Reference.Builder rootReference = Reference.newBuilder().setHash(getRootHash()).
                                              addAllAvailabilityAttestations(pendingRefs.get(getRootHash()));
    RequestIntegrityAttestationInput.Builder builder = RequestIntegrityAttestationInput.newBuilder().setPolicy(
            IntegrityPolicy.newBuilder().setFillInTheBlank(
              IntegrityAttestation.newBuilder().setSignedChainSlot(
                SignedChainSlot.newBuilder().setChainSlot(
                  ChainSlot.newBuilder().
                    setBlock(blockReference).
                    setRoot(rootReference).
                    setSlot(slot).
                    setParent(parentBuilder)
                )
              )
            )
          );
    // for each fern, the request specifies a CryptoId
    for (Entry<CryptoId, BlockingQueue<RequestIntegrityAttestationInput>> entry : getRequestQueues().entrySet()){
      builder.getPolicyBuilder().getFillInTheBlankBuilder().getSignedChainSlotBuilder().getSignatureBuilder().
              setCryptoId(entry.getKey());
      try {
        entry.getValue().put(builder.build());
      } catch (InterruptedException e) {
        logger.log(Level.SEVERE, "interrupted while enqueuing to send to fern", e);
      }
    }
  }



  /**
   * Make the blocks with hashes given sufficiently available. 
   * Here, that means sending requests to all the Wilbur servers, and
   *  awaiting enough responses.
   * If we already have responses, we won't make a new request, and
   *  we'll just return immediately.
   * This will block until there are enough responses.
   * @param hashes the hashes of the blocks for which we want Wilbur attestations.
   */
  public void makeAvailable(final Set<Hash> hashes) {
    final Set<Hash> unRequested = newKeySet();
    for (Hash hash : hashes) {
      final Set<Hash> existing = pendingRefs.putIfAbsent(hash, newKeySet());
      if (existing == null) {
        unRequested.add(hash);
      }
    }
    broadcastWilburRequest(unRequested);
    for (Hash hash : hashes) {
      final Set<Hash> pending = pendingRefs.get(hash);
      if (pending.size() <= getJsonConfig().getWilburThreshold()) {
        synchronized(pending) {
          while (pending.size() <= getJsonConfig().getWilburThreshold()) {
            try {
              pending.wait();
            } catch (InterruptedException e) {
              logger.log(Level.SEVERE, "interrupted while waiting for pending availability attestations", e);
            }
          }
        }
      }
    }
  }

  /**
   * Send a request to all the wilbur servers to attest to all these blocks being available.
   * @param hashes the hashes of blocks we want available.
   */
  private void broadcastWilburRequest(Set<Hash> hashes) {
    if (hashes.size() == 0) {
      return;
    }
    final StoreForever.Builder storeForeverBuilder = StoreForever.newBuilder();
    for (Hash hash : hashes) {
      storeForeverBuilder.addBlock(Reference.newBuilder().setHash(hash));
    }
    final RequestAvailabilityAttestationInput.Builder requestBuilder =
            RequestAvailabilityAttestationInput.newBuilder();
    requestBuilder.setPolicy(AvailabilityPolicy.newBuilder().setFillInTheBlank(
      AvailabilityAttestation.newBuilder().setSignedStoreForever(
        SignedStoreForever.newBuilder().
          setStoreForever(storeForeverBuilder).
          setSignature(Signature.newBuilder())
        )
      )
    );
    for (Entry<CryptoId, BlockingQueue<RequestAvailabilityAttestationInput>> entry : wilburQueues.entrySet()) {
      requestBuilder.getPolicyBuilder().getFillInTheBlankBuilder().getSignedStoreForeverBuilder().
                     getSignatureBuilder().setCryptoId(entry.getKey());
      sendWilburRequest(requestBuilder.build(), entry.getValue());
    }
  }

  /**
   * Send a particular requet to a particular wilbur server, as represented by an asynchronous blocking queue.
   * @param input the input to be sent to the Wilbur server.
   * @param wilbur the asynchronous blocking queue of stuff to be sent to a particular server.
   */
  private void sendWilburRequest(final RequestAvailabilityAttestationInput input,
                                 final BlockingQueue<RequestAvailabilityAttestationInput> wilbur) {
    try {
      wilbur.put(input);
    } catch (InterruptedException e) {
      logger.log(Level.SEVERE, "interrupted while queueing wilbur request", e);
    }
  }



  /**
   * This is called when a wilbur server responds to a request.
   * @param input the request sent to the wilbur service
   * @param response the response the wilbur server sent back
   * @param wilburClient the client that communicates with that Wilbur service.
   */
  public void onWilburResponse(final RequestAvailabilityAttestationInput input,
                               final RequestAvailabilityAttestationResponse response,
                               final WilburClient wilburClient) {
    final Block availabilityAttestation = wilburClient.checkAvailabilityAttestation(response);
    if (availabilityAttestation == null) {
      // repeat request
      sendWilburRequest(input, wilburQueues.get(wilburClient.getContact().getCryptoId()));
      return;
    }
    final Hash attestationHash = sha3Hash(availabilityAttestation);
    for (Reference reference : availabilityAttestation.getAvailabilityAttestation().getSignedStoreForever().
                               getStoreForever().getBlockList()) {
      final Set<Hash> pending = pendingRefs.get(reference.getHash());
      pending.add(attestationHash);
      synchronized(pending) {
        if (pending.size() > getJsonConfig().getWilburThreshold()) {
          pending.notifyAll();
        }
      }
    }
  }

  /**
   * Run the experiment.
   * Thi ssets up the AgreementNWclient, broacasts the root block, and waits until it's done.
   * @param args command line arguments args[0] must be the config yaml file.
   */
  public static void main(String[] args) throws InterruptedException, FileNotFoundException, IOException {
    // start the client's CharlotteNode
    if (args.length < 1) {
      logger.log(Level.SEVERE, "no config file name given as argument");
      return;
    }
    final JsonExperimentConfig jsonConfig =
      (new ObjectMapper(new YAMLFactory())).readValue(Paths.get(args[0]).toFile(), JsonExperimentConfig.class);
    final CharlotteNodeService service = new CharlotteNodeService(args[0]);
    (new Thread(new CharlotteNode(service))).start();
    final AgreementNClient client = new AgreementNWClient(service, jsonConfig);

    TimeUnit.SECONDS.sleep(1); // wait for servers to start up
    client.broadcastRequest(Reference.newBuilder(), 0); // send out the root block
    client.waitUntilDone();
    System.exit(0);
  }
}
