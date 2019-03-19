package com.isaacsheff.charlotte.node;

import static java.util.concurrent.ConcurrentHashMap.newKeySet;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.logging.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.isaacsheff.charlotte.fern.HetconsFern;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.Config;

import com.isaacsheff.charlotte.yaml.Contact;
import com.isaacsheff.charlotte.yaml.JsonContact;
import com.xinwenwang.hetcons.HetconsParticipantService;
import com.xinwenwang.hetcons.HetconsUtil;
import com.xinwenwang.hetcons.config.HetconsConfig;

/**
 * A small extension of the HetconsParticipantService designed to forward consensus decisions to the HetconsFern service.
 * It also tracks which 2B message blocks go with which proposals, so those can be referenced.
 * @author Isaac Sheff
 */
public class HetconsParticipantNodeForFern extends HetconsParticipantService {

  /** Use logger for logging events in this class. */
  private static final Logger logger = Logger.getLogger(HetconsParticipantService.class.getName());

  /** The HetconsConfig affiliated with this consensus **/
  private final HetconsConfig hetconsConfig;

  /** The 2bs we've received affiliated with each proposal **/
  private final ConcurrentMap<HetconsProposal, Set<Block>> reference2bsPerProposal;

  /** The HetconsFern Service affiliated with this node **/
  private HetconsFern fern;

  /* next available slot for a chain */
  private HashMap<Reference, Long> nextSlot;

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
    nextSlot = new HashMap<>();
//    logger.setUseParentHandlers(false);
//    SimpleFormatter fmt = new SimpleFormatter();
//    StreamHandler sh = new StreamHandler(System.out, fmt) {
//      @Override
//      public synchronized void publish(final LogRecord record) {
//        super.publish(record);
//        flush();
//      }
//    };
//    logger.addHandler(sh);
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
   */
  @Override
  protected void onDecision(final HetconsObserverQuorum quora,
                            final Collection<Reference> quorum2b) {
    HetconsValue value = HetconsUtil.get2bValue(getBlock(quorum2b.iterator().next()).getHetconsBlock().getHetconsMessage().getM2B(), this);
    String loggerString = "";
//    Contact contact = getConfig().getContact(quora.getOwner());
//    loggerString += "\n\n\t" + contact.getUrl() + ":" + contact.getPort() + "\n\n";
//    for (CryptoId id : quora.getMembersList()) {
//      contact = getConfig().getContact(id);
//      loggerString += contact.getUrl() + ":" + contact.getPort() + "\n";
//    }
    logger.info("Consensus Decided on value " + value.getNum() + "\n" + loggerString + "\n");
    getFern().observersDecide(quora, quorum2b);
  }

  /**
   * If this is a 2B, we store it away with the associated proposal.
   * Then we pass it along (whether or not it was a 2B).
   * @return any responses, in this case just forwarded from HetconsParticipantService.onSendBlocksInput
   */
  @Override
  public Iterable<SendBlocksResponse> onSendBlocksInput(Block block) {
    if (block.hasIntegrityAttestation() && storeNewBlock(block) && block.getIntegrityAttestation().hasSignedHetconsAttestation()) {
      block.getIntegrityAttestation().getSignedHetconsAttestation().getAttestation().getSlotsList().forEach(e -> {
        Long slot = nextSlot.putIfAbsent(e.getRoot(), e.getSlot() + 1);
        /* update next available slot */
        if (slot != null && slot < e.getSlot() + 1) {
          nextSlot.put(e.getRoot(), e.getSlot() + 1);
        }
      });
      getFern().saveAttestation(block.getIntegrityAttestation());
//      for (CryptoId o : block.getIntegrityAttestation().getHetconsAttestation().getObserversList()) {
//        sendBlock(o, block);
//      }
      broadcastBlock(block);
      return Collections.emptySet();
    } else {
      return super.onSendBlocksInput(block);
    }
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

  /**
   * Return the next available slot number for the chain with given root. If there is no root registered yet, then register this root and set the slot number to be 0.
   * @param root
   * @return
   */
  public Long getNextAvailableSlot(Reference root) {
    nextSlot.putIfAbsent(root, 1L);
    return nextSlot.get(root);
  }

  /**
   *
   * @param slot
   * @param observer
   * @return
   */
  @Override
  protected boolean hasAttestation(IntegrityAttestation.ChainSlot slot, CryptoId observer) {

    return getFern().getHetconsAttestationCache().containsKey(slot) && getFern().getHetconsAttestationCache().get(slot).containsKey(observer);
  }
}
