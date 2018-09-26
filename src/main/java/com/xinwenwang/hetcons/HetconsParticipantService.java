package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.node.HashUtil;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.yaml.Config;
import com.xinwenwang.hetcons.config.HetconsConfig;

import java.util.*;
import java.util.logging.Logger;

public class HetconsParticipantService extends CharlotteNodeService {

    private static final Logger logger = Logger.getLogger(HetconsParticipantService.class.getName());

    private HashMap<String, HetconsObserverStatus> observers;


    public HetconsParticipantService(Config config) {
        super(config);
        observers = new HashMap<>();
    }

    public Iterable<SendBlocksResponse> onSendBlocksInput(SendBlocksInput input) {
        if (!input.hasBlock()) {
            //TODO: handle error
            return super.onSendBlocksInput(input);
        }

        Block block = input.getBlock();

        if (this.getBlockMap().containsKey(HashUtil.sha3Hash(block)))
            return new ArrayList<>();

        if (!block.hasHetconsMessage()) {
            //TODO: handle error
            return super.onSendBlocksInput(input);
        }

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

        logger.info("Got 1A");

        // FIXME: Concurrency
        // TODO: parallel receive1a
        observerGroup.getObserversList().forEach(o -> {
            HetconsObserverStatus observerStatus = new HetconsObserverStatus(o, this);
            observers.putIfAbsent(HetconsUtil.cryptoIdToString(o.getId()), observerStatus);
            observerStatus = observers.get(HetconsUtil.cryptoIdToString(o.getId()));
            if (!observerStatus.receive1a(block))
                return;
        });
    }

    private void handle1b(HetconsMessage1b message1b, CryptoId id, Block block) {

        logger.info("Got M1B:\n");

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
            observerStatus.receive1b(block);
        });


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
                logger.warning("Got m1b but no such observer");
                return;
            }
            observerStatus.receive2b(block);
        });
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
        block.getHetconsMessage().getObserverGroup().getObserversList().forEach(o -> {
            o.getQuorumsList().forEach(q -> {
                q.getMemebersList().forEach(m -> {
                    sendBlock(m, block);
                });
            });
        });
    }

    /**
     * Invoked whenever an observer reaches a decision.
     * Extending classes may find it useful to Override this.
     * Note that this may be called multiple times for the same consensus, as more 2bs arrive.
     * This implementation does nothing.
     * @param quoraMembers The quora satisfied by the 2b messages known.
     * @param message2b the actual message that triggered this decision.
     * @param id the CryptoId of the sender of the most recent 2b.
     */
    protected void onDecision(final HetconsObserverQuorum quoraMembers,
                              final Collection<Reference> quoraMessages) {}
}