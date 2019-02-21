package com.xinwenwang.hetcons;

import static com.isaacsheff.charlotte.node.PortUtil.getFreshPort;

import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeServiceTest;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.*;
import com.xinwenwang.hetcons.config.HetconsConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HetconsParticipantServiceTest extends HetconsTest {



    // Propose

    // relay successfully

    // 1b sent correctly


    @Test
    void proposeNewBlock() {

        ArrayList<JsonContact> testContacts = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            testContacts.add(new JsonContact(
                    "server"+i+".pem",
                    "localhost",
                    getFreshPort()
            ));
        }

        JsonContact contactServer = testContacts.get(0);
        JsonContact contactClient = testContacts.get(1);

        Map<String, JsonContact> contacts = new HashMap<>();
        contacts.put("serverNode", contactServer);
        contacts.put("clientNode", contactClient);


        for (int i = 2; i < 10; i ++) {
            contacts.put("server"+(i+1), testContacts.get(i));
        }

        JsonConfig serverJsonConfig = new JsonConfig("private-key1.pem",
                "serverNode",
                contacts);
        JsonConfig clientJsonConfig = new JsonConfig("private-key2.pem",
                "clientNode",
                contacts);

        HetconsConfig.setConfigFileDirectory(testDirectory);

        ArrayList<Thread> threads = new ArrayList<>();

        startNewService(serverJsonConfig, threads);
        for (int i = 3; i <= 10; i ++) {
            startNewService(new JsonConfig("private-key"+i+".pem",
                    "server"+(i),
                    contacts), threads);
        }

        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, "Interrupt got");
            return;
        }

        Config clientConfig = new Config(clientJsonConfig, Paths.get(testDirectory));
        Contact serverContact = new Contact(contactServer, Paths.get(testDirectory), clientConfig);

        ArrayList<CryptoId> quorumMembers = new ArrayList<>();
        for (int i = 2; i < 9; i ++) {
            quorumMembers.add(new Contact(testContacts.get(i), Paths.get(testDirectory), clientConfig).getCryptoId());
        }
        HetconsClientNode client = new HetconsClientNode(serverContact, clientConfig);


        /*
         * Set up observer group
         */
        HetconsObserverQuorum quorum = HetconsObserverQuorum.newBuilder()
                .addAllMemebers(quorumMembers)
                .setOwner(serverContact.getCryptoId())
//                .setSize(quorumMembers.size())
                .build();

        HetconsObserver observer1 = HetconsObserver.newBuilder()
                .setId(serverContact.getCryptoId())
                .addQuorums(quorum)
                .build();

        HetconsObserver observer2 = HetconsObserver.newBuilder()
                .setId(quorumMembers.get(0))
                .addQuorums(quorum)
                .build();

        HetconsObserverGroup observerGroup = HetconsObserverGroup.newBuilder()
                .addObservers(observer1)
                .addObservers(observer2)
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

        HetconsBallot ballot = HetconsUtil.buildBallot(value);

        HetconsValue value1 = HetconsValue.newBuilder().setNum(200).build();
        HetconsBallot ballot1 = HetconsUtil.buildBallot(value1);


        /*
         * start the test
         */
//        assertEquals(0, map.size(), "sizebefore propose, there should no prosals avalaible");
        client.propose(slots, value, ballot, observerGroup, 1000);
        client.propose(slots, value1, ballot1, observerGroup, 1000);

        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, "Interrupt got");
            return;
        }
//        assertEquals(1, map.size(), "Propose successfully");
//        assertEquals(1, map.get("abc|1").getCurrentProposal().getBallot().getBallotNumber(), "ballot number different");
//        assertEquals(100, map.get("abc|1").getCurrentProposal().getValue().getNum(), "value different");
    }


    private HashMap<String, HetconsStatus> startNewService(JsonConfig config, List<Thread> threads) {

        Config serverConfig = new Config(config, Paths.get(testDirectory));
        HetconsConfig hetconsConfig = new HetconsConfig();
        HetconsParticipantService service = new HetconsParticipantService(serverConfig, hetconsConfig);
        CharlotteNode node = new CharlotteNode(service);
        HashMap<String, HetconsStatus> map = service.getProposalStatusHashMap();
        final Thread thread = new Thread(node);
        threads.add(thread);
        thread.start();
        return map;
    }
}
