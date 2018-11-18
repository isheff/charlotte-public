package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.Config;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

public class HetconsParticipantService extends CharlotteNodeService {

    // private static final Logger logger = Logger.getLogger(HetconsParticipantService.class.getName());
    private static final Logger logger = Logger.getLogger(CharlotteNodeService.class.getName());

    // Map from observer crypto id to observers
    private HashMap<String, HetconsObserverStatus> observers;

    private  ThreadPoolExecutor executorService;

    private final Integer arrivingBlockLock = 0;


    public HetconsParticipantService(Config config) {
        super(config);
        observers = new HashMap<>();
        executorService = (ThreadPoolExecutor) Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 3);
//        logger.setUseParentHandlers(false);
//        SimpleFormatter fmt = new SimpleFormatter();
//        StreamHandler sh = new StreamHandler(System.out, fmt);
//        logger.addHandler(sh);
    }

    public Iterable<SendBlocksResponse> onSendBlocksInput(Block block) {
//        if (!input.hasBlock()) {
//            //TODO: handle error
//            return super.onSendBlocksInput(input.getBlock());
//        }
//
//        Block block = input.getBlock();

        logger.info("Block arrived " + block.getHetconsMessage().getType());

//        synchronized (this) {


//        }

        if (!block.hasHetconsMessage()) {
            //TODO: handle error
            return Collections.emptySet();
        }

//        synchronized (arrivingBlockLock) {
            if (!storeNewBlock(block)) {
                logger.info("Discard duplicated block " + block.getHetconsMessage().getType());
                return new ArrayList<>();
            }
//        }

        HetconsMessage hetconsMessage = block.getHetconsMessage();

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
//                    logger.info(String.format("Receive Observer group block %s", block.getHetconsMessage()));
                    logger.info("Receive Observer group block");
                    storeNewBlock(block);
                    broadCastObserverGroupBlock(block);
                    break;
                case UNRECOGNIZED:
                    throw new HetconsException(HetconsErrorCode.EMPTY_MESSAGE);
            }
        } catch (HetconsException ex) {
            ex.printStackTrace();
            return new ArrayList<>();
        }
        //storeNewBlock(block);
        return new ArrayList<>();
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
            observerGroup = this.getBlock(block.getHetconsMessage().getObserverGroupReferecne())
                    .getHetconsMessage().getObserverGroup();
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

//        logger.info("Got 1A");

        // FIXME: Concurrency
        // TODO: parallel receive1a
        observerGroup.getObserversList().forEach(o -> {
            String name = getConfig().getContact(o.getId()).getUrl() + ":" + getConfig().getContact(o.getId()).getPort();
            HetconsObserverStatus observerStatus = new HetconsObserverStatus(o, this, name);
            observers.putIfAbsent(HetconsUtil.cryptoIdToString(o.getId()), observerStatus);
        });
        observerGroup.getObserversList().forEach(o -> {
            HetconsObserverStatus observerStatus = observers.get(HetconsUtil.cryptoIdToString(o.getId()));
            executorService.submit(() -> {
                observerStatus.receive1a(block);
                logger.info("RETURN FROM RECEIVE1A");
                return;
            });
        });
        logger.info("# of threads in pool is " + executorService.getActiveCount() + "/" + executorService.getCompletedTaskCount());
    }

    private void handle1b(HetconsMessage1b message1b, CryptoId id, Block block) {

//        logger.info("Got M1B:\n");

        HetconsObserverGroup observerGroup;
        try {
            observerGroup = this.getBlock(block.getHetconsMessage().getObserverGroupReferecne())
                    .getHetconsMessage().getObserverGroup();
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        observerGroup.getObserversList().forEach(o -> {
            HetconsObserverStatus observerStatus = observers.get(HetconsUtil.cryptoIdToString(o.getId()));
            if (observerStatus == null) {
                logger.warning("Got m1b but no such observer");
                return;
            }
            executorService.submit(() -> {
                observerStatus.receive1b(block);
                logger.info("RETURN FROM RECEIVE1B");
                return;
            });
        });
        logger.info("# of threads in pool is " + executorService.getActiveCount() + "/" + executorService.getCompletedTaskCount());
        if (executorService.getActiveCount() > 100) {
            logger.info("larger than 100");
        }
    }

    private void handle2b(HetconsMessage2ab message2b, CryptoId id, Block block) {
        HetconsObserverGroup observerGroup;
        try {
            observerGroup = this.getBlock(block.getHetconsMessage().getObserverGroupReferecne())
                    .getHetconsMessage().getObserverGroup();
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        observerGroup.getObserversList().forEach(o -> {
            HetconsObserverStatus observerStatus = observers.get(HetconsUtil.cryptoIdToString(o.getId()));
            if (observerStatus == null) {
                logger.warning("Got m2b but no such observer");
                return;
            }
            executorService.submit(() -> {
                observerStatus.receive2b(block);
                logger.info("RETURN FROM RECEIVE2B");
                return;
            });
        });
        logger.info("# of threads in pool is " + executorService.getActiveCount() + "/" + executorService.getCompletedTaskCount());
    }


    private String formatConsensus(ArrayList<HetconsObserverQuorum> quorum) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < quorum.size(); i++) {
            HetconsObserverQuorum q = quorum.get(i);
            stringBuilder.append(String.format("Quorum %d has receive message from %d members\n", i, q.getMemebersCount()));
            q.getMemebersList().forEach(m -> {
                stringBuilder.append(String.format("\t%s\n", HetconsUtil.cryptoIdToString(m)));
            });
        }
        return stringBuilder.toString();
    }

    private void broadCastObserverGroupBlock(Block block) {
        HashSet<CryptoId> participants = new HashSet<>();
        block.getHetconsMessage().getObserverGroup().getObserversList().forEach(o -> {
            o.getQuorumsList().forEach(q -> {
                q.getMemebersList().forEach(m -> {
                    participants.add(m);
                });
            });
        });
        participants.forEach(m -> {
            sendBlock(m, block);
        });
    }

    public boolean hasBlock(Reference reference) {
        return this.getBlockMap().get(reference) != null;
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
}