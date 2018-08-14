package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.node.CharlotteNodeServiceTest;
import com.isaacsheff.charlotte.yaml.GenerateX509;
import org.junit.jupiter.api.BeforeAll;

import java.util.logging.Logger;

public class HetconsTest {

    protected final Logger logger = Logger.getLogger(CharlotteNodeServiceTest.class.getName());

    protected static int usedPort;

    protected static String testDirectory = "src/test/resources/";

    // Propose

    // relay successfully

    // 1b sent correctly


    @BeforeAll
    protected static void setup() {
        GenerateX509.generateKeyFiles( testDirectory + "server1.pem",
                testDirectory + "private-key1.pem",
                "isheff.cs.cornell.edu",
                "128.84.155.11");
        GenerateX509.generateKeyFiles( testDirectory + "server2.pem",
                testDirectory + "private-key2.pem",
                "isheff.cs.cornell.edu",
                "128.84.155.11");
    }
}
