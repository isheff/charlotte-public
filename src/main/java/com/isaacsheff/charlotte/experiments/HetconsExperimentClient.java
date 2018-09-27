package com.isaacsheff.charlotte.experiments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.fern.HetconsFernClient;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.Contact;
import com.isaacsheff.charlotte.yaml.JsonContact;
import com.xinwenwang.hetcons.HetconsClientNode;
import com.xinwenwang.hetcons.HetconsUtil;
import com.xinwenwang.hetcons.config.ChainConfig;
import com.xinwenwang.hetcons.config.HetconsConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class HetconsExperimentClient {

    private static final Logger logger = Logger.getLogger(TimestampExperimentClient.class.getName());

    /**
     * Command line main
     *
     * @param args args[0] should be the config file path
     */
    public static void main(String[] args) throws IOException, InterruptedException {

        final HetconsExperimentClientConfig config =
                (new ObjectMapper(new YAMLFactory())).readValue(Paths.get(args[0]).toFile(), HetconsExperimentClientConfig.class);

        Path configPath = Paths.get(args[0]);
        Path expDir = configPath.getParent();
        config.getContacts();
        Config localNodeConfig = new Config(config, expDir);

        Contact fernContact = new Contact(config.getContacts().get(config.getContactServer()), expDir);
        CharlotteNodeService service = new CharlotteNodeService(localNodeConfig);
        HetconsFernClient client = new HetconsFernClient(service, fernContact);
        HetconsConfig hetconsConfig = new HetconsConfig(expDir);

        assert config.getContactServer() != null;
        switch (args[1].charAt(0)) {
            case '1':
                assert config.getChainNames().size() == 1;
                assert config.getFernServers().size() == 4;
                runExperiment(hetconsConfig, config, client, expDir, 1);
                break;
            case '2':
                assert config.getChainNames().size() > 1;
                assert config.getFernServers().size() >= 4;
                runExperiment(hetconsConfig, config, client, expDir, 2);
            case '3':
                assert config.getChainNames().size() == 1;
                assert config.getFernServers().size() == 4;
                runExperiment(hetconsConfig, config, client, expDir, 4);
            case '4':

            default:
                logger.warning("No such experiment");
        }

    }

    private static void runExperiment(HetconsConfig hetconsConfig,
                                      HetconsExperimentClientConfig config,
                                      HetconsFernClient clientNode,
                                      Path expDir,
                                      int num) throws InterruptedException {


        ArrayList<Thread> threads = new ArrayList<>();

        for (String cn : config.getChainNames()) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    testPerChain(cn, hetconsConfig, config, clientNode, expDir, num);
                }
            });
            threads.add(thread);
            thread.start();
        }

        for (Thread t : threads) {
            t.join();
        }
    }

    private static void testPerChain(String cn,
                                     HetconsConfig hetconsConfig,
                                     HetconsExperimentClientConfig config,
                                     HetconsFernClient clientNode,
                                     Path expDir,
                                     int num) {
        ChainConfig chainConfig = hetconsConfig.loadChain(cn);
        if (chainConfig == null)
            return;
        HetconsObserverGroup group = chainConfig.getObserverGroup(expDir);

        HetconsMessage observerMessage = HetconsMessage.newBuilder()
                .setType(HetconsMessageType.OBSERVERGROUP)
                .setObserverGroup(group)
                .setIdentity(clientNode.getLocalService().getConfig().getCryptoId())
                .setSig(SignatureUtil.signBytes(clientNode.getLocalService().getConfig().getKeyPair(),
                        group))
                .build();

        Block observerBlock = Block.newBuilder().setHetconsMessage(observerMessage).build();
        clientNode.getLocalService().sendBlock(clientNode.getContact().getCryptoId(), observerBlock);


        Reference obsblkRef = Reference.newBuilder()
                .setHash(HashUtil.sha3Hash(observerBlock)).build();

        logger.info(String.format("Experiment part %d start", num));
        for (int i = 0; i < config.getBlocksPerExperiment(); i++) {
            // Build proposal
            IntegrityAttestation.ChainSlot slot = IntegrityAttestation.ChainSlot.newBuilder()
                    .setRoot(Reference.newBuilder()
                            .setHash(Hash.newBuilder().setSha3(ByteString.copyFromUtf8("abc")).build())
                            .build())
                    .setSlot(i)
                    .build();

            ArrayList<IntegrityAttestation.ChainSlot> slots = new ArrayList<>();
            slots.add(slot);

            HetconsValue value = HetconsValue.newBuilder()
                    .setNum(100 + i).build();

            HetconsBallot ballot = HetconsUtil.buildBallot(value);

            // Propose

            HetconsProposal proposal = HetconsUtil.buildProposal(slots, value, ballot, 1000);
            HetconsMessage1a message1a = HetconsMessage1a.newBuilder()
                    .setProposal(proposal)
                    .build();
            HetconsMessage message = HetconsMessage.newBuilder()
                    .setType(HetconsMessageType.M1a)
                    .setM1A(message1a)
                    .setIdentity(clientNode.getLocalService().getConfig().getCryptoId())
                    .setObserverGroupReferecne(obsblkRef)
                    .setSig(SignatureUtil.signBytes(
                            clientNode.getLocalService().getConfig().getKeyPair(),
                            message1a))
                    .build();

            RequestIntegrityAttestationInput input = RequestIntegrityAttestationInput.newBuilder()
                    .setPolicy(
                            IntegrityPolicy.newBuilder()
                                    .setHetconsPolicy(
                                            IntegrityPolicy.HetconsPolicy.newBuilder()
                                                    .setProposal(message)
                                                    .setObserver(clientNode.getContact().getCryptoId()) // FIXME: only wait on this observer?
                                                    .build()
                                    )
                                    .build()
                    ).build();

            RequestIntegrityAttestationResponse response = clientNode.requestIntegrityAttestation(input);
        }
        logger.info(String.format("Experiment part %d completed", num));
    }
}

