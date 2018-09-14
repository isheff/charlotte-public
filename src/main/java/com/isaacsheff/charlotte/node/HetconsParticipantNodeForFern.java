package com.isaacsheff.charlotte.node;

import static java.util.concurrent.ConcurrentHashMap.newKeySet;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.isaacsheff.charlotte.fern.HetconsFern;
import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.HetconsMessage2ab;
import com.isaacsheff.charlotte.proto.HetconsObserverQuorum;
import com.isaacsheff.charlotte.proto.HetconsProposal;
import com.isaacsheff.charlotte.proto.SendBlocksInput;
import com.isaacsheff.charlotte.proto.SendBlocksResponse;
import com.isaacsheff.charlotte.yaml.Config;

import com.xinwenwang.hetcons.HetconsParticipantService;
import com.xinwenwang.hetcons.HetconsStatus;
import com.xinwenwang.hetcons.config.HetconsConfig;

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
   * @param config the configuration file for the CharlotteNodeService
   * @param hetconsConfig the configuration for the Hetcons.
   * @param fern the Fern Service
   */
  public HetconsParticipantNodeForFern(final Config config,
                                       final HetconsConfig hetconsConfig,
                                       final HetconsFern fern) {
    super(config, hetconsConfig);
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
  protected void onDecision(final Collection<HetconsObserverQuorum> quora,
                            final HetconsStatus status,
                            final HetconsMessage2ab message2b,
                            final CryptoId id) {
    final Set<CryptoId> observers = newKeySet();
    for (HetconsObserverQuorum quorum : quora) {
      if (quorum.hasOwner()) {
        observers.add(quorum.getOwner());
      }
    }
    getFern().observersDecide(observers, message2b.getValue(), message2b.getProposal());
  }

  /**
   * If this is a 2B, we store it away with the associated proposal.
   * Then we pass it along (whether or not it was a 2B).
   */
  @Override
  public Iterable<SendBlocksResponse> onSendBlocksInput(SendBlocksInput input) {
    if (input.hasBlock()
        && input.getBlock().hasHetconsMessage()
        && input.getBlock().getHetconsMessage().hasM2B()
        && input.getBlock().getHetconsMessage().getM2B().hasProposal()) {
       final Set<Block> newM2bSet = newKeySet();
       final Set<Block> m2bsKnownForThisHash = getReference2bsPerProposal().putIfAbsent(
               input.getBlock().getHetconsMessage().getM1A().getProposal(),
               newM2bSet);
       // If there was already a set in the map, we use the old one.
       // Otherwise, newM2bSet WAS ADDED, so we should use that.
       if (m2bsKnownForThisHash == null) {
         newM2bSet.add(input.getBlock());
       } else {
         m2bsKnownForThisHash.add(input.getBlock());
       }
     }
    return super.onSendBlocksInput(input);
  }

}
