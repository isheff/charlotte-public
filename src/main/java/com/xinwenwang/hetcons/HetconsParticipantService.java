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
            return super.onSendBlocksInput(input);

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
                    storeNewBlock(block);
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

        observerGroup.getObserversList().forEach(o -> {
            HetconsObserverStatus observerStatus = new HetconsObserverStatus(o, this);
            observerStatus = observers.putIfAbsent(HetconsUtil.cryptoIdToString(o.getId()), observerStatus);
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


        // validate 1bs
        String statusKey = buildConsensusId(message1b.getM1A().getProposal().getSlotsList());
        HetconsProposalStatus status = getStatus(statusKey);

        if (!validateStatus(status, message1b.getM1A().getProposal(), false))
            return;

        if (!storeNewBlock(block))
            return;

        // add this message to the 1b map
        ArrayList<HetconsObserverQuorum> quora = status.receive1b(id, message1b);

        if (quora.isEmpty())
            return;

        // prepare for 2a 2b
        HashMap<CryptoId, HetconsMessage2ab> message2abs = new HashMap<>();

        quora.forEach(quorum -> {
            message2abs.putIfAbsent(
                    quorum.getOwner(),
                    HetconsMessage2ab.newBuilder()
                            .setProposal(message1b.getM1A().getProposal())
                            .setValue(message1b.getValue())
                            .setQuorumOf1Bs(status.get1bQuorumRef(quorum))
                            .build());
        });

        // save a 2a
        // send out 2bs to observes and broadcast to all participants
        message2abs.forEach((cryptoId, message2ab) -> {
            status.addM2A(cryptoId, message2ab);
            Block m2abblock = Block.newBuilder()
                    .setHetconsMessage(HetconsMessage.newBuilder()
                            .setIdentity(this.getConfig().getCryptoId())
                            .setSig(SignatureUtil.signBytes(this.getConfig().getKeyPair(), message2ab.toByteString()))
                            .setType(HetconsMessageType.M2b)
                            .setM2B(message2ab)
                            .build())
                    .build();

//            sendBlock(cryptoId,block);
            broadcastHetconsMessageBlocks(status, m2abblock);
        });

        status.setStage(HetconsConsensusStage.M2BSent);
        logger.info("Sent M2B:\n");

        if (status.getM2bTimer() != null)
            return;

        status.setM2bTimer(new Timer());
        status.getM2bTimer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (status.getStage().equals(HetconsConsensusStage.M2BSent)) {
                    logger.info("Restart consensus on " + status.getStage().toString());
                    status.setStage(HetconsConsensusStage.HetconsTimeout);
                    propose(buildConsensusId(status.getCurrentProposal().getSlotsList()),
                            status.getHighestBallotM2A().getValue());
                    status.setM2bTimer(null);
                }
            }
        }, status.getConsensuTimeout());
    }

    private void handle2b(HetconsMessage2ab message2b, CryptoId id, Block block) {
        logger.info(String.format("Server %s Got M2B\n", this.getConfig().getMe()));
        String statusKey = buildConsensusId(message2b.getProposal().getSlotsList());
        HetconsProposalStatus status = getStatus(statusKey);

        if (!validateStatus(status, message2b.getProposal(), false))
            return;

        if (!storeNewBlock(block))
            return;
        ArrayList<HetconsObserverQuorum> quora = status.receive2b(id, message2b);

        if (quora.isEmpty())
            return;

        //TODO: Is handle 2b run in linear manner or parallel?

        status.setStage(HetconsConsensusStage.ConsensusDecided);
        String logInfo = "";
        logInfo += (String.format("Server %s finished consensus\n", this.getConfig().getMe()));
        logInfo += (String.format("Consensus decided on\nvalue: %d\n", message2b.getValue().getNum()));
        logInfo += (String.format("Ballot Number: %s\n", message2b.getProposal().getBallot().getBallotSequence()));
        logInfo += formatConsensus(quora);
        logger.info(logInfo);
        status.onDecided(statusKey);
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
        ;
        return stringBuilder.toString();
    }

    private void broadcastHetconsMessageBlocks(HetconsProposalStatus status, Block block) {
        HetconsProposal proposal = status.getCurrentProposal();
        status.getLock().readLock().lock();
        try {
            HetconsProposal updatedProposal = status.getCurrentProposal();
            if (proposal.getBallot().getBallotSequence().compareTo(updatedProposal.getBallot().getBallotSequence()) < 0)
                return;
            status.getParticipants().forEach((k, v) -> {
                if (status.getParticipantIds().get(k) == null)
                    System.out.printf("k:%s\nsize: %d\n", k, status.getParticipantIds().size());
                sendBlock(status.getParticipantIds().get(k), block);
            });
        } finally {
            status.getLock().readLock().unlock();
        }
    }
}
