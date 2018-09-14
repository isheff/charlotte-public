package com.isaacsheff.charlotte.fern;   

import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;

import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Set;

import com.isaacsheff.charlotte.collections.BlockingConcurrentHashMap;
import com.isaacsheff.charlotte.collections.BlockingMap;
import com.isaacsheff.charlotte.collections.ConcurrentHolder;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.HetconsParticipantNodeForFern;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.CryptoId;
import com.isaacsheff.charlotte.proto.HetconsProposal;
import com.isaacsheff.charlotte.proto.HetconsValue;
import com.isaacsheff.charlotte.proto.IntegrityAttestation;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.ChainSlot;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.HetconsAttestation;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.SignedChainSlot;
import com.isaacsheff.charlotte.proto.IntegrityPolicy;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationResponse;
import com.isaacsheff.charlotte.yaml.Config;
import com.xinwenwang.hetcons.config.HetconsConfig;

import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

public class HetconsFern extends AgreementFernService {

  /** Use logger for logging events in this class. */
  private static final Logger logger = Logger.getLogger(HetconsFern.class.getName());
  
  private final HetconsParticipantNodeForFern hetconsNode;

  private final BlockingMap<ChainSlot, RequestIntegrityAttestationResponse> agreementAttestationCache;
  private final ConcurrentMap<ChainSlot, BlockingMap<CryptoId, RequestIntegrityAttestationResponse>> hetconsAttestationCache;

  /**
   * Run as a main class with an arg specifying a config file name to run a Fern Timestamp server.
   * creates and runs a new CharlotteNode which runs a Fern Service
   *  and a TimestampNode service (which is a CharlotteNode Service), in a new thread.
   * @param args command line args. args[0] should be the name of the config file, args[1] is BlocksPerTimestamp
   */
  public static void main(String[] args) throws InterruptedException{
    if (args.length < 1) {
      System.out.println("Correct Usage: FernService configFileName.yaml");
      return;
    }
    final Thread thread = new Thread(getFernNode(args[0]));
    thread.start();
    logger.info("Fern service started on new thread");
    thread.join();
  }


  /**
   * @param node a TimestampFern with which we'll build a TimestampFern Service
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final HetconsFern fern) {
    return new CharlotteNode(fern.getNode(),
                             ServerBuilder.forPort(fern.getNode().getConfig().getPort()).addService(fern),
                             fern.getNode().getConfig().getPort());
  }

  /**
   * @param configFilename the name of the configuration file for this CharlotteNode
   * @param referencesPerTimestamp the number of references the node acquires to automatically request a timestamp
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final String configFilename) {
    return getFernNode(new Config(configFilename));
  }

  /**
   * @param config the name of the configuration file for this CharlotteNode
   * @param referencesPerTimestamp the number of references the node acquires to automatically request a timestamp
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final Config config) {
    return getFernNode(new HetconsFern(config, new HetconsConfig()));
  }

  public HetconsFern(final ConcurrentMap<ChainSlot,ConcurrentHolder<RequestIntegrityAttestationResponse>>commitments,
                     final HetconsParticipantNodeForFern node) {
    super(node, commitments);
    node.setFern(this);
    hetconsNode = node;
    agreementAttestationCache =
      new BlockingConcurrentHashMap<ChainSlot, RequestIntegrityAttestationResponse>();
    hetconsAttestationCache =
      new ConcurrentHashMap<ChainSlot, BlockingMap<CryptoId, RequestIntegrityAttestationResponse>>();
  }

  public HetconsFern(final HetconsParticipantNodeForFern node) {
    super(node);
    node.setFern(this);
    hetconsNode = node;
    agreementAttestationCache =
      new BlockingConcurrentHashMap<ChainSlot, RequestIntegrityAttestationResponse>();
    hetconsAttestationCache =
      new ConcurrentHashMap<ChainSlot, BlockingMap<CryptoId, RequestIntegrityAttestationResponse>>();
  }

  public HetconsFern(final ConcurrentMap<ChainSlot,ConcurrentHolder<RequestIntegrityAttestationResponse>>commitments,
                     final Config config,
                     final HetconsConfig hetconsConfig) {
    this(commitments, new HetconsParticipantNodeForFern(config, hetconsConfig, null));
  }

  public HetconsFern(final Config config,
                     final HetconsConfig hetconsConfig) {
    this(new HetconsParticipantNodeForFern(config, hetconsConfig, null));
  }

  public HetconsParticipantNodeForFern getHetconsNode() {return hetconsNode;};

  public BlockingMap<ChainSlot, RequestIntegrityAttestationResponse> getAgreementAttestationCache() {
    return agreementAttestationCache;
  }

  public ConcurrentMap<ChainSlot, BlockingMap<CryptoId, RequestIntegrityAttestationResponse>> getHetconsAttestationCache() {
    return hetconsAttestationCache;
  }

 private RequestIntegrityAttestationResponse putHetconsAttestation(final ChainSlot slot,
                                                                   final CryptoId observer,
                                                                   final RequestIntegrityAttestationResponse response) {
   final BlockingMap<CryptoId, RequestIntegrityAttestationResponse> newObserverToResponse =
     new BlockingConcurrentHashMap<CryptoId, RequestIntegrityAttestationResponse>();
   final BlockingMap<CryptoId, RequestIntegrityAttestationResponse> oldObserverToResponse =
     getHetconsAttestationCache().putIfAbsent(slot, newObserverToResponse);
   if (oldObserverToResponse == null) {
     return newObserverToResponse.put(observer, response);
   } else {
     return oldObserverToResponse.put(observer, response);
   }
 }
 
private RequestIntegrityAttestationResponse getHetconsAttestation(final ChainSlot slot, final CryptoId observer) {
   final BlockingMap<CryptoId, RequestIntegrityAttestationResponse> newObserverToResponse =
     new BlockingConcurrentHashMap<CryptoId, RequestIntegrityAttestationResponse>();
   final BlockingMap<CryptoId, RequestIntegrityAttestationResponse> oldObserverToResponse =
     getHetconsAttestationCache().putIfAbsent(slot, newObserverToResponse);
   if (oldObserverToResponse == null) {
     return newObserverToResponse.blockingGet(observer);
   } else {
     return oldObserverToResponse.blockingGet(observer);
   }
 }


  public void observersDecide(final Set<CryptoId> observers,
                              final HetconsValue value,
                              final HetconsProposal proposal)  {
    final HetconsAttestation.Builder hetconsAttestationBuilder = HetconsAttestation.newBuilder();
    final boolean selfInObservers = observers.contains(getNode().getConfig().getCryptoId());
    for (CryptoId observer : observers) {
      hetconsAttestationBuilder.addObservers(observer);
    }
    for (Block block : getHetconsNode().getReference2bsPerProposal().get(proposal)) { 
      if (block.getHetconsMessage().getM2B().getValue().equals(value)) {
        hetconsAttestationBuilder.addMessage2B(Reference.newBuilder().setHash(sha3Hash(block)));
      }
    }
    final Block hetconsAttestation = Block.newBuilder().setIntegrityAttestation(
      IntegrityAttestation.newBuilder().setHetconsAttestation(hetconsAttestationBuilder)).build();

    getNode().onSendBlocksInput(hetconsAttestation);

    final RequestIntegrityAttestationResponse hetconsResponse = RequestIntegrityAttestationResponse.newBuilder().
      setReference(Reference.newBuilder().setHash(sha3Hash(hetconsAttestation))).build();
    for (ChainSlot chainslot : proposal.getSlotsList()) {
      // we're indexing strictly by root and slot.
      // that means different parents or whatever conflict.
      final ChainSlot indexableChainSlot = ChainSlot.newBuilder().
        setRoot(chainslot.getRoot()).
        setSlot(chainslot.getSlot()).
        build();
      for (CryptoId observer : observers) {
        putHetconsAttestation(indexableChainSlot, observer, hetconsResponse);
      }
      if (selfInObservers) {
        getAgreementAttestationCache().put(indexableChainSlot, newResponse(
          IntegrityPolicy.newBuilder().setFillInTheBlank(
            IntegrityAttestation.newBuilder().setSignedChainSlot(
              SignedChainSlot.newBuilder().setChainSlot(
                ChainSlot.newBuilder(chainslot).setBlock(
                  value.getBlock()
                )
              )
            )
          ).build()
        ));

      }
    }
  }



  @Override
  public void requestIntegrityAttestation(final RequestIntegrityAttestationInput request,
                                          final StreamObserver<RequestIntegrityAttestationResponse> responseObserver) {
    if (!request.hasPolicy()) {
      responseObserver.onNext(RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(
               "There is no policy in this RequestIntegrityAttestationInput.").build());
      responseObserver.onCompleted();
    } else if (request.getPolicy().hasFillInTheBlank()
               && request.getPolicy().getFillInTheBlank().hasSignedChainSlot()
               && request.getPolicy().getFillInTheBlank().getSignedChainSlot().hasChainSlot()
               && request.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().hasRoot()) {
      responseObserver.onNext(getAgreementAttestationCache().get(
        ChainSlot.newBuilder().
          setRoot(request.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().getRoot()).
          setSlot(request.getPolicy().getFillInTheBlank().getSignedChainSlot().getChainSlot().getSlot()).
          build()));
      responseObserver.onCompleted();
    } else if (request.getPolicy().hasHetconsPolicy()
               && request.getPolicy().getHetconsPolicy().hasObserver()
               && request.getPolicy().getHetconsPolicy().hasProposal()
               && request.getPolicy().getHetconsPolicy().getProposal().hasM1A()
               && request.getPolicy().getHetconsPolicy().getProposal().getM1A().hasProposal() ) {
      
      // send the request out (this will just show up as a duplicate if the request is already out) as a 1A.
      getNode().onSendBlocksInput(
        Block.newBuilder().setHetconsMessage(request.getPolicy().getHetconsPolicy().getProposal()).build());

      // Whichever Slot reaches consensus first for this observer, return the affiliated response
      request.getPolicy().getHetconsPolicy().getProposal().getM1A().getProposal().getSlotsList().parallelStream().
        forEach(chainslot -> {
          try {
            // This call blocks until that slot is filled
            responseObserver.onNext(getHetconsAttestation(chainslot, request.getPolicy().getHetconsPolicy().getObserver()));
            responseObserver.onCompleted();
          } catch (Throwable t) {
            // This is likely to happen if multiple chainSlots are filled.
            // That's ok, we can return any one of them.
          }
        });
    } else {
      responseObserver.onNext(RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(
               "There was neither a properly formatted Agreement request nor a properly formatted Consensus request").build());
      responseObserver.onCompleted();
    }
  }
}
