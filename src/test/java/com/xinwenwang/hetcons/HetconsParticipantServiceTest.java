package com.xinwenwang.hetcons;

import static com.isaacsheff.charlotte.node.PortUtil.getFreshPort;

import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.*;
import com.xinwenwang.hetcons.config.HetconsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HetconsParticipantServiceTest extends HetconsTest {



    // Propose

    // relay successfully

    // 1b sent correctly


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

//        HetconsConfig.setConfigFileDirectory(testDirectory);

        ArrayList<Thread> threads = new ArrayList<>();

        Config serverConfig = startNewService(serverJsonConfig, threads);
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
                .addAllMembers(quorumMembers)
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
        IntegrityAttestation.ChainSlot slot2 = IntegrityAttestation.ChainSlot.newBuilder()
                .setRoot(Reference.newBuilder()
                        .setHash(Hash.newBuilder().setSha3(ByteString.copyFromUtf8("efg")).build())
                        .build())
                .setSlot(1)
                .build();

        ArrayList<IntegrityAttestation.ChainSlot> slots= new ArrayList<>();
        ArrayList<IntegrityAttestation.ChainSlot> slots2= new ArrayList<>();
        slots.add(slot);
        slots.add(slot2);
        slots2.add(slot2);
        HetconsValue value = HetconsValue.newBuilder()
                .setNum(100).build();

        HetconsBallot ballot = HetconsUtil.buildBallot(value);

        HetconsValue value1 = HetconsValue.newBuilder().setNum(200).build();
        HetconsBallot ballot1 = HetconsUtil.buildBallot(value1);


        /*
         * start the test
         */
//        assertEquals(0, map.size(), "sizebefore propose, there should no prosals avalaible");
        client.propose(slots, value, ballot, observerGroup, 10000);
        client.propose(slots2, value1, ballot1, observerGroup, 10000);

        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, "Interrupt got");
            return;
        }
        threads.forEach(t -> {
            t.interrupt();
        });
//        assertEquals(1, map.size(), "Propose successfully");
//        assertEquals(1, map.get("abc|1").getCurrentProposal().getBallot().getBallotNumber(), "ballot number different");
//        assertEquals(100, map.get("abc|1").getCurrentProposal().getValue().getNum(), "value different");
    }


    private Config startNewService(JsonConfig config, List<Thread> threads) {

        Config serverConfig = new Config(config, Paths.get(testDirectory));
        HetconsParticipantService service = new HetconsParticipantService(serverConfig);
        CharlotteNode node = new CharlotteNode(service);
//        HashMap<String, HetconsProposalStatus> map = service.getProposalStatusHashMap();
        final Thread thread = new Thread(node);
        threads.add(thread);
        thread.start();
        return serverConfig;
    }

    @Test
    void testProposalNewBlock() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                proposeNewBlock();
            }
        });
    }
}
