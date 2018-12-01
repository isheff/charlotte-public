package com.isaacsheff.charlotte.fern;

import static com.isaacsheff.charlotte.node.HashUtil.sha3Hash;
import static com.isaacsheff.charlotte.node.SignatureUtil.checkSignature;
import static com.isaacsheff.charlotte.node.SignatureUtil.signBytes;
import static java.util.concurrent.ConcurrentHashMap.newKeySet;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Set;

import com.google.protobuf.ByteString;
import com.isaacsheff.charlotte.node.CharlotteNodeService;
import com.isaacsheff.charlotte.proto.Block;
import com.isaacsheff.charlotte.proto.Hash;
import com.isaacsheff.charlotte.proto.IntegrityAttestation;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.GitSimBranch;
import com.isaacsheff.charlotte.proto.IntegrityAttestation.SignedGitSimBranch;
import com.isaacsheff.charlotte.proto.IntegrityPolicy;
import com.isaacsheff.charlotte.proto.Reference;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationInput;
import com.isaacsheff.charlotte.proto.RequestIntegrityAttestationResponse;
import com.isaacsheff.charlotte.proto.SendBlocksResponse;
import com.isaacsheff.charlotte.proto.Signature;
import com.isaacsheff.charlotte.proto.SignedGitSimCommit;
import com.isaacsheff.charlotte.proto.SignedGitSimCommit.GitSimCommit;
import com.isaacsheff.charlotte.proto.SignedGitSimCommit.GitSimCommit.GitSimParents;
import com.isaacsheff.charlotte.proto.SignedGitSimCommit.GitSimCommit.GitSimParents.GitSimParent;
import com.isaacsheff.charlotte.yaml.Contact;

/**
 * A Client for a Fern server that runs a kind of simulation of the
 *  Git version control system.
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
 * This client needs a local CharlotteNode to get blocks from, and
 *  communicates with a Fern server described by a Contact.
 * </p>
 *
 * @author Isaac Sheff
 */
public class GitSimClient extends AgreementFernClient {
  /** Use logger for logging events on this class. */
  private static final Logger logger = Logger.getLogger(GitSimClient.class.getName());

  /**
   * Make a new GitSimClient for a specific GitSim Fern server.
   * This will attempt to open a channel of communication.
   * @param localService a CharlotteNodeService which can be used to receive blocks
   * @param contact the Contact representing the server.
   */
  public GitSimClient(final CharlotteNodeService localService, final Contact contact) {
    super(localService, contact);
  }


  /**
   * Create a new Commit (not yet attached to a branch).
   * This will be signed with the localService's key.
   * @param comment a comment by the committer
   * @param repositoryContents the binary contents of the git Repository at this commit.
   * @param parentRefs references to all parent commits
   * @param parentDiffs diff operations (as binary) from each of the parent commits (in order). parentDiffs must be at least as long as parentRefs
   * @return a reference to the commit object, or null if something went wrong.
   */
  public Reference commit(final String comment, 
                          final ByteString repositoryContents,
                          final Iterable<Reference> parentRefs,
                          final Iterable<ByteString> parentDiffs) {
    final GitSimParents.Builder parentsBuilder = GitSimParents.newBuilder();
    final Iterator<ByteString> parentDiffIterator = parentDiffs.iterator();
    boolean hasParents = false;
    for (Reference parentRef : parentRefs) {
      hasParents = true;
      parentsBuilder.addParent(GitSimParent.newBuilder().setParentCommit(parentRef).setDiff(parentDiffIterator.next()));
    }
    final GitSimCommit.Builder commitBuilder = GitSimCommit.newBuilder().setComment(comment).setHash(sha3Hash(repositoryContents));
    if (hasParents) {
      commitBuilder.setParents(parentsBuilder);
    } else {
      commitBuilder.setInitialCommit(repositoryContents);
    }
    final GitSimCommit commit = commitBuilder.build();
    final Block block = Block.newBuilder().setSignedGitSimCommit(
            SignedGitSimCommit.newBuilder().
              setCommit(commit).
              setSignature(signBytes(getLocalService().getConfig().getKeyPair(), commit))
          ).build();

    // send out the block, and log any errors sent back
    boolean errorsSendingBlock = false;
    for (SendBlocksResponse response : getLocalService().onSendBlocksInput(block)) {
      errorsSendingBlock = true;
      logger.log(Level.SEVERE, "Unable to git commit. ERROR: " + response.getErrorMessage() + "\nBLOCK:\n" + block);
    }
    if (errorsSendingBlock) {
      return null;
    }
    return Reference.newBuilder().setHash(sha3Hash(block)).build();
  }

  /**
   * Generate an input for a request to a GitSim Fern server, given a commit reference and branch name.
   * This just formats the input object with those things.
   * @param commit a reference to the commit block we're trying to put on this Fern server's branch.
   * @param branch the branch on the fern server
   * @return the input object to be sent to the Fern server.
   */
  public RequestIntegrityAttestationInput generateCommitToBranchRequest(final Reference commit,
                                                                        final String branch) {
    return (
      RequestIntegrityAttestationInput.newBuilder().setPolicy(
        IntegrityPolicy.newBuilder().setFillInTheBlank(
          IntegrityAttestation.newBuilder().setSignedGitSimBranch(
            SignedGitSimBranch.newBuilder().
              setGitSimBranch(
                GitSimBranch.newBuilder().
                  setBranchName(branch).
                  setCommit(commit)
              ).
              setSignature(
                Signature.newBuilder().setCryptoId(getContact().getCryptoId())
              )
          )
        )
      ).build()
    );
  }

  /**
   * Gets the integrity attestation (server put the commit on a branch) for a given commit on a given branch.
   * @param commit a reference to the commit block
   * @param branch the branch onto which we're committing
   * @return the integrity attestaion block if successfull (also it logs INFO), null otherwise.
   */
  public Block commitToBranch(final Reference commit, final String branch) {
    final Block block = getIntegrityAttestation(generateCommitToBranchRequest(commit, branch));
    if (block != null) {
      // We're just going to Info log the fact that we successfully committed.
      logger.info("Successfully committed to branch: " +
        block.getIntegrityAttestation().getSignedGitSimBranch().getGitSimBranch().getBranchName() +
        "\nCOMMIT comment: " +
        // get the commit block that was referenced
        getLocalService().getBlock(block.getIntegrityAttestation().getSignedGitSimBranch().getGitSimBranch().getCommit()).
        // then get the comment from it
          getSignedGitSimCommit().getCommit().getComment()
      );
    }
    return block;
  }

  /**
   * Create a new Commit, and attach it to a branch on the server.
   * This will be signed with the localService's key.
   * @param branch the name of the desired branch
   * @param comment a comment by the committer
   * @param repositoryContents the binary contents of the git Repository at this commit.
   * @param parentRefs references to all parent commits
   * @param parentDiffs diff operations (as binary) from each of the parent commits (in order). parentDiffs must be at least as long as parentRefs
   * @return a reference to the commit object, featuring an Integrity Attestation that puts it on the branch (if available), or null, if something went wrong.
   */
  public Reference commit(final String branch,
                          final String comment, 
                          final ByteString repositoryContents,
                          final Iterable<Reference> parentRefs,
                          final Iterable<ByteString> parentDiffs) {
    final Reference commitReference = commit(comment, repositoryContents, parentRefs, parentDiffs);
    if (commitReference == null) {
      return null;
    }
    final Block attestation = commitToBranch(commitReference, branch);
    if (attestation == null) {
      return null; // or should we return commitReference here?
    }
    return Reference.newBuilder(commitReference).addIntegrityAttestations(
             Reference.newBuilder().setHash(sha3Hash(attestation))).build();
  }

  /**
   * Fill in all known attestations for a reference.
   * Danger: this is SLOW!
   * It is going to search through all known blocks, possibly multiple times (as it fills out references to integrity attestations).
   * @param reference the reference for which we're seeking attestations.
   * @return the same reference, but with all known integrity and availability attestations added.
   */
  public Reference collectAttestations(final Reference reference) {
    final Reference.Builder builder = Reference.newBuilder();
    // to avoid duplicating existing attestations, I'll keep track of them in this set.
    final Set<Hash> knownAttestations = newKeySet();
    for (Hash attestation : reference.getAvailabilityAttestationsList()) {
      knownAttestations.add(attestation);
      builder.addAvailabilityAttestations(attestation);
    }
    for (Reference attestation : reference.getIntegrityAttestationsList()) {
      if (attestation.hasHash()) {
        knownAttestations.add(attestation.getHash());
        builder.addIntegrityAttestations(collectAttestations(attestation));
      }
    }
    for (Block block : getLocalService().getBlockMap().values()) {
      final Hash blockHash = sha3Hash(block);
      if (!knownAttestations.contains(blockHash)) { // if this attestation isn't already listed...
        if (block.hasAvailabilityAttestation()) {
          if (block.getAvailabilityAttestation().hasSignedStoreForever()
             && block.getAvailabilityAttestation().getSignedStoreForever().hasStoreForever()) {
            for (Reference r : block.getAvailabilityAttestation().getSignedStoreForever().getStoreForever().getBlockList()) {
              if (r.hasHash()
                 && r.getHash().equals(reference.getHash())) {
                builder.addAvailabilityAttestations(blockHash);
              }
            }
          }
        } else if (block.hasIntegrityAttestation()) {
          if(block.getIntegrityAttestation().hasSignedChainSlot()) {
            if (block.getIntegrityAttestation().getSignedChainSlot().hasChainSlot()
               && block.getIntegrityAttestation().getSignedChainSlot().getChainSlot().hasBlock()
               && block.getIntegrityAttestation().getSignedChainSlot().getChainSlot().getBlock().hasHash()
               && block.getIntegrityAttestation().getSignedChainSlot().getChainSlot().getBlock().getHash().equals(reference.getHash())) {
              builder.addIntegrityAttestations(collectAttestations(Reference.newBuilder().setHash(blockHash).build()));
            }
          } else if (block.getIntegrityAttestation().hasSignedGitSimBranch()) {
            if (block.getIntegrityAttestation().getSignedGitSimBranch().hasGitSimBranch()
               && block.getIntegrityAttestation().getSignedGitSimBranch().getGitSimBranch().hasCommit()
               && block.getIntegrityAttestation().getSignedGitSimBranch().getGitSimBranch().getCommit().hasHash()
               && block.getIntegrityAttestation().getSignedGitSimBranch().getGitSimBranch().getCommit().getHash().
                  equals(reference.getHash())){
              builder.addIntegrityAttestations(collectAttestations(Reference.newBuilder().setHash(blockHash).build()));
            }
          } else if (block.getIntegrityAttestation().hasSignedTimestampedReferences()) {
            if (block.getIntegrityAttestation().getSignedTimestampedReferences().hasTimestampedReferences()) {
              for (Reference r:block.getIntegrityAttestation().getSignedTimestampedReferences().getTimestampedReferences().getBlockList()) {
                if (r.hasHash()
                   && r.getHash().equals(reference.getHash())) {
                  builder.addIntegrityAttestations(collectAttestations(Reference.newBuilder().setHash(blockHash).build()));
                }
              }
            }
          }
        }
      }
    }
    return builder.build();
  }

  /**
   * Check whether this Block contains a valid IntegrityAttestation.
   * Checks for a valid signature in a properly formatted SignedGitSimBranch.
   * @param attestation the block we're hoping contains the IntegrityAttestation
   * @return the Block input if it's valid, null otherwise.
   */
  @Override
  public Block checkIntegrityAttestation(final Block attestation) {
    if (!attestation.hasIntegrityAttestation()) {
      logger.log(Level.WARNING, "Response from Fern Server referenced block which is not an Integrity Attestation:\n" +
                                attestation);
      return null;
    }
    if (!attestation.getIntegrityAttestation().hasSignedGitSimBranch()) {
      logger.log(Level.WARNING, "Response from Fern Server referenced attestation which is not a SignedTimestampedReferences:\n" +
                                attestation);
      return null;
    }
    if (!attestation.getIntegrityAttestation().getSignedGitSimBranch().hasGitSimBranch()) {
      logger.log(Level.WARNING, "Response from Fern Server referenced attestation which has no GitSimBranch:\n" +
                                attestation);
      return null;
    }
    if (!attestation.getIntegrityAttestation().getSignedGitSimBranch().getGitSimBranch().hasTimestamp()) {
      logger.log(Level.WARNING, "Response from Fern Server referenced attestation which has no Timestamp:\n" +
                                attestation);
      return null;
    }
    if (!attestation.getIntegrityAttestation().getSignedGitSimBranch().hasSignature()) {
      logger.log(Level.WARNING, "Response from Fern Server referenced attestation which has no Signature:\n" +
                                attestation);
      return null;
    }
    if (!checkSignature(attestation.getIntegrityAttestation().getSignedGitSimBranch().getGitSimBranch(),
                        attestation.getIntegrityAttestation().getSignedGitSimBranch().getSignature())) {
      logger.log(Level.WARNING, "Response from Fern Server referenced attestation with an incorrect signature:\n" +
                                attestation);
      return null;
    }
    return attestation;
  }



  /**
   * Check whether this Response references a valid IntegrityAttestation matching the request.
   * Fetches the block from our local node.
   * This may wait until the commit block referenced is received.
   * @param request the request we sent to the Fern server
   * @param response references the block we're hoping contains the IntegrityAttestation
   * @return the Integrity Attestation Block input if it's valid, null otherwise.
   */
  public Block checkIntegrityAttestation(final RequestIntegrityAttestationInput request,
                                         final RequestIntegrityAttestationResponse response) {
     final Block attestation = checkIntegrityAttestation(response);
     // by this point, the attestation itself is verified, so we know it has a chain slot and a valid signature and such.
     if (attestation == null) {
       return null;
     }
     // check if the two GitSimBranch s are equal, EXCEPT for timestamps: those can be different.
     if (!attestation.getIntegrityAttestation().getSignedGitSimBranch().getGitSimBranch().equals(
        GitSimBranch.newBuilder(request.getPolicy().getFillInTheBlank().getSignedGitSimBranch().getGitSimBranch()).
          setTimestamp(attestation.getIntegrityAttestation().getSignedGitSimBranch().getGitSimBranch().getTimestamp()).
          build())) {
       logger.log(Level.WARNING, "Response from Fern Server referenced Attestation with different GitSimBranch."+
                                 "\nATTESTATION:\n"+attestation+
                                 "\nREQUEST:\n"+request);
       return null;
     }
     if (!attestation.getIntegrityAttestation().getSignedGitSimBranch().getSignature().getCryptoId().equals(
        request.getPolicy().getFillInTheBlank().getSignedGitSimBranch().getSignature().getCryptoId())) {
       logger.log(Level.WARNING, "Response from Fern Server referenced Attestation different CryptoId than requested:" +
                                 "\nATTESTATION:\n"+attestation+
                                 "\nREQUEST:\n"+request);
       return null;
     }
     return attestation;
  }
}
