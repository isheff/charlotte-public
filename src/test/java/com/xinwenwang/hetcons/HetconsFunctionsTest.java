package com.xinwenwang.hetcons;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

public class HetconsFunctionsTest {

    @Test
    void testBuildConsensusID() {
        System.out.println(buildChainID(Lists.newArrayList("abc", "vcb", "efg")));
    }

    public static String buildChainID(List<String> chainNames) {
        return chainNames.stream().sorted().reduce((a, n) -> a.concat(n)).get();
    }
}
