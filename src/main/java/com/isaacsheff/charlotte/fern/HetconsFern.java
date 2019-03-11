package com.isaacsheff.charlotte.fern;   

import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Set;

import com.isaacsheff.charlotte.collections.BlockingConcurrentHashMap;
import com.isaacsheff.charlotte.collections.BlockingMap;
import com.isaacsheff.charlotte.collections.ConcurrentHolder;
import com.isaacsheff.charlotte.experiments.HetconsExperimentFernNode;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.HetconsParticipantNodeForFern;
import com.isaacsheff.charlotte.node.SignatureUtil;
import com.isaacsheff.charlotte.proto.*;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.ChainSlot;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.HetconsAttestation;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.SignedChainSlot;
import com.isaacsheff.charlotte.yaml.Config;
import com.sun.net.httpserver.Filter;
import com.xinwenwang.hetcons.HetconsUtil;
import com.xinwenwang.hetcons.config.HetconsConfig;

import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

/**
 * A Fern Server built around Hetcons.
 * The idea here is that the HetconsParticipantService services (which
 *  act as CharlotteNode services) run Hetcons, and this interracts
 *  with clients, issuing attestations.
 * This can be run as a main class with "HetconsFern hetconsConfig.yaml"
 *
 * This server issues 2 kinds of attestations:
 * <ul>
 * <li>
 * Hetcons Attestations merely reference some 2B message blocks.
 * In principle, these communicate to a client (that understands
 *  Hetcons) enough information to work out what was decided.
 * The "value" decided is the one found in each of the 2B messages.
 * </li>
 * <li>
 * AgreementAttestations, in which the signer agrees never to sign a
 *  conflicting attestation (in this case naming a block as the
 *  inhabitant of a slot on a chain), are presumably easier for
 *  clients to parse.
 * This Fern server creates an AgreementAttestation whenever it
 *  witnesses a Hetcons decision that makes the observer corresponding
 *  to this node decide.
 * That is to say, if the consensus lists this node as an Observer,
 *  and this Observer decides (which hopefully happens at the end of
 *  every consensus), then this node will issue an
 *  AgreementAttestation.
 * </li>
 * </ul>
 *
 * <p>
 * Clients can issue requests for either (or both) type of attestation.
 * The server will wait until it has an attestation in order to respond.
 * If a client sends a Hetcons attestation request, that must include a
 *  1A message, which this server will put in a block, thus beginning
 *  consensus.
 * Therefore, a valid way to begin consensus is for a client to send a
 *  HetconsAttestation request to all the servers involved, thus
 *  sending them all a 1A.
 * </p>
 * @author Isaac Sheff
 */
public class HetconsFern extends AgreementFernService {

  /** Use logger for logging events in this class. */
  private static final Logger logger = Logger.getLogger(HetconsFern.class.getName());
  
  /** The HetconsNode (which is also a CharlotteNode Service) that also inhabits this server **/
  private final HetconsParticipantNodeForFern hetconsNode;

  /** All responses yet made for agreement requests for each chain slot **/
  private final BlockingMap<ChainSlot, RequestIntegrityAttestationResponse> agreementAttestationCache;

  /** All responses yet made for hetcons requests for each chain slot and observer **/
  private final ConcurrentMap<ChainSlot, BlockingMap<CryptoId, RequestIntegrityAttestationResponse>> hetconsAttestationCache;

  /**
   * Run as a main class with an arg specifying a config file name to run a Fern Hetcons server.
   * Creates and runs a new HetconsParticipantService (which is a CharlotteNodeService) which also runs this Fern Service.
   * @param args command line args. args[0] should be the name of the config file
   * @throws InterruptedException
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
   * @param fern a HetconsFern Service
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final HetconsFern fern) {
    return new CharlotteNode(fern.getNode(),
                             fern);
  }

  /**
   * @param configFilename the name of the configuration file for this CharlotteNode
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final String configFilename) {
    return getFernNode(new Config(configFilename));
  }

  /**
   * @param config the name of the configuration file for this CharlotteNode
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final Config config) {
    return getFernNode(new HetconsFern(config, new HetconsConfig()));
  }


  /**
   * Create a New HetconsFern service.
   * @param node the corresponding HetconsParticipantNodeForFern service that will pass on decisions to this service.
   */
  public HetconsFern(final HetconsParticipantNodeForFern node) {
    super(node);
    node.setFern(this);
    hetconsNode = node;
    agreementAttestationCache =
      new BlockingConcurrentHashMap<ChainSlot, RequestIntegrityAttestationResponse>();
    hetconsAttestationCache =
      new ConcurrentHashMap<ChainSlot, BlockingMap<CryptoId, RequestIntegrityAttestationResponse>>();
  }

  /**
   * Create a New HetconsFern service.
   * @param config the configuration for the underlying CharlotteNodeService
   * @param hetconsConfig the configuration for Hetcons
   * @see com.xinwenwang.hetcons.config.HetconsConfig
   */
  public HetconsFern(final Config config,
                     final HetconsConfig hetconsConfig) {
    this(new HetconsParticipantNodeForFern(config, hetconsConfig, null));
  }

  /** @return The HetconsNode (which is also a CharlotteNode Service) that also inhabits this server **/
  public HetconsParticipantNodeForFern getHetconsNode() {return hetconsNode;};

  /** @return All responses yet made for agreement requests for each chain slot **/
  public BlockingMap<ChainSlot, RequestIntegrityAttestationResponse> getAgreementAttestationCache() {
    return agreementAttestationCache;
  }

  /** @return All responses yet made for hetcons requests for each chain slot and observer **/
  public ConcurrentMap<ChainSlot, BlockingMap<CryptoId, RequestIntegrityAttestationResponse>> getHetconsAttestationCache() {
    return hetconsAttestationCache;
  }


  /**
   * Insert a value into hetconsAttestationCache atomically.
   * @param slot the ChainSlot this value is in
   * @param observer the observer for whom this value is decided
   * @param response the actual response to send to clients
   * @return the old value, or null, if there was none.
   */
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
 
  /**
   * Retrieve a value from hetconsAttestationCache, waiting, if necessary, until there is one.
   * @param slot the ChainSlot this value is in
   * @param observer the observer for whom this value is decided
   * @return the actual response to send to clients
   */
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


  /**
   * Called when Hetcons has reached a decision.
   * This will cause the Fern server to issue attestations, and populate agreementAttestationCache and hetconsAttestationCache.
   */
  public void observersDecide(final HetconsObserverQuorum q,
                              final Collection<Reference> quorum2b)  {

    final HetconsAttestation.Builder hetconsAttestationBuilder = HetconsAttestation.newBuilder();

    CryptoId observer = q.getOwner();

    hetconsAttestationBuilder.addObservers(observer);

    HetconsBallot ballot = null;
    HetconsValue value = null;
    HetconsMessage1a message1a = null;

    for (Reference r: quorum2b) {
      Block b2b = getNode().getBlock(r);
      HetconsMessage2ab m2b = b2b.getHetconsBlock().getHetconsMessage().getM2B();
      HetconsValue temp = HetconsUtil.get2bValue(m2b, getNode());
      message1a = HetconsUtil.getM1aFromReference(m2b.getM1ARef(), getNode());
      if (ballot == null && value == null) {
        ballot = message1a.getProposal().getBallot();
        value = HetconsUtil.get2bValue(m2b, getNode());
      } else {
        if (HetconsUtil.ballotCompare(
                message1a.getProposal().getBallot(),
                ballot) != 0 || (temp != null && !value.equals(temp)))
          return;
      }
    }
    hetconsAttestationBuilder.addAllMessage2B(quorum2b);
    hetconsAttestationBuilder.setAttestedValue(message1a.getProposal().getValue());
    hetconsAttestationBuilder.addAllSlots(message1a.getProposal().getSlotsList());

    final IntegrityAttestation attestation = IntegrityAttestation.newBuilder().setHetconsAttestation(hetconsAttestationBuilder).build();

    final Block hetconsAttestation = Block.newBuilder().setIntegrityAttestation(attestation).build();

    getHetconsNode().onSendBlocksInput(hetconsAttestation);

    final RequestIntegrityAttestationResponse hetconsResponse = RequestIntegrityAttestationResponse.newBuilder().
      setReference(Reference.newBuilder().setHash(sha3Hash(hetconsAttestation)))
            .setAttestation(attestation)
            .build();

    for (ChainSlot chainslot : message1a.getProposal().getSlotsList()) {
      // we're indexing strictly by root and slot.
      // that means different parents or whatever conflict.
      final ChainSlot indexableChainSlot = ChainSlot.newBuilder().
        setRoot(chainslot.getRoot()).
        setSlot(chainslot.getSlot()).
        build();

      putHetconsAttestation(indexableChainSlot, observer, hetconsResponse);
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



  /**
   * Called when a request for integrity attestation comes in over the wire.
   * This will wait for an attestation to become available, and then respond with it.
   * If the request is for a HetconsAttestation, it will also initiate consensus.
   * @param request the incoming request for an attestation from the wire.
   * @param responseObserver used to send ONE response back over the wire.
   */
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


      for (ChainSlot c : request.getPolicy().getHetconsPolicy().getProposal().getM1A().getProposal().getSlotsList()) {
        if (getHetconsNode().getNextAvailableSlot(c.getRoot()) != c.getSlot()) {
          /* return next available slots to clients for re-propose */
          responseSlotAlreadyTaken(request, responseObserver);
          return;
        }
      }
      
      // send the request out (this will just show up as a duplicate if the request is already out) as a 1A.
      HetconsBlock requestBlock = HetconsBlock.newBuilder()
              .setHetconsMessage(request.getPolicy().getHetconsPolicy().getProposal())
              .setSig(SignatureUtil.signBytes(getHetconsNode().getConfig().getKeyPair(), request.getPolicy().getHetconsPolicy().getProposal()))
              .build();
      getHetconsNode().onSendBlocksInput(
              Block.newBuilder().setHetconsBlock(requestBlock).build()
      );

      // Whichever Slot reaches consensus first for this observer, return the affiliated response
      request.getPolicy().getHetconsPolicy().getProposal().getM1A().getProposal().getSlotsList().parallelStream().
        forEach(chainslot -> {
          try {
            // This call blocks until that slot is filled
            RequestIntegrityAttestationResponse attestationResponse = getHetconsAttestation(chainslot, request.getPolicy().getHetconsPolicy().getObserver());
            if (attestationResponse.getAttestation().getHetconsAttestation().getAttestedValue().equals(request.getPolicy().getHetconsPolicy().getProposal().getM1A().getProposal().getValue())) {
              responseObserver.onNext(attestationResponse);
              responseObserver.onCompleted();
            } else {
              responseSlotAlreadyTaken(request, responseObserver);
            }
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

  /* If one or more slots in the request are taken by other proposals, then send back a list of chain slot that available */
  private void responseSlotAlreadyTaken(RequestIntegrityAttestationInput request, final StreamObserver<RequestIntegrityAttestationResponse> responseObserver) {
    List<ChainSlot> nextAvailableSlots = new ArrayList<>();
    for (ChainSlot ci : request.getPolicy().getHetconsPolicy().getProposal().getM1A().getProposal().getSlotsList()) {
      nextAvailableSlots.add(ChainSlot.newBuilder(ci).setSlot(getHetconsNode().getNextAvailableSlot(ci.getRoot())).build());
    }
    responseObserver.onNext(RequestIntegrityAttestationResponse.newBuilder().
            setErrorMessage("One or more slots have already been taken.").
            setAttestation(IntegrityAttestation.newBuilder().setHetconsAttestation(HetconsAttestation.newBuilder().addAllNextSlotNumbers(nextAvailableSlots).build()))
            .build());
    responseObserver.onCompleted();
  }
}
