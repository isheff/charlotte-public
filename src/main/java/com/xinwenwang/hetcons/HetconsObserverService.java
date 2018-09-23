package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.collections.BlockingMap;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.yaml.Config;

import java.nio.file.Path;

public class HetconsObserverService extends CharlotteNodeService {

    public HetconsObserverService(BlockingMap<Hash, Block> blockMap, Config config) {
        super(blockMap, config);
    }

    public HetconsObserverService(Config config) {
        super(config);
    }

    public HetconsObserverService(Path path) {
        super(path);
    }

    public HetconsObserverService(String filename) {
        super(filename);
    }
}
