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
        GenerateX509.generateKeyFiles( testDirectory + "server.pem",
                testDirectory + "private-key.pem",
                "isheff.cs.cornell.edu",
                "128.84.155.11");

        for (int i = 1; i <= 10; i ++) {
            GenerateX509.generateKeyFiles( testDirectory + "server"+i+".pem",
                    testDirectory + "private-key"+i+".pem",
                    "isheff.cs.cornell.edu",
                    "128.84.155.11");
        }
//        GenerateX509.generateKeyFiles( testDirectory + "server1.pemgenerateKeyFiles",
//                testDirectory + "private-key1.pem"generateKeyFiles,
//                "isheff.cs.cornell.edu"generateKeyFiles,
//                "128.84.155.11")generateKeyFiles;
//        GenerateX509.generateKeyFiles( testDirectory + "server2.pemgenerateKeyFiles",
//                testDirectory + "private-key2.pem"generateKeyFiles,
//                "isheff.cs.cornell.edu"generateKeyFiles,
//                "128.84.155.11"generateKeyFiles);
    }
}
