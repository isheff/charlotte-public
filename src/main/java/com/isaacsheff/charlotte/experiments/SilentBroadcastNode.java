package com.isaacsheff.charlotte.experiments;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;

/**
 * A CharlotteNodeService that simply doesn't do anything when other services would broadcast a block.
 * @author Isaac Sheff
 */
public class SilentBroadcastNode extends CharlotteNodeService {
  /**
   * Create a new SilentBroadcastNode.
   * It's like a regular CharlotteNodeService, but it doesn't broadcast blocks when others would.
   * @param config the filename of a confugration file
   */
  public SilentBroadcastNode(final String config) {super(config);}

  /**
   * Instead of actually broadcasting a block, this does nothing.
   * @param block the block we won't broadcast.
   */
  @Override
  public void broadcastBlock(final Block block) {}
}
