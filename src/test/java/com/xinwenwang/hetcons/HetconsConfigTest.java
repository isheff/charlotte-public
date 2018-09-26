package com.xinwenwang.hetcons;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.isaacsheff.charlotte.yaml.JsonContact;
import com.xinwenwang.hetcons.config.ChainConfig;
import com.xinwenwang.hetcons.config.HetconsConfig;
import com.xinwenwang.hetcons.config.ObserverConfig;
import com.xinwenwang.hetcons.config.QuorumConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

public class HetconsConfigTest extends HetconsTest {



    @Test
    void parseChainConfigFile() {

        Path path = Paths.get(testDirectory + "chain.yaml");
        try {

            ChainConfig config = new ObjectMapper(new YAMLFactory()).readValue(path.toFile(), ChainConfig.class);
            assertEquals(config.getRoot(), "abc");

            assertEquals(config.getObservers().size(), 2);
            assertEquals(config.getObservers().get(0).getSelf().getUrl(), "server1.com");
            assertEquals(config.getObservers().get(1).getSelf().getUrl(), "server2.com");
            logger.info(config.getObserverGroup(Paths.get(testDirectory)).toString());
        } catch (IOException ex) {
            ex.printStackTrace();
            assert false;
        }
    }


    @Test
    void parseObserverConfigFile() {

        Path path = Paths.get(testDirectory + "observer.yaml");
        try {

            ObserverConfig config = new ObjectMapper(new YAMLFactory()).readValue(path.toFile(), ObserverConfig.class);
            assertEquals(config.getSelf().getUrl(), "server.com");
            assertEquals(config.getQuorums().size(), 2);
            for (QuorumConfig quorumConfig : config.getQuorums()) {
                assertEquals(quorumConfig.getParticipants().size(), 2);
                for (JsonContact contact : quorumConfig.getParticipants()) {
                    logger.info(contact.getUrl());
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            assert false;
        }
    }


    @Test
    void parseQuorumConfigFile() {
        Path path = Paths.get(testDirectory + "quorum.yaml");
        try {

            QuorumConfig config = new ObjectMapper(new YAMLFactory()).readValue(path.toFile(), QuorumConfig.class);
            for (JsonContact contact : config.getParticipants()) {
                assertTrue(contact.getUrl().equals("bob.com") || contact.getUrl().equals("alice.com"));
                logger.info(contact.getUrl());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            assert false;
        }
    }
}
