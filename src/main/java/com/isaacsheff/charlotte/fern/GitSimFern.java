package com.isaacsheff.charlotte.fern;

import static com.google.protobuf.util.Timestamps.fromMillis;
import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
import static com.isaacsheff.charlotte.node.SignatureUtil.checkSignature;
import static com.isaacsheff.charlotte.node.SignatureUtil.signBytes;
import static java.lang.System.currentTimeMillis;

import com.isaacsheff.charlotte.collections.ConcurrentHolder;
import com.isaacsheff.charlotte.node.CharlotteNode;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.FernGrpc.FernImplBase; import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.proto.IntegrityAttestation;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.ChainSlot;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.GitSimBranch;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.SignedGitSimBranch;
import com.isaacsheff.charlotte.proto.IntegrityPolicy;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationResponse;
import com.isaacsheff.charlotte.proto.SignedGitSimCommit.GitSimCommit.GitSimParents.GitSimParent;

import io.grpc.ServerBuilder;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * A Fern server that runs agreement.
 * That is to say: this Fern server will, when asked, commit to a block in a slot on a chain, and never contradict itself.
 * If you ask it for commitments to the same slot with different blocks, it will keep referring you to the same attestation,
 *  where it commits to one block.
 *
 * <p>
 * Can be run as a main class with: AgreementFernService configFileName.yaml
 * </p>
 *
 * <p>
 * Future extensions may wish to override validPolicy, as it designates
 *  (in a vacuum, so to speak), what policies this Fern server
 *   considers acceptable.
 * Alternatively, requestIntegrityAttestation contains almost all the
 *  functionality (it calls validPolicy), so you could override that
 *  to completely change what the server does.
 * </p>
 *
 * <p>
 * Furthermore, future extensions may wish to override newResponse and
 *  newAttestation (called by requestIntegrityAttestation), which govern
 *  how responses are made to new requests (which don't conflict with anything
 *  seen so far). 
 * By default, newResponse just calls newAttestation and makes a simple
 *  reference to the attestation block.
 * </p>
 * @author Isaac Sheff
 */
public class GitSimFern extends AgreementFernService {

  /** Use logger for logging events in this class. */
  private static final Logger logger = Logger.getLogger(GitSimFern.class.getName());

  private final ConcurrentMap<String, Hash> latestCommits;

  /**
   * Run as a main class with an arg specifying a config file name to run a Fern Agreement server.
   * creates and runs a new CharlotteNode which runs a Wilbur Service and a CharlotteNodeService, in a new thread.
   * @param args command line args. args[0] should be the name of the config file
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
   * Get a new one of these Fern services using this local node.
   * @param node the local CharlotteNodeService
   * @return a new AgreementFernService
   */
  public static FernImplBase newFern(final CharlotteNodeService node) {
    return new GitSimFern(node);
  }

  /**
   * @param node a CharlotteNodeService with which we'll build a AgreementFernService
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final CharlotteNodeService node) {
    return new CharlotteNode(node,
                             ServerBuilder.forPort(node.getConfig().getPort()).addService(newFern(node)),
                             node.getConfig().getPort());
  }

  /**
   * @param configFilename the name of the configuration file for this CharlotteNode
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final Path configFilename) {
    return getFernNode(new CharlotteNodeService(configFilename));
  }

  /**
   * @param configFilename the name of the configuration file for this CharlotteNode
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final String configFilename) {
    return getFernNode(new CharlotteNodeService(configFilename));
  }
  /**
   * Make a new Fern with these attributes.
   * @param node the local CharlotteNodeService used to send and receive blocks 
   * @param latestCommits If we've seen a request for a given ChainSlot, this stores the response 
   */
  public GitSimFern(final CharlotteNodeService node,
                    final ConcurrentMap<String, Hash> latestCommits){
    super(node, new ConcurrentHashMap<ChainSlot, ConcurrentHolder<RequestIntegrityAttestationResponse>>());
    this.latestCommits = latestCommits;
  }

  /**
   * Make a new Fern with this node and no known commitments.
   * @param node the local CharlotteNodeService used to send and receive blocks 
   */
  public GitSimFern(final CharlotteNodeService node) {
    this(node, new ConcurrentHashMap<String, Hash>());
  }

  /** @return the Hash of the latest block this Fern server has attested on each branch **/
  public ConcurrentMap<String, Hash> getLatestCommits() {return latestCommits;}

  /**
   * @param branch the git branch in question
   * @return the most recent block (Hash) committed to that branch, or null, if there is none
   */
  public Hash getLatestCommit(String branch) { return getLatestCommits().get(branch);}

  /**
   * Is this policy, alone, one which this server could ever accept?.
   * @return an error string if it's unacceptable, null if it's acceptable
   */
  @Override
  public String validPolicy(IntegrityPolicy policy) {
    if (!policy.getFillInTheBlank().getSignedGitSimBranch().hasGitSimBranch()) {
      return "The SignedGitSimBranch has no actual GitSimBranch";
    }
    if (!policy.getFillInTheBlank().getSignedGitSimBranch().getGitSimBranch().hasCommit()) {
      return "The SignedGitSimBranch has no actual Commit";
    }
    if (!policy.getFillInTheBlank().getSignedGitSimBranch().getGitSimBranch().getCommit().hasHash()) {
      return "The SignedGitSimBranch has a Commit with no actualy block Hash in it.";
    }
    if (policy.getFillInTheBlank().getSignedGitSimBranch().getGitSimBranch().getBranchName().equals("")) {
      return "The SignedGitSimBranch has no actual Branch name";
    }

    // wait for the referenced commit to arrive
    final Block commit = getNode().getBlock(policy.getFillInTheBlank().getSignedGitSimBranch().getGitSimBranch().getCommit());
    //check if the referenced commit is legit.
    if (!commit.hasSignedGitSimCommit()) {
      return "Referenced block is not a git commit:\nPOLICY:\n"+policy+"\nREFERENCED BLOCK:\n"+commit;
    }
    if (!commit.getSignedGitSimCommit().hasCommit()) {
      return "Referenced block has no Commit:\nPOLICY:\n"+policy+"\nREFERENCED BLOCK:\n"+commit;
    }
    if (!commit.getSignedGitSimCommit().hasSignature()) {
      return "Referenced block has no Signature:\nPOLICY:\n"+policy+"\nREFERENCED BLOCK:\n"+commit;
    }
    if (!checkSignature(commit.getSignedGitSimCommit().getCommit(),
                        commit.getSignedGitSimCommit().getSignature())) {
      return "The SignedGitSimCommit signature does not verify correctly.";
    }

    return null;
  }

  /**
   * Called when we actually will indeed create an integrity attestation.
   * It just signs the GitSimBranch, sends off that IntegrityAttestation, and returns a response with a reference to it.
   * @param request the over-the-wire request for the IntegrityAttestation, which must have a valid GitSimBranch field.
   * @return a RequestIntegrityAttestationResponse referencing the newly minted attestation.
   */
  public RequestIntegrityAttestationResponse createIntegrityAttestation(final RequestIntegrityAttestationInput request) {
    final GitSimBranch gitSimBranch =
      GitSimBranch.newBuilder(request.getPolicy().getFillInTheBlank().getSignedGitSimBranch().getGitSimBranch()).
                   setTimestamp(fromMillis(currentTimeMillis())).build();
    final Block block = Block.newBuilder().setIntegrityAttestation(
            IntegrityAttestation.newBuilder().setSignedGitSimBranch(
              SignedGitSimBranch.newBuilder().
                setGitSimBranch(gitSimBranch).
                setSignature(signBytes(getNode().getConfig().getKeyPair(), gitSimBranch))
            )
          ).build();
    getNode().onSendBlocksInput(block); // distribute the block
    return RequestIntegrityAttestationResponse.newBuilder().setReference(
             Reference.newBuilder().setHash(sha3Hash(block))
           ).build();
  }


  /**
   * @param request details what we want attested to
   * @return RequestIntegrityAttestationResponse featues an error message or a reference to an attestation.
   */
  @Override
  public RequestIntegrityAttestationResponse requestIntegrityAttestation(final RequestIntegrityAttestationInput request) {
    if (!request.hasPolicy()) {
      return RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(
               "There is no policy in this RequestIntegrityAttestationInput.").build();
    }
    if (!request.getPolicy().hasFillInTheBlank()) {
      return RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(
               "The policy in this RequestIntegrityAttestationInput isn't FillInTheBlank.").build();
    }
    if (!request.getPolicy().getFillInTheBlank().hasSignedGitSimBranch()) {
      return RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(
               "The policy in this RequestIntegrityAttestationInput isn't SignedGitSimBranch.").build();
    }
    final String validPolicyCheck = validPolicy(request.getPolicy());
    if (validPolicyCheck != null) {
      return RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(validPolicyCheck).build();
    }

    // wait for the commit block referenced to appear
    final Block commit = getNode().getBlock(request.getPolicy().getFillInTheBlank().getSignedGitSimBranch().getGitSimBranch().getCommit());

    boolean successfulReplacement = false;
    while(!successfulReplacement) {
      // If there is no known priorCommit, insert this one.
      // Otherwise, fetch the known priorCommit
      final Hash priorCommit = getLatestCommits().putIfAbsent(
        request.getPolicy().getFillInTheBlank().getSignedGitSimBranch().getGitSimBranch().getBranchName(),
        request.getPolicy().getFillInTheBlank().getSignedGitSimBranch().getGitSimBranch().getCommit().getHash());
      if (priorCommit == null) {
        return createIntegrityAttestation(request);
      }
      // If the prior commit is exactly this commit, we can feel free to commit again to the same thing.
      if (priorCommit.equals(request.getPolicy().getFillInTheBlank().getSignedGitSimBranch().getGitSimBranch().getCommit().getHash())) {
        return createIntegrityAttestation(request);
      }
      if (!commit.getSignedGitSimCommit().getCommit().hasParents()) {
        return RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(
               "We have a prior commit for this branch, but this commit lists no parents.").build();
      }

      // Breadth-first search for our previous commit in this commit's heritage
      // Then we try to atomically compare-and-swap the "previous commit" value.
      final Queue<GitSimParent> queue = new LinkedList<GitSimParent>(commit.getSignedGitSimCommit().getCommit().getParents().getParentList());
      boolean pathFound = false;
      while ((!pathFound) && (!queue.isEmpty())) {
        final GitSimParent parent = queue.remove();
        if (parent.hasParentCommit()) {
          if (parent.getParentCommit().hasHash()) {
            if (parent.getParentCommit().getHash() == priorCommit) {
              pathFound = true;
            } else {
              final Block parentBlock = getNode().getBlockMap().get(parent.getParentCommit().getHash());
              if (parentBlock != null) {
                if (parentBlock.hasSignedGitSimCommit()) {
                  if (parentBlock.getSignedGitSimCommit().hasCommit()) {
                    if (parentBlock.getSignedGitSimCommit().getCommit().hasParents()) {
                      for (GitSimParent newParent : parentBlock.getSignedGitSimCommit().getCommit().getParents().getParentList()) {
                        queue.add(newParent);
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      if (!pathFound) {
        return RequestIntegrityAttestationResponse.newBuilder().setErrorMessage(
               "I was unable to find a lineage from my previous commit on this branch to this new commit.").build();
      }
      // atomic compare and swap, returns boolean success
      successfulReplacement = getLatestCommits().replace(
        request.getPolicy().getFillInTheBlank().getSignedGitSimBranch().getGitSimBranch().getBranchName(),
        priorCommit,
        request.getPolicy().getFillInTheBlank().getSignedGitSimBranch().getGitSimBranch().getCommit().getHash());
    }
    return createIntegrityAttestation(request);
  }
}
