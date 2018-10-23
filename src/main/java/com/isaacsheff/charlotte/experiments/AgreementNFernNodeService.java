package com.isaacsheff.charlotte.experiments;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.Contact;
import java.nio.file.Path;
import java.util.Map.Entry;

/** 
 * A CharlotteNodeService to be paired with AgreementNFern service, so that it only broadcasts its own attestations.
 * This is useful for cutting down bandwidth in an agreement-based system.
 * (Other services, by default, flood all blocks.)
 * This experiment uses the JsonExperimentConfig.blockSize parameter to determind block payload size.
 * @author Isaac Sheff
 */
public class AgreementNFernNodeService extends CharlotteNodeService {

  /** Raw configuration information for experiments. */
  private final JsonExperimentConfig jsonConfig;

  /**
   * Create a new CharlotteNodeService given an experimental
   *  configuration, and a path to the dir it lives in.
   * These are used to make a Config object.
   * @param jsonConfig the raw experimental confuguration.
   * @param dir the directory it lives in.
   */
  public AgreementNFernNodeService(final JsonExperimentConfig jsonConfig, final Path dir) {
    super(new Config(jsonConfig, dir));
    this.jsonConfig = jsonConfig;
  }

  /**
   * Send this block to all known contacts IFF it is an attestation from this node.
   * Since each contact's sendBlock function is nonblocking, this will be done in parallel.
   * @param block the block to send
   */
  @Override
  public void broadcastBlock(final Block block) {
    // If this is an integrity attestation I've signed
    if (block.hasIntegrityAttestation()
        && block.getIntegrityAttestation().hasSignedChainSlot()
        && block.getIntegrityAttestation().getSignedChainSlot().hasSignature()
        && block.getIntegrityAttestation().getSignedChainSlot().getSignature().hasCryptoId()
        && getConfig().getCryptoId().equals(
             block.getIntegrityAttestation().getSignedChainSlot().getSignature().getCryptoId())) {
      // send it to people who are not Fern servers
      for (Entry<String, Contact> entry : getConfig().getContacts().entrySet()) {
        if (!jsonConfig.getFernServers().contains(entry.getKey())) {
          entry.getValue().getCharlotteNodeClient().sendBlock(block);
        }
      }
    }
  }
}
