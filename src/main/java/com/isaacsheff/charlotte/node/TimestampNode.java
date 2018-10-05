package com.isaacsheff.charlotte.node;


import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.concurrent.ConcurrentHashMap.newKeySet;

import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.collections.BlockingMap;
import com.isaacsheff.charlotte.fern.TimestampFern;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.proto.IntegrityAttestation;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.SignedTimestampedReferences;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.TimestampedReferences;
import com.isaacsheff.charlotte.proto.IntegrityPolicy;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationResponse;
import com.isaacsheff.charlotte.proto.SendBlocksResponse;
import com.isaacsheff.charlotte.proto.Signature;
import com.isaacsheff.charlotte.yaml.Config;

/**
 * A gRPC service for the Charlotte API.
 * gRPC separates "service" from "server."
 * One Server can run multiple Services.
 * This is a Service implementing the charlotte gRPC API.
 * It can be extended for more interesting implementations.
 *
 * The TimestampNode differs from the regular CharlotteNodeService in
 *  that, when it has received referencesPerAttestation" new blocks,
 *  it requests a timestamp from the local fern service for those new
 *  blocks.
 *
 * @author Isaac Sheff
 */
public class TimestampNode extends CharlotteNodeService {
  /** Use logger for logging events on a CharlotteNodeService. */
  private static final Logger logger = Logger.getLogger(TimestampNode.class.getName());

  /** a local timestamping service **/
  private final TimestampFern fern;

  /** recently received blocks which are not yet timestamped **/
  private final Set<Reference.Builder> untimestamped;

  /** how many blocks do we want per timestamp? **/
  private int referencesPerAttestation;

  /**
   * Create a new service with the given map of blocks, and the given map of addresses.
   * No input is checked for correctness.
   * @param referencesPerAttestation the number of blocks we want per timestamp we auto-request
   * @param fern the local timestamping service
   * @param blockMap a map of known hashes and blocks
   * @param config the Configuration settings for this Service
   */
  public TimestampNode(final int referencesPerAttestation, 
                       final TimestampFern fern,
                       final BlockingMap<Hash, Block> blockMap,
                       final Config config) {
    super(blockMap, config);
    this.fern = fern;
    untimestamped = newKeySet();
    this.referencesPerAttestation = referencesPerAttestation;
  }

  /**
   * Create a new service with an empty map of blocks and an empty map of addresses.
   * @param referencesPerAttestation the number of blocks we want per timestamp we auto-request
   * @param fern the local timestamping service
   * @param config the Configuration settings for this Service
   */
  public TimestampNode(final int referencesPerAttestation, final TimestampFern fern, final Config config) {
    super(config);
    this.fern = fern;
    untimestamped = newKeySet();
    this.referencesPerAttestation = referencesPerAttestation;
  }

  /**
   * Create a new service with an empty map of blocks and an empty map of addresses, parse configuration.
   * @param referencesPerAttestation the number of blocks we want per timestamp we auto-request
   * @param fern the local timestamping service
   * @param path the file path for the configuration file
   */
  public TimestampNode(final int referencesPerAttestation, final TimestampFern fern, final Path path) {
    super(path);
    this.fern = fern;
    untimestamped = newKeySet();
    this.referencesPerAttestation = referencesPerAttestation;
  }

  /**
   * Create a new service with an empty map of blocks and an empty map of addresses, parse configuration.
   * @param referencesPerAttestation the number of blocks we want per timestamp we auto-request
   * @param fern the local timestamping service
   * @param filename the file name for the configuration file
   */
  public TimestampNode(final int referencesPerAttestation, final TimestampFern fern, final String filename) {
    super(filename);
    this.fern = fern;
    untimestamped = newKeySet();
    this.referencesPerAttestation = referencesPerAttestation;
  }

  /**
   * Called after a new block has been received, and set to be broadcast to all other nodes.
   * This is where we count up the recently received blocks, and if
   *  referencesPerAttestation have accrued, we request a timestamp
   *  for them, and clear out the untimestamped set.
   * @param block the newly received block
   * @return any SendBlockResponses (including error messages) to be sent back over the wire to the block's sender.
   */
  @Override
  public Iterable<SendBlocksResponse> afterBroadcastNewBlock(final Block block) {
    boolean shouldRequestAttestation = false;
    final TimestampedReferences.Builder references = TimestampedReferences.newBuilder();
    untimestamped.add(Reference.newBuilder().setHash(sha3Hash(block)));
    synchronized(untimestamped) {
      if (untimestamped.size() >= referencesPerAttestation) {
        shouldRequestAttestation = true;
        for (Reference.Builder reference : untimestamped) {
          references.addBlock(reference);
        }
        untimestamped.clear();
      }
    }
    if (shouldRequestAttestation) {
      final RequestIntegrityAttestationResponse response = fern.requestIntegrityAttestation(
          RequestIntegrityAttestationInput.newBuilder().setPolicy(
            IntegrityPolicy.newBuilder().setFillInTheBlank(
              IntegrityAttestation.newBuilder().setSignedTimestampedReferences(
                SignedTimestampedReferences.newBuilder().
                  setSignature(
                    Signature.newBuilder().setCryptoId(getConfig().getCryptoId())). // signed by me
                  setTimestampedReferences(references)))).build());
      if (!response.getErrorMessage().equals("")) {
        return singleton(SendBlocksResponse.newBuilder().setErrorMessage(
                 "Problem while getting attestation for latest batch of blocks:\n"+
                 response.getErrorMessage()).build());
      }
    }
    return emptySet();
  }
}
