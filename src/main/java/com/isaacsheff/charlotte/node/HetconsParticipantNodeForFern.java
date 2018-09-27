package com.isaacsheff.charlotte.node;

import static java.util.concurrent.ConcurrentHashMap.newKeySet;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.isaacsheff.charlotte.fern.HetconsFern;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.Config;

import com.xinwenwang.hetcons.HetconsParticipantService;
import com.xinwenwang.hetcons.config.HetconsConfig;

/**
 * A small extension of the HetconsParticipantService designed to forward consensus decisions to the HetconsFern service.
 * It also tracks which 2B message blocks go with which proposals, so those can be referenced.
 * @author Isaac Sheff
 */
public class HetconsParticipantNodeForFern extends HetconsParticipantService {

  /** Use logger for logging events in this class. */
  private static final Logger logger = Logger.getLogger(HetconsParticipantNodeForFern.class.getName());

  /** The HetconsConfig affiliated with this consensus **/
  private final HetconsConfig hetconsConfig;

  /** The 2bs we've received affiliated with each proposal **/
  private final ConcurrentMap<HetconsProposal, Set<Block>> reference2bsPerProposal;

  /** The HetconsFern Service affiliated with this node **/
  private HetconsFern fern;

  /**
   * A very minor extension of HetconsParticipantService designed to integrate with HetconsFern.
   * This basically just forwards onDecision calls to the Fern service.
 * It also tracks which 2B message blocks go with which proposals, so those can be referenced.
   * @param config the configuration file for the CharlotteNodeService
   * @param hetconsConfig the configuration for the Hetcons.
   * @param fern the Fern Service
   */
  public HetconsParticipantNodeForFern(final Config config,
                                       final HetconsConfig hetconsConfig,
                                       final HetconsFern fern) {
    super(config);
    this.hetconsConfig = hetconsConfig;
    this.fern = fern;
    this.reference2bsPerProposal = new ConcurrentHashMap<HetconsProposal, Set<Block>>();
  }

  /**
   * Reset the Fern service affiliated with this node (SHOULD ONLY BE CALLED RIGHT AFTER CONSTRUCTION).
   * @param newFern the new HetconsFern service to be affiliated with this node.
   * @return The HetconsFern Service that USED TO BE affiliated with this node 
   */
  public HetconsFern setFern(HetconsFern newFern) {
    final HetconsFern oldFern = fern;
    fern = newFern;
    if (oldFern != null) {
      logger.log(Level.WARNING,
        "HetconsParticipantNodeForFern re-assigned HetconsFern field, "+
        "which probably shouldn't happen.\nOLD FERN:\n"+
         oldFern+"\nNEW FERN:\n"+newFern);
    }
    return oldFern;
  }

  /** @return The HetconsFern Service affiliated with this node **/
  public HetconsFern getFern() {return fern;}

  /** @return The HetconsConfig affiliated with this consensus **/
  public HetconsConfig getHetconsConfig() {return hetconsConfig;}

  /** @return The 2bs we've received affiliated with each proposal **/
  public ConcurrentMap<HetconsProposal, Set<Block>> getReference2bsPerProposal() {
    return reference2bsPerProposal;
  }

  /**
   * TODO: Make sure this is doing it right!.
   * Invoked whenever an observer reaches a decision.
   * Note that this may be called multiple times for the same consensus, as more 2bs arrive.
   * Calls HetconsFern.observersDecide for all the observers in quora, with value and proposal of message2b.
   * @param quora The quora satisfied by the 2b messages known.
   * @param statis the HetconsStatus for this decision.
   * @param message2b the actual message that triggered this decision.
   * @param id the CryptoId of the sender of the most recent 2b.
   */
  @Override
  protected void onDecision(final HetconsObserverQuorum quora,
                            final Collection<Reference> quorum2b) {

    getFern().observersDecide(quora, quorum2b);
  }

  /**
   * If this is a 2B, we store it away with the associated proposal.
   * Then we pass it along (whether or not it was a 2B).
   * @param input the SendBlocksInput received over the wire
   * @return any responses, in this case just forwarded from HetconsParticipantService.onSendBlocksInput
   */
  @Override
  public Iterable<SendBlocksResponse> onSendBlocksInput(SendBlocksInput input) {
    if (input.hasBlock()
        && input.getBlock().hasHetconsMessage()) {
//       final Set<Block> newM2bSet = newKeySet();
//       final Set<Block> m2bsKnownForThisHash = getReference2bsPerProposal().putIfAbsent(
//               input.getBlock().getHetconsMessage().getM1A().getProposal(),
//               newM2bSet);
//       // If there was already a set in the map, we use the old one.
//       // Otherwise, newM2bSet WAS ADDED, so we should use that.
//       if (m2bsKnownForThisHash == null) {
//         newM2bSet.add(input.getBlock());
//       } else {
//         m2bsKnownForThisHash.add(input.getBlock());
//       }
//        logger.info(input.getBlock().getHetconsMessage().toString());
//        storeNewBlock(input.getBlock());
     }
    return super.onSendBlocksInput(input);
  }

}
