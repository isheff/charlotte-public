package com.isaacsheff.charlotte.fern;

import static com.google.protobuf.util.Timestamps.fromMillis;
import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
import static com.isaacsheff.charlotte.node.SignatureUtil.checkSignature;
import static com.isaacsheff.charlotte.node.SignatureUtil.signBytes;
import static java.lang.Integer.parseInt;
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

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A Fern server that runs a kind of simulation of the Git version
 *  control system.
 * In this simulation, each Commit is a block featuring a hash of the
 *  repository contents, and diffs from (with references to) prior
 *  commits.
 * A commit with multiple prior commit references is a "merge," with
 *  none is "initial," and with one is a "regular" commit.
 *
 * <p>
 * Furthermore, any Fern server can maintain one or more "branches,"
 *  identified by branch name (a String).
 * Different servers may disagree on which commits belong in which
 *  branches.
 * Integrity Attestations here are signed statements that a given
 *  commit belongs in a given branch at a given (real) time.
 * Furthermore, a Fern server will not attest to any other commits
 *  being in the same branch, unless this commit is an ancestor of the
 *  new commit.
 * Any backtracking must be handled explicitly in the diffs.
 * </p>
 *
 * <p>
 * By default, this server will accept any commit in any branch, so
 *  long as it is initial, or a descendant of the commits it has so
 *  far in that branch.
 * However, you can override "validPolicy" (you'll probably want to
 *  call super.validPolicy), to make this server more selective.
 * For example, since commits are signed, you can allow only certain
 *  committers to commit to certain branches.
 * </p>
 *
 * <p>
 * Can be run as a main class with: GitSimFern configFileName.yaml
 * </p>
 * @author Isaac Sheff
 */
public class GitSimFern extends AgreementFernService {

  /** Use logger for logging events in this class. */
  private static final Logger logger = Logger.getLogger(GitSimFern.class.getName());

  private final ConcurrentMap<String, Hash> latestCommits;

  /**
   * Run as a main class with an arg specifying a config file name to run a Fern server.
   * creates and runs a new CharlotteNode which runs a Fern Service and a CharlotteNodeService, in a new thread.
   * @param args command line args. args[0] should be the name of the config file, and args[1] is auto-shutdown time in seconds
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
    if (args.length < 2) {
      TimeUnit.SECONDS.sleep(Integer.MAX_VALUE);
    } else {
      TimeUnit.SECONDS.sleep(parseInt(args[1]));
    }
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
   * @param node a CharlotteNodeService with which we'll build a GitSimFern service
   * @return a new CharlotteNode which runs a Fern Service and a CharlotteNodeService
   */
  public static CharlotteNode getFernNode(final CharlotteNodeService node) {
    return new CharlotteNode(node, newFern(node));
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
   * This checks:
   * <ul>
   * <li> The reference references a real block (and waits for that block).</li>
   * <li> The referenced block is properly signed.                         </li>
   * </ul>
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
   * Called whenever a request comes in over the wire.
   * If there is really a GitSim request in here, we pass it on to
   *  validPolicy, and if everything checks out, we assemble a new
   *  attestation with createIntegrityAttestation.
   * Along the way, we update our Map of most recent commits for each
   *  branch.
   * We have to take care to do this atomically.
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
