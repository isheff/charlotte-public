package com.isaacsheff.charlotte.experiments;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.Contact;
import java.nio.file.Path;
import java.util.Map.Entry;

public class AgreementNFernNodeService extends CharlotteNodeService {

  private final JsonExperimentConfig jsonConfig;

  public AgreementNFernNodeService(final JsonExperimentConfig jsonConfig, final Path dir) {
    super(new Config(jsonConfig, dir));
    this.jsonConfig = jsonConfig;
  }

  /**
   * Send this block to all known contacts.
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
