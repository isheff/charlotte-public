package com.isaacsheff.charlotte.experiments;

import static com.isaacsheff.charlotte.fern.AgreementFernClient.checkAgreementIntegrityAttestation;
import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.isaacsheff.charlotte.fern.AgreementFernClient;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.LogHashService;
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
import com.isaacsheff.charlotte.yaml.Contact;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * For running an experiment with Agreement Fern servers that demand N
 *  attestations before committing a block.
 * This will append blocks to the chain, waiting to broadcast and
 *  request attestations for each until the previous is committed.
 * This experiment uses the JsonExperimentConfig.blockSize parameter to determind block payload size.
 * @author Isaac Sheff
 */
public class AgreementNClient {
  /** used for logging events in this class **/
  private static final Logger logger = Logger.getLogger(AgreementNClient.class.getName());

  /** The local CharlotteNodeService (used for sending blocks) */
  private final CharlotteNodeService service;

  /** The experiment Config file **/
  private final JsonExperimentConfig config;

  /** For each fern, a Queue of requests to be sent out to that Fern (in case sending takes time) **/
  private final Map<CryptoId, BlockingQueue<RequestIntegrityAttestationInput>> requestQueues;

  /** For each fern, the hash of the attestation (if known) for the most recent block */
  private final Map<CryptoId, Hash> knownAttestations;

  /** The number of attestations required for each block to commit */
  private final int threshold;

  /** The total number of blocks we'll be sending out **/
  private final int totalBlocks;

  /** The blocks we'll be appending to the chain **/
  private final Block[] blocks;

  /** The hash of the root block (literally sha3Hash(blocks[0])) **/
  private final Hash rootHash;

  /** used to wait until we're done */
  private final Object doneLock;

  /** The current slot number we've just tried to append to the chain (MUTABLE) **/
  private int currentSlot;

  
  /** Fills in the blocks array with boring, but distinct, blocks */
  protected void fillBlocks() {
    getBlocks()[0] = Block.newBuilder().setStr("block content 0").build();
    for (int i = 1; i < getBlocks().length; ++i) {
      getBlocks()[i] = Block.newBuilder().setStr(randomString(getJsonConfig().getBlocksize())).build();
    }
  }


  /**
   * Start up a new client.
   * This does not initiate the experiment.
   * To initiate the experiment, broadcast block 0 (root).
   * @param service the local CharlotteNodeService (for sendign blocks and such)
   * @param config the experimental config.
   */
  public AgreementNClient(final CharlotteNodeService service, final JsonExperimentConfig config) {
    this.service = service;
    this.config = config;
    doneLock = new Object();
    totalBlocks = 1 + (2 * config.getBlocksPerExperiment());
    blocks = new Block[totalBlocks];
    fillBlocks();
    rootHash = sha3Hash(blocks[0]);
    threshold = (2 * config.getFernServers().size()) / 3;
    currentSlot = 0;
    knownAttestations = new ConcurrentHashMap<CryptoId, Hash>(config.getFernServers().size());
    requestQueues = new ConcurrentHashMap<CryptoId, BlockingQueue<RequestIntegrityAttestationInput>>();

    // For each queue, start a thread that just takes from the queue, and sends the request via the client.
    // The response is handled asynchronously by the AgreementNObserver, which just calls onFernResponse.
    for (String fern : config.getFernServers()) {
      final Contact contact = service.getConfig().getContact(fern);
      final AgreementFernClient client = new AgreementFernClient(service, contact);
      final BlockingQueue<RequestIntegrityAttestationInput> queue =
        new LinkedBlockingQueue<RequestIntegrityAttestationInput>();
      (new Thread(() -> {
        RequestIntegrityAttestationInput input;
        while (true) {
          try {
            input = queue.take();
            client.requestIntegrityAttestation(input, new AgreementNObserver(this, input));
          } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Interrupted while trying to send to Fern", e);
          }
        }
      },
      "RequestIntegrityAttestationInput_" + fern
      )).start();
      requestQueues.put(contact.getCryptoId(), queue);
    }
  }


  /** @return The local CharlotteNodeService (used for sending blocks) */
  public CharlotteNodeService getService() {return service;}
  
  /** @return The experiment Config file **/
  public JsonExperimentConfig getJsonConfig() {return config;}

  /** @return For each fern, a Queue of requests to be sent out to that Fern (in case sending takes time) **/
  public Map<CryptoId, BlockingQueue<RequestIntegrityAttestationInput>> getRequestQueues() {return requestQueues;}

  /** @return The total number of blocks we'll be sending out **/
  public int getTotalBlocks() {return totalBlocks;}

  /** @return The blocks we'll be appending to the chain **/
  public Block[] getBlocks() {return blocks;}

  /** @return The hash of the root block (literally sha3Hash(blocks[0])) **/
  public Hash getRootHash() {return rootHash;}

  /** 
   * Send a request to all the clients (and so to all the fern servers).
   * This will broadcast the block from blocks[slot], and then enqueue requests built for each fern.
   * @param parentBuilder represents the reference to the parent block
   * @param slot the slot number of this new block
   */
  public void broadcastRequest(final Reference.Builder parentBuilder, final int slot) {
    if (slot >= totalBlocks) {
      done(); // we've finished all the blocks, and we're done.
      return; // unreachable, I'm pretty sure
    }
    logger.info("Beginning slot " + slot);
    getService().onSendBlocksInput(getBlocks()[slot]); // send out the block the attestations reference
    RequestIntegrityAttestationInput.Builder builder = RequestIntegrityAttestationInput.newBuilder().setPolicy(
            IntegrityPolicy.newBuilder().setFillInTheBlank(
              IntegrityAttestation.newBuilder().setSignedChainSlot(
                SignedChainSlot.newBuilder().setChainSlot(
                  ChainSlot.newBuilder().
                    setBlock(Reference.newBuilder().setHash(sha3Hash(getBlocks()[slot]))).
                    setRoot(Reference.newBuilder().setHash(getRootHash())).
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

  /** To be called when the experiment is complete. **/
  public void done() {
    synchronized(doneLock) {
      doneLock.notifyAll();
    }
  }

  /**  Wait until the experiment is done */
  public void waitUntilDone() {
    while (true) {
      try {
        synchronized(doneLock) {
          doneLock.wait();
          return;
        }
      } catch (InterruptedException e) {
        logger.log(Level.SEVERE, "Interrupted while waiting to finish experiment", e);
      }
    }
  }

  /**
   * Called whenever a Fern server responds to a request.
   * This checks to see if the attestation referenced is legit, and then if it
   *  is, checks if we have enough attestations for the current block.
   * If we do, it broadcasts the request for the next block.
   * @param response the response from the Fern server off the wire.
   */
  public void onFernResponse(final RequestIntegrityAttestationResponse response,
                             final RequestIntegrityAttestationInput request) {
    if (!(response.hasReference() && response.getReference().hasHash())) {
      try { // re-send the request
        requestQueues.get(request.getPolicy().getFillInTheBlank().getSignedChainSlot().getSignature().
                          getCryptoId()).put(request);
      } catch (InterruptedException e) {
        logger.log(Level.SEVERE, "interrupted while enqueuing to send to fern", e);
      }
      return;
    }
    final Block block = getService().getBlock(response.getReference().getHash()); // blocking
    if (block.hasIntegrityAttestation()
        && block.getIntegrityAttestation().hasSignedChainSlot()
        && (block.equals(checkAgreementIntegrityAttestation(block))) // signature verification and such
        && rootHash.equals(block.getIntegrityAttestation().getSignedChainSlot().getChainSlot().getRoot().getHash())
        && currentSlot == block.getIntegrityAttestation().getSignedChainSlot().getChainSlot().getSlot()
        ) {
      Reference.Builder parentBuilder = null;
      int newSlot = 0;
      synchronized (knownAttestations) { // as usual, I regret having to explicitly syncronize anything
        // if we're still (now that we're in the synchronized block) on the same slot...
        if (currentSlot == block.getIntegrityAttestation().getSignedChainSlot().getChainSlot().getSlot()) {
          knownAttestations.put(
            block.getIntegrityAttestation().getSignedChainSlot().getSignature().getCryptoId(),
            sha3Hash(block));
          if (knownAttestations.size() > threshold) {
            parentBuilder = Reference.newBuilder().setHash(sha3Hash(blocks[currentSlot]));
            for (Hash hash : knownAttestations.values()) {
              parentBuilder.addIntegrityAttestations(Reference.newBuilder().setHash(hash));
            }
            ++currentSlot;
            newSlot = currentSlot;
            knownAttestations.clear();
          }
        }
      }
      if (parentBuilder != null) {
        broadcastRequest(parentBuilder, newSlot);
      }
    }
  }

  /** Creates a random alpha-numeric string of the length given. */
  public static String randomString(final int targetStringLength) {
    final int leftLimit = 97; // letter 'a'
    // final int rightLimit = 122; // letter 'z'
    // final int modulus = (rightLimit - leftLimit + 1);
    final int modulus = 26;
    final Random random = new Random();
    final StringBuilder buffer = new StringBuilder(targetStringLength);
    for (int i = 0; i < targetStringLength; i++) {
        buffer.append((char) (leftLimit + (int) (random.nextFloat() * modulus)));
    }
    return(buffer.toString());
  }

  /**
   * Run the experiment.
   * This starts up a client, and then sends block 0 (root), and then waits for the client to finish.
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
    final CharlotteNodeService service = new LogHashService(args[0]);
    (new Thread(new CharlotteNode(service))).start();
    final AgreementNClient client = new AgreementNClient(service, jsonConfig);

    TimeUnit.SECONDS.sleep(1); // wait for servers to start up
    client.broadcastRequest(Reference.newBuilder(), 0); // send out the root block
    client.waitUntilDone();
    System.exit(0);
  }
}
