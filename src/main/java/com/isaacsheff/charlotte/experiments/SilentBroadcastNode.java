package com.isaacsheff.charlotte.experiments;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;

/**
 * A CharlotteNodeService that simply doesn't do anything when other services would broadcast a block.
 */
public class SilentBroadcastNode extends CharlotteNodeService {
  public SilentBroadcastNode(String config) {super(config);}

  @Override
  public void broadcastBlock(final Block block) {}
}
