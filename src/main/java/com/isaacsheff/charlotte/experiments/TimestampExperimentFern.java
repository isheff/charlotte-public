package com.isaacsheff.charlotte.experiments;

import com.isaacsheff.charlotte.fern.TimestampFern;

/** 
 * A minor extension of TimestampFern that lets you manually reset the
 *  TimestampExperimentNode (which is a CharlotteNodeService) that you're using.
 * @author Isaac Sheff
 */
public class TimestampExperimentFern extends TimestampFern {

  /** 
   * Set the TimestampExperimentNode (which is a CharlotteNodeService) that you're using.
   * @param node the new TimestampExperimentNode 
   */
  public void setNode(TimestampExperimentNode node) { this.node = node; }
}
