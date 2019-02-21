package com.xinwenwang.hetcons;

import static com.isaacsheff.charlotte.node.PortUtil.getFreshPort;

import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.Contact;
import com.isaacsheff.charlotte.yaml.JsonConfig;
import com.isaacsheff.charlotte.yaml.JsonContact;
import com.xinwenwang.hetcons.config.HetconsConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HetconsClientTest extends HetconsTest {


    @Test
    void proposeBlocks() {


        JsonContact contactServer = new JsonContact(
                "server1.pem",
                "localhost",
                getFreshPort()
        );

        JsonContact contact2 = new JsonContact(
                "server2.pem",
                "localhost",
                getFreshPort()
        );

        JsonContact contactClient = new JsonContact(
                "server.pem",
                "localhost",
                getFreshPort()
        );

        Map<String, JsonContact> contacts = new HashMap<>();
        contacts.put("serverNode", contactServer);
        contacts.put("clientNode", contactClient);

        JsonConfig serverJsonConfig = new JsonConfig("private-key1.pem",
                "serverNode",
                contacts);
        JsonConfig clientJsonConfig = new JsonConfig("private-key2.pem",
                "clientNode",
                contacts);

        Config serverConfig = new Config(serverJsonConfig, Paths.get(testDirectory));
        Config clientConfig = new Config(clientJsonConfig, Paths.get(testDirectory));
        HetconsConfig hetconsConfig = new HetconsConfig();

        HetconsConfig.setConfigFileDirectory(testDirectory);

        HetconsParticipantService service = new HetconsParticipantService(serverConfig, hetconsConfig);
        CharlotteNode node1 = new CharlotteNode(service);

        HashMap<String, HetconsStatus> map = service.getProposalStatusHashMap();

        final Thread thread1 = new Thread(node1);
        thread1.start();

        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, "Interrupt got");
            return;
        }

        Contact serverContact = new Contact(contactServer, Paths.get(testDirectory), serverConfig);
        HetconsClientNode client = new HetconsClientNode(serverContact, clientConfig);


        /*
         * Set up observer group
         */
        HetconsObserverQuorum quorum = HetconsObserverQuorum.newBuilder()
                .addMemebers(serverContact.getCryptoId())
                .setOwner(serverContact.getCryptoId())
//                .setSize(1)
                .build();

        HetconsObserver observer = HetconsObserver.newBuilder()
                .setId(serverContact.getCryptoId())
                .addQuorums(quorum)
                .build();

        HetconsObserverGroup observerGroup = HetconsObserverGroup.newBuilder()
                .addObservers(observer)
                .build();


        /*
         * set up proposal
         */
        IntegrityAttestation.ChainSlot slot = IntegrityAttestation.ChainSlot.newBuilder()
                .setRoot(Reference.newBuilder()
                        .setHash(Hash.newBuilder().setSha3(ByteString.copyFromUtf8("abc")).build())
                        .build())
                .setSlot(1)
                .build();

        ArrayList<IntegrityAttestation.ChainSlot> slots= new ArrayList<>();
        slots.add(slot);
        HetconsValue value = HetconsValue.newBuilder()
                .setNum(100).build();

        HetconsBallot ballot = HetconsBallot.newBuilder().setBallotNumber(1).build();


        /*
         * start the test
         */
        assertEquals(0, map.size(), "before propose, there should no prosals avalaible");
        client.propose(slots, value, ballot, observerGroup, 100);

        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, "Interrupt got");
            return;
        }
        assertEquals(1, map.size(), "Propose successfully");
        assertEquals(1, map.get("abc|1").getCurrentProposal().getBallot().getBallotNumber(), "ballot number different");
        assertEquals(100, map.get("abc|1").getCurrentProposal().getValue().getNum(), "value different");
    }
}
