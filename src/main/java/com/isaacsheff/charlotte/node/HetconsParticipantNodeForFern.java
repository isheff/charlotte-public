package com.isaacsheff.charlotte.node;

import java.util.ArrayList;
import java.util.Collection;

import com.isaacsheff.charlotte.fern.HetconsFern;
import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.HetconsMessage2ab;
import com.isaacsheff.charlotte.proto.HetconsObserverQuorum;
import com.isaacsheff.charlotte.yaml.Config;

import com.xinwenwang.hetcons.HetconsParticipantService;
import com.xinwenwang.hetcons.HetconsStatus;
import com.xinwenwang.hetcons.config.HetconsConfig;

public class HetconsParticipantNodeForFern extends HetconsParticipantService {

  private final HetconsFern fern;


  public HetconsParticipantNodeForFern(final Config config,
                                       final HetconsConfig hetconsConfig,
                                       final HetconsFern fern) {
    super(config, hetconsConfig);
    this.fern = fern;
  }

  /**
   * Invoked whenever an observer reaches a decision.
   * Extending classes may find it useful to Override this.
   * Note that this may be called multiple times for the same consensus, as more 2bs arrive.
   * This implementation does nothing.
   * @param quora The quora satisfied by the 2b messages known.
   * @param statis the HetconsStatus for this decision.
   * @param message2b the actual message that triggered this decision.
   * @param id the CryptoId of the sender of the most recent 2b.
   */
  @Override
  protected void onDecision(final Collection<HetconsObserverQuorum> quora,
                            final HetconsStatus status,
                            final HetconsMessage2ab message2b,
                            final CryptoId id) {}

}
