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
import com.xinwenwang.hetcons.HetconsUtil;
import com.xinwenwang.hetcons.config.ChainConfig;
import com.xinwenwang.hetcons.config.HetconsConfig;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class HetconsExperimentClient {

    private static Random rnd = new Random(new Date().getTime());

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

        CharlotteNodeService service = new CharlotteNodeService(localNodeConfig);
        HetconsConfig hetconsConfig = new HetconsConfig(expDir);
        if (config.isBitcoinSim()) {
            runBitcoinSim(hetconsConfig, config, service, expDir);
        } else {
            runExperiment(hetconsConfig, config, service, expDir);
        }
    }

    private static void runExperiment(HetconsConfig hetconsConfig,
                                      HetconsExperimentClientConfig config,
                                      CharlotteNodeService service,
                                      Path expDir) throws InterruptedException {

        logger.info("Hetcons Experiment Begin");
        int count = 1;
        for (String chainLine : config.getChainNames()) {
            logger.info(String.format("Hetcons Experiment Part %d Begin", count));
            ArrayList<Thread> threads = new ArrayList<>();
            for (String cn : chainLine.trim().split("\\s+")) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        testPerChain(cn, hetconsConfig, config, service, expDir);
                    }
                });
                threads.add(thread);
                thread.start();
            }
            for (Thread t : threads) {
                t.join();
            }
            logger.info(String.format("Hetcons Experiment Part %d Completed", count++));
        }
        logger.info("Hetcons Experiment Completed");
        System.exit(0);
    }

    /**
     * Test for each given chain or chains with shared block
     * @param cn
     * @param hetconsConfig
     * @param config
     * @param expDir
//     * @param num
     */
    private static void testPerChain(String cn,
                                     HetconsConfig hetconsConfig,
                                     HetconsExperimentClientConfig config,
                                     CharlotteNodeService service,
                                     Path expDir) {
        ChainConfig chainConfig = hetconsConfig.loadChain(cn);
        if (chainConfig == null)
            return;
        JsonContact contactServer = config.getContacts().get(config.getContactServer());
        Contact fernContact = new Contact(contactServer, expDir, service.getConfig());
//        Contact fernContact = new Contact(config.getContactServer()   chainConfig.getObservers().get(0).getSelf(), expDir, service.getConfig());
        HetconsFernClient clientNode = new HetconsFernClient(service, fernContact);

        HetconsObserverGroup group = chainConfig.getObserverGroup(expDir);

        HetconsMessage observerMessage = HetconsMessage.newBuilder()
                .setType(HetconsMessageType.OBSERVERGROUP)
                .setObserverGroup(group)
                .setIdentity(clientNode.getLocalService().getConfig().getCryptoId())
                .build();

        HetconsBlock observerHetconsBlock = HetconsBlock.newBuilder()
                .setHetconsMessage(observerMessage)
                .setSig(SignatureUtil.signBytes(clientNode.getLocalService().getConfig().getKeyPair(),
                        observerMessage))
                .build();

        Block observerBlock = Block.newBuilder().setHetconsBlock(observerHetconsBlock).build();
        clientNode.getLocalService().sendBlock(clientNode.getContact().getCryptoId(), observerBlock);

        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        Reference obsblkRef = Reference.newBuilder()
                .setHash(HashUtil.sha3Hash(observerBlock)).build();

//        logger.info(String.format("Experiment part %d start", num));
        for (int i = 1; i <= config.getBlocksPerExperiment(); i++) {
            // Build proposal
            IntegrityAttestation.ChainSlot slot = IntegrityAttestation.ChainSlot.newBuilder()
                    .setRoot(Reference.newBuilder()
                            .setHash(Hash.newBuilder().setSha3(ByteString.copyFromUtf8(cn)).build())
                            .build())
                    .setSlot(i)
                    .build();

            ArrayList<IntegrityAttestation.ChainSlot> slots = new ArrayList<>();
            slots.add(slot);

            HetconsValue value = HetconsValue.newBuilder()
                    .setNum(config.getStartingIndex() + i).build();

            HetconsBallot ballot = HetconsUtil.buildBallot(value);

            // Propose

            HetconsProposal proposal = HetconsUtil.buildProposal(slots, value, ballot, config.getTimeout() * 1000);
            HetconsMessage1a message1a = HetconsMessage1a.newBuilder()
                    .setProposal(proposal)
                    .build();
            HetconsMessage message = HetconsMessage.newBuilder()
                    .setType(HetconsMessageType.M1a)
                    .setM1A(message1a)
                    .setIdentity(clientNode.getLocalService().getConfig().getCryptoId())
                    .setObserverGroupReferecne(obsblkRef)
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
            logger.info(String.format("Beginning slot for chain %s %d", cn, i));
//            logger.info("Proposal is " + proposal.getSlotsList());
            RequestIntegrityAttestationResponse response = clientNode.requestIntegrityAttestation(input);
            logger.info(String.format("Received response for chain %s %d", cn, i));
        }
//        logger.info(String.format("Experiment part %d completed", num));
    }

    private static void runBitcoinSim(HetconsConfig hetconsConfig,
                                      HetconsExperimentClientConfig config,
                                      CharlotteNodeService service,
                                      Path expDir) {
        HashMap<String, Chain> chainMap = new HashMap<>();
        ConcurrentMap<Hash, Long> chainSlotNumber = new ConcurrentHashMap<>();
        logger.info("Start BitcoinSim...");
        logger.info("# of blocks: "+config.getBlocksPerExperiment());
        logger.info("# of chains: "+config.getSingleChainNames().size());
        logger.info("Starting value: "+config.getStartingIndex());
        for (int i = 0; i < config.getBlocksPerExperiment(); i++) {
            String cn = getChain(config);
            try {
                if (!chainMap.containsKey(cn)) {
                    chainMap.put(cn, new Chain(cn, hetconsConfig, config, service, expDir, Arrays.asList(cn.split("-")), chainSlotNumber));
                }
                Chain chain = chainMap.get(cn);
                chain.proposeNewBlock(i);
            } catch (FileNotFoundException ex) {
                logger.severe(ex.getLocalizedMessage());
                System.exit(1);
            }
        }
        logger.info("BitcoinSim Completed");
        System.exit(0);

    }

    private static String getChain(HetconsExperimentClientConfig config) {
        if (rnd.nextFloat() > config.getDoubleChainProbability()) {
            return config.getSingleChainNames().get(rnd.nextInt(config.getSingleChainNames().size()));
        } else {
            return config.getDoubleChainNames().get(rnd.nextInt(config.getDoubleChainNames().size()));
        }
    }

    /**
     * A chain object waiting for issuing new proposal for that chain
     */
    static class Chain {

        static HashMap<CryptoId, HetconsFernClient> channelMap = new HashMap<>();

        HetconsExperimentClientConfig config;
        HetconsFernClient clientNode;
        Reference obsblkRef;
        String observerConfigFileName;
        Map<Hash, Long> chainStatus;
        Set<Hash> chainNames;
        Contact fernContact;

        Chain(String observerConfigFileName,
              HetconsConfig hetconsConfig,
              HetconsExperimentClientConfig config,
              CharlotteNodeService service,
              Path expDir,
              List<String> subChainNames,
              Map<Hash, Long> chainSlotStatus) throws FileNotFoundException {
            this.observerConfigFileName = observerConfigFileName;
            this.config = config;
            ChainConfig chainConfig = hetconsConfig.loadChain(observerConfigFileName);
            if (chainConfig == null) {
                throw new FileNotFoundException("Chain Config file: "+observerConfigFileName+" was not found");
            }
            /* Contact server will be a random fern server in the current observer group */
            JsonContact contactServer = chainConfig.getObservers().get(rnd.nextInt(chainConfig.getObservers().size())).getSelf();
//            JsonContact contactServer = chainConfig.getObservers().get(0).getSelf();
//            JsonContact contactServer = config.getContacts().get(config.getContactServer());
            fernContact = new Contact(contactServer, expDir, service.getConfig());

            if (!channelMap.containsKey(fernContact.getCryptoId())) {
                channelMap.put(fernContact.getCryptoId(), new HetconsFernClient(service, fernContact));
            }
            clientNode = channelMap.get(fernContact.getCryptoId());

            HetconsObserverGroup group = chainConfig.getObserverGroup(expDir);

            HetconsMessage observerMessage = HetconsMessage.newBuilder()
                    .setType(HetconsMessageType.OBSERVERGROUP)
                    .setObserverGroup(group)
                    .setIdentity(clientNode.getLocalService().getConfig().getCryptoId())
                    .build();

            HetconsBlock observerHetconsBlock = HetconsBlock.newBuilder()
                    .setHetconsMessage(observerMessage)
                    .setSig(SignatureUtil.signBytes(clientNode.getLocalService().getConfig().getKeyPair(),
                            observerMessage))
                    .build();

            Block observerBlock = Block.newBuilder().setHetconsBlock(observerHetconsBlock).build();
            clientNode.getLocalService().sendBlock(clientNode.getContact().getCryptoId(), observerBlock);

            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            chainNames = new HashSet<>();

            obsblkRef = Reference.newBuilder()
                    .setHash(HashUtil.sha3Hash(observerBlock)).build();
            if (subChainNames != null) {
                chainStatus = chainSlotStatus;
                subChainNames.forEach(n -> {
                    Hash nameHash = Hash.newBuilder().setSha3(ByteString.copyFromUtf8(n)).build();
                    chainStatus.putIfAbsent(nameHash, 1L);
                    chainNames.add(nameHash);
                });
            }
        }

        /**
         * Propose a new block to be appended to the end of current chain
         * @param i the slot number for this block to be placed.
         */
        void proposeNewBlock(int i) {
//            try {
//                TimeUnit.MILLISECONDS.sleep(500);
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
            RequestIntegrityAttestationResponse response = null;
            RequestIntegrityAttestationInput  input = null;
            do {
                input = prepareProposalBlock(i);
//                List<IntegrityAttestation.ChainSlot> slots = input.getPolicy().getHetconsPolicy().getProposal().getM1A().getProposal().getSlotsList();
                if (response == null) {
//                    logger.info(String.format("%d:%d:Beginning slot for %s", i, fernContact.getPort(), slots.toString()));
                    logger.info(String.format("Beginning slot for %s", i));
                } else {
//                    logger.info(String.format("%d:%d:Retry: Slot has been taken. Retry another %s", i, fernContact.getPort(), slots));
                    logger.info(String.format("Retry: Slot has been taken. Retry %s", i));
                }
                response = clientNode.requestIntegrityAttestation(input);
//                logger.info("Response back for "+slots.toString());
                if (response.getErrorMessage() == null || response.getErrorMessage().length() == 0) {
//                    logger.info(String.format("%d:%d:Received response for %s", i, fernContact.getPort(), slots.toString()));
                    logger.info(String.format("Received response for %s", i));
                }
                response.getAttestation().getSignedHetconsAttestation().getAttestation().getNextSlotNumbersList().forEach(chainSlot -> {
                    chainStatus.put(chainSlot.getRoot().getHash(), chainSlot.getSlot());
                });
            } while (response.getErrorMessage() != null && response.getErrorMessage().length() > 0);
        }


        /**
         * Prepare request block which will be sent to one of contact server
         * @param i
         * @return
         */
        RequestIntegrityAttestationInput prepareProposalBlock(int i) {

            /* set up proposal slots */
            ArrayList<IntegrityAttestation.ChainSlot> slots = new ArrayList<>();
            chainNames.forEach(n -> {
                slots.add(
                        IntegrityAttestation.ChainSlot.newBuilder()
                                .setRoot(Reference.newBuilder()
                                        .setHash(n)
                                        .build())
                                .setSlot(chainStatus.get(n))
                                .build()
                );
            });

            HetconsValue value = HetconsValue.newBuilder()
                    .setNum(config.getStartingIndex() + i).build();

            HetconsBallot ballot = HetconsUtil.buildBallot(value);

            HetconsProposal proposal = HetconsUtil.buildProposal(slots, value, ballot, config.getTimeout() * 1000);
            HetconsMessage1a message1a = HetconsMessage1a.newBuilder()
                    .setProposal(proposal)
                    .build();
            HetconsMessage message = HetconsMessage.newBuilder()
                    .setType(HetconsMessageType.M1a)
                    .setM1A(message1a)
                    .setIdentity(clientNode.getLocalService().getConfig().getCryptoId())
                    .setObserverGroupReferecne(obsblkRef)
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
            return input;
        }

    }
}

