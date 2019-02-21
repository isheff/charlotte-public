package com.isaacsheff.charlotte.fern;

import static com.isaacsheff.charlotte.node.SignatureUtil.checkSignature;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationResponse;
import com.isaacsheff.charlotte.yaml.Contact;

/**
 * A Client for a TimestampFern.
 * This client needs a local CharlotteNodeService to get blocks for it.
 * @author Isaac Sheff
 */
public class TimestampClient extends AgreementFernClient {
  /** Use logger for logging events on this class. */
  private static final Logger logger = Logger.getLogger(TimestampClient.class.getName());

  /**
   * Make a new TimestampFernClient for a specific TimestampFern server.
   * This will attempt to open a channel of communication.
   * @param localService a CharlotteNodeService which can be used to receive blocks
   * @param contact the Contact representing the server.
   */
  public TimestampClient(final CharlotteNodeService localService, final Contact contact) {
    super(localService, contact);
  }


  /**
   * Check whether this Block contains a valid IntegrityAttestation.
   * Checks for a valid signature in a properly formatted SignedChainSlot.
   * @param attestation the block we're hoping contains the IntegrityAttestation
   * @return the Block input if it's valid, null otherwise.
   */
  @Override
  public Block checkIntegrityAttestation(final Block attestation) {
    if (!attestation.hasIntegrityAttestation()) {
      logger.log(Level.WARNING, "Response from Fern Server referenced block which is not an Integrity Attestation:\n" +
                                attestation);
      return null;
    }
    if (!attestation.getIntegrityAttestation().hasSignedTimestampedReferences()) {
      logger.log(Level.WARNING, "Response from Fern Server referenced block which is not a SignedTimestampedReferences:\n" +
                                attestation);
      return null;
    }
    if (!attestation.getIntegrityAttestation().getSignedTimestampedReferences().hasTimestampedReferences()) {
      logger.log(Level.WARNING, "Response from Fern Server referenced block which has no TimestampedReferences:\n" +
                                attestation);
      return null;
    }
    if (!attestation.getIntegrityAttestation().getSignedTimestampedReferences().hasSignature()) {
      logger.log(Level.WARNING, "Response from Fern Server referenced block which has no Signature:\n" +
                                attestation);
      return null;
    }
    if (!checkSignature(attestation.getIntegrityAttestation().getSignedTimestampedReferences().getTimestampedReferences(),
                        attestation.getIntegrityAttestation().getSignedTimestampedReferences().getSignature())) {
      logger.log(Level.WARNING, "Response from Fern Server referenced block with an incorrect signature:\n" +
                                attestation);
      return null;
    }
    return attestation;
  }

  /**
   * Check whether this Response references a valid IntegrityAttestation matching the request.
   * Fetches the block from our local node.
   * This may wait until the block referenced is received.
   * Checks for a valid signature in a properly formatted SignedChainSlot.
   * Checks that the Attestation is for the same ChainSlot, and the same CryptoId, as the request.
   * @param request the request we sent to the Fern server
   * @param response references the block we're hoping contains the IntegrityAttestation
   * @return the Integrity Attestation Block input if it's valid, null otherwise.
   */
  public Block checkIntegrityAttestation(final RequestIntegrityAttestationInput request,
                                         final RequestIntegrityAttestationResponse response) {
     final Block attestation = checkIntegrityAttestation(response);
     // by this point, the attestation itself is verified, so we know it has a chain slot and a valid signature and such.
     if (attestation == null) {
       return null;
     }
     if (!attestation.getIntegrityAttestation().getSignedTimestampedReferences().getTimestampedReferences().getBlockList().containsAll(
        request.getPolicy().getFillInTheBlank().getSignedTimestampedReferences().getTimestampedReferences().getBlockList())) {
       logger.log(Level.WARNING, "Response from Fern Server did not reference all the blocks we wanted timestamped."+
                                 "\nATTESTATION:\n"+attestation+
                                 "\nREQUEST:\n"+request);
       return null;
     }
     if (!attestation.getIntegrityAttestation().getSignedTimestampedReferences().getSignature().getCryptoId().equals(
        request.getPolicy().getFillInTheBlank().getSignedTimestampedReferences().getSignature().getCryptoId())) {
       logger.log(Level.WARNING, "Response from Fern Server referenced block different CryptoId than requested:" +
                                 "\nATTESTATION:\n"+attestation+
                                 "\nREQUEST:\n"+request);
       return null;
     }
     return attestation;
  }
}
