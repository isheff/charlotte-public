package com.xinwenwang.hetcons;

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

    private int port;

    @Test
    void proposeBlocks() {

        port = 10080;

        JsonContact contactServer = new JsonContact(
                "server1.pem",
                "localhost",
                port++
        );

        JsonContact contact2 = new JsonContact(
                "server2.pem",
                "localhost",
                port++
        );

        JsonContact contactClient = new JsonContact(
                "server.pem",
                "localhost",
                port++
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

        Contact serverContact = new Contact(contactServer, Paths.get(testDirectory));
        HetconsClientNode client = new HetconsClientNode(serverContact, clientConfig);


        /*
         * Set up observer group
         */
        HetconsObserverQuorum quorum = HetconsObserverQuorum.newBuilder()
                .addMemebers(serverContact.getCryptoId())
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
        HetconsParticipatedAccountsInfo accountsInfo = HetconsParticipatedAccountsInfo.newBuilder()
                .setChainHash(Hash.newBuilder().setSha3(ByteString.copyFromUtf8("abc")).build()).setSlot(HetconsSlot.newBuilder().setBlockSlotNumber(1).build())
                .build();

        ArrayList<HetconsParticipatedAccountsInfo> accountsInfos = new ArrayList<>();
        accountsInfos.add(accountsInfo);
        HetconsValue value = HetconsValue.newBuilder()
                .setNum(100).build();


        /*
         * start the test
         */
        assertEquals(0, map.size(), "before propose, there should no prosals avalaible");
        client.propose(accountsInfos, value, 1, observerGroup );

        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, "Interrupt got");
            return;
        }
        assertEquals(1, map.size(), "Propose successfully");
        assertEquals(1, map.get("abc|1").getCurrentProposal().getBallotNumber(), "ballot number different");
        assertEquals(100, map.get("abc|1").getCurrentProposal().getValue().getNum(), "value different");


//        HetconsValue value1 = HetconsValue.newBuilder()
//                .setNum(200).build();

    }
}
