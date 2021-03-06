package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.Config;
import com.isaacsheff.charlotte.yaml.Contact;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

public class HetconsParticipantService extends CharlotteNodeService {

    private static final Logger logger = Logger.getLogger(CharlotteNodeService.class.getName());

   /*  Map from observer crypto id to observers */
    private HashMap<String, HetconsObserverStatus> observers;

    /* the pool of threads to handle incoming blocks for each observer */
    private  ThreadPoolExecutor executorService;
    private  ThreadPoolExecutor executorService1b;
    private  ThreadPoolExecutor executorService2b;
    private  ThreadPoolExecutor executorServiceRestart;

    private Map<String, HetconsRestartStatus> restartTimers;

    private Map<CryptoId, Set<HetconsMessage>> sentBlocSet;
    private Map<HetconsMessage, Block> sentBlocks;

    private Object crossObserverSlotLock = new Object();

    public HetconsParticipantService(Config config) {
        super(config);
        observers = new HashMap<>();
        sentBlocSet = new ConcurrentHashMap<>();
        sentBlocks = new ConcurrentHashMap<>();
//        executorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 3);
        executorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
        executorService1b = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
        executorService2b = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
        executorServiceRestart = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
//        executorService1b = executorService;
//        executorService2b = executorService1b;
        restartTimers = new ConcurrentHashMap<>();
//        logger.setUseParentHandlers(false);
//        SimpleFormatter fmt = new SimpleFormatter();
//        StreamHandler sh = new StreamHandler(System.out, fmt);
//        logger.addHandler(sh);
    }

    /**
     * Multiplexing incoming blocks to its handler by block types
     * @param block the newly arrived block
     * @return a empty set if no errors. Otherwise, a collection of error messages in SendBlocksResponse
     */
    @Override
    public Iterable<SendBlocksResponse> onSendBlocksInput(final Block block) {
        if (!block.hasHetconsBlock()) {
            //TODO: handle error
            return super.onSendBlocksInput(block);
        }

        if (!storeNewBlock(block)) {
            // logger.info("Discard duplicated block " + block.getHetconsBlock().getHetconsMessage().getType());
            return Collections.emptySet();
        }

        HetconsMessage hetconsMessage = block.getHetconsBlock().getHetconsMessage();

        if (!verifySignature(block.getHetconsBlock()))
            return Collections.emptySet();

        try {
            switch (hetconsMessage.getType()) {
                case M1a:
                    if (hetconsMessage.hasM1A())
                        handle1a(hetconsMessage.getM1A(), block);
                    else
                        throw new HetconsException(HetconsErrorCode.NO_1A_MESSAGES);
                    break;
                case M1b:
                    if (hetconsMessage.hasM1B())
                        handle1b(hetconsMessage.getM1B(), hetconsMessage.getIdentity(), block);
                    else
                        throw new HetconsException(HetconsErrorCode.NO_1B_MESSAGES);
                    break;
                case M2b:
                    if (hetconsMessage.hasM2B())
                        handle2b(hetconsMessage.getM2B(), hetconsMessage.getIdentity(), block);
                    else
                        throw new HetconsException(HetconsErrorCode.NO_2B_MESSAGES);
                    break;
                case OBSERVERGROUP:
//                    // logger.info(String.format("Receive Observer group block %s", block.getHetconsMessage()));
                    // logger.info("Receive Observer group block");
                    storeNewBlock(block);
//                    broadcastBlock(block);
                    broadCastObserverGroupBlock(block);
                    break;
                case UNRECOGNIZED:
                    throw new HetconsException(HetconsErrorCode.EMPTY_MESSAGE);
            }
        } catch (HetconsException ex) {
            ex.printStackTrace();
            return Collections.emptySet();
        }
        //storeNewBlock(block);
        return Collections.emptySet();
    }

    /**
     * Handle 1a message
     * Set status
     *
     * @param message1a
     */
    private void handle1a(HetconsMessage1a message1a, Block block) {
        if (!message1a.hasProposal())
            return;

        HetconsProposal proposal = message1a.getProposal();
        HetconsObserverGroup observerGroup;
        try {
            observerGroup = this.getBlock(block.getHetconsBlock().getHetconsMessage().getObserverGroupReferecne())
                    .getHetconsBlock().getHetconsMessage().getObserverGroup();
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

//        logger.info("Got 1A");

        if (proposal.getTimeout() != 0) {
            HetconsProposal proposalCopy = HetconsProposal.newBuilder(proposal).setTimeout(0).build();

            HetconsMessage1a message1aCopy = HetconsMessage1a.newBuilder(message1a).setProposal(proposalCopy).build();

            HetconsMessage messageCopy = HetconsMessage.newBuilder(block.getHetconsBlock().getHetconsMessage()).setM1A(message1aCopy)
                    .setIdentity(getConfig().getCryptoId())
                    .build();

            HetconsBlock hetconsBlockCopy = HetconsBlock.newBuilder().setHetconsMessage(messageCopy)
                    .setSig(
                            SignatureUtil.signBytes(getConfig().getKeyPair(), messageCopy)
                    ).build();

            Block blockCopy = Block.newBuilder().setHetconsBlock(hetconsBlockCopy).build();

            block = blockCopy;
            storeNewBlock(block);
        }

        final Block inputBlock = block;
        List<CryptoId> observerIDs = new ArrayList<>();
        // FIXME: Concurrency
        // TODO: parallel receive1a
        observerGroup.getObserversList().forEach(o -> {
            String name = getConfig().getContact(o.getId()).getUrl() + ":" + getConfig().getContact(o.getId()).getPort();
            HetconsObserverStatus observerStatus = new HetconsObserverStatus(o, this, name);
            observers.putIfAbsent(HetconsUtil.cryptoIdToString(o.getId()), observerStatus);
            observerIDs.add(o.getId());
        });
        restartTimers.putIfAbsent(HetconsUtil.buildConsensusId(proposal.getSlotsList()), new HetconsRestartStatus(observerIDs));
//            executorService.submit(() -> {
        observerGroup.getObserversList().forEach(o -> {
            if (!o.getId().equals(getConfig().getCryptoId()))
                return;
            HetconsObserverStatus observerStatus = observers.get(HetconsUtil.cryptoIdToString(o.getId()));
//            if (observerStatus.getProposalStatus().containsKey(HetconsUtil.buildConsensusId(proposal.getSlotsList()))
//            && proposal.getTimeout() != 0) {
//                return;
//            }
            executorService.submit(() -> {
                observerStatus.receive1a(inputBlock,
                        proposal.getTimeout(),
                        o.getQuorumsList(),
                        HetconsUtil.buildChainID(observerGroup.getRootsList()));
                // logger.info("RETURN FROM RECEIVE1A");
//            });
        });
            });
        // logger.info("# of threads in pool 1a is " + executorService.getActiveCount() + "/" + executorService.getCompletedTaskCount());
    }


    /**
     * handle 1b messsage, if enough 1b received, send out a 2b message and start restart timer.
     * @param message1b
     * @param id
     * @param block
     */
    private void handle1b(HetconsMessage1b message1b, CryptoId id, Block block) {

//        logger.info("Got M1B:\n");

        HetconsObserverGroup observerGroup;
        try {
            observerGroup = this.getBlock(block.getHetconsBlock().getHetconsMessage().getObserverGroupReferecne())
                    .getHetconsBlock().getHetconsMessage().getObserverGroup();
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

//            executorService1b.submit(() -> {
        observerGroup.getObserversList().forEach(o -> {
            if (!o.getId().equals(getConfig().getCryptoId()))
                return;
            HetconsObserverStatus observerStatus = observers.get(HetconsUtil.cryptoIdToString(o.getId()));
            if (observerStatus == null) {
                logger.warning("Got m1b but no such observer");
                return;
            }
            executorService1b.submit(() -> {
                observerStatus.receive1b(block);
                // logger.info("RETURN FROM RECEIVE1B");
//                return;
//            });
        });
            });
        // logger.info("# of threads in pool 1b is " + executorService1b.getActiveCount() + "/" + executorService1b.getCompletedTaskCount());
//        if (executorService1b.getActiveCount() > 100) {
            // logger.info("larger than 100");
//        }
    }


    /**
     * handle 2b message.
     * @param message2b
     * @param id
     * @param block
     */
    private void handle2b(HetconsMessage2ab message2b, CryptoId id, Block block) {
        HetconsObserverGroup observerGroup;
        try {
            observerGroup = this.getBlock(block.getHetconsBlock().getHetconsMessage().getObserverGroupReferecne())
                    .getHetconsBlock().getHetconsMessage().getObserverGroup();
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

//            executorService2b.submit(() -> {
        observerGroup.getObserversList().forEach(o -> {
            if (!o.getId().equals(getConfig().getCryptoId()))
                return;
            HetconsObserverStatus observerStatus = observers.get(HetconsUtil.cryptoIdToString(o.getId()));
            if (observerStatus == null) {
                logger.warning("Got m2b but no such observer");
                return;
            }
            executorService2b.submit(() -> {
                observerStatus.receive2b(block);
                // logger.info("RETURN FROM RECEIVE2B");
//                return;
//            });
        });
        // logger.info("# of threads in pool 2b is " + executorService2b.getActiveCount() + "/" + executorService2b.getCompletedTaskCount());
            });
    }


    /**
     * This broadcast method only sends messages to servers.
     * @param block the block to send
     */
    @Override
    public void broadcastBlock(Block block) {
        for (Contact contact : getConfig().getContacts().values()) {
            if (!contact.getJsonContact().isClient()) {
                contact.getCharlotteNodeClient().sendBlock(block);
//                sendBlock(contact.getCryptoId(), block);
            }
        }
    }

    private void broadCastObserverGroupBlock(Block block) {
        HashSet<CryptoId> participants = new HashSet<>();
        String chainName = HetconsUtil.buildChainID(block.getHetconsBlock().getHetconsMessage().getObserverGroup().getRootsList());
        block.getHetconsBlock().getHetconsMessage().getObserverGroup().getObserversList().forEach(o -> {
            participants.addAll(new HetconsQuorumStatus(o.getQuorumsList(), chainName).getParticipants());
        });
        participants.forEach(m -> {
            sendBlock(m, block);
        });
    }

    private boolean verifySignature(HetconsBlock block) {
        return SignatureUtil.checkSignature(block.getHetconsMessage(), block.getSig());
    }

    /*
     * Only send block with same hetcons messages at most once
     * @param cryptoid identifies the server we want to send to
     * @param block the block we want to send
     * @return
     */
    @Override
    public synchronized boolean sendBlock(CryptoId cryptoid, Block block) {
        if (!sentBlocSet.containsKey(cryptoid))
            sentBlocSet.put(cryptoid, new HashSet<>());
        if (sentBlocSet.get(cryptoid).add(block.getHetconsBlock().getHetconsMessage())) {
            HetconsMessage message = block.getHetconsBlock().getHetconsMessage();
            if (message.getType() != HetconsMessageType.OBSERVERGROUP && message.getType() != HetconsMessageType.M1a) {
                if (sentBlocks.containsKey(message))
                    block = sentBlocks.get(message);
                else {
                    HetconsBlock uniqueBlock = HetconsBlock.newBuilder()
                            .setHetconsMessage(message)
                            .setSig(SignatureUtil.signBytes(getConfig().getKeyPair(), message))
                            .build();
                    block = Block.newBuilder().setHetconsBlock(uniqueBlock).build();
                    sentBlocks.put(message, block);
                }
            }
//            Logger.getLogger(getClass().getName()).info("send " +
//                    block.getHetconsBlock().getHetconsMessage().getType() +
//                    " to " + getConfig().getContact(cryptoid).getPort());
            return super.sendBlock(cryptoid, block);
        }
//        logger.info("Duplicated block " + block.getHetconsBlock().getHetconsMessage().getType());
        return true;
    }

    public boolean hasBlock(Reference reference) {
        return this.getBlockMap().get(reference) != null;
    }

    public Map<String, HetconsRestartStatus> getRestartTimers() {
        return restartTimers;
    }

    public ThreadPoolExecutor getExecutorService() {
        return executorService;
    }

    public ThreadPoolExecutor getExecutorServiceRestart() {
        return executorServiceRestart;
    }

    /**
     * Invoked whenever an observer reaches a decision.
     * Extending classes may find it useful to Override this.
     * Note that this may be called multiple times for the same consensus, as more 2bs arrive.
     * This implementation does nothing.
     * @param quoraMembers The quora satisfied by the 2b messages known.
     */
    protected void onDecision(final HetconsObserverQuorum quoraMembers,
                              final Collection<Reference> quoraMessages) {}

    public void onAttestationReceived(IntegrityAttestation.HetconsAttestation attestation) {
        if (observers.containsKey(HetconsUtil.cryptoIdToString(attestation.getObservers(0)))) {
//            observers.get(HetconsUtil.cryptoIdToString(attestation.getObservers(0))).decideSlots(attestation);

            observers.get(HetconsUtil.cryptoIdToString(getConfig().getCryptoId())).decideSlots(attestation);
        }
    }

    /**
     * If given slot has an attestation from the given observer, then save that attaestion on the behalf of current server.
     * @param slot
     * @return
     */
    protected IntegrityAttestation.HetconsAttestation hasAttestation(IntegrityAttestation.ChainSlot slot, CryptoId observer) {
        return null;
    }


    public Map<String, HetconsSlotStatus> getObserverSlotStatus(CryptoId id) {
        String _id = HetconsUtil.cryptoIdToString(id);
        if (observers.containsKey(_id))
            return observers.get(_id).getSlotStatus();
        return null;
    }

    public Object getCrossObserverSlotLock() {
        return crossObserverSlotLock;
    }

    /**
     * Make modify to the slots status cross all observers atomically.
     * @param function
     */
    public void lockedModifySlotStatus(Runnable function) {
        synchronized (crossObserverSlotLock) {
            function.run();
        }
    }

    public void abortProposal(String proposalID) {
        // System.err.println(proposalID+" Aborted");
        observers.get(HetconsUtil.cryptoIdToString(getConfig().getCryptoId())).abortProposal(proposalID);
        // System.err.println(proposalID+" return from aborted");
    }
}
