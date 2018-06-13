package co.nyzo.verifier;

import java.nio.ByteBuffer;
import java.util.*;

public class NewVerifierVoteManager {

    // This class manages votes for the next verifier to let into the verification cycle. Each existing verifier has a
    // vote for who should be allowed in next, and the winning verifier is the one who should be admitted. This is a
    // vote that can (and should) be recast many times, as many new verifiers are let into the mesh.

    // As much as possible, this class follows the pattern of BlockVoteManager.

    // The local vote is redundant, but it is a simple and efficient way to store the local vote for responding to
    // node-join messages.
    private static final byte[] localVote = new byte[FieldByteSize.identifier];
    private static final Map<ByteBuffer, ByteBuffer> voteMap = new HashMap<>();

    public static synchronized void registerVote(byte[] votingIdentifier, byte[] newVerifierIdentifier,
                                                 boolean isLocalVote) {

        // Register the vote. The map ensures that each identifier only gets one vote. Some of the votes may not count.
        // Votes are only counted for verifiers in the previous cycle.
        voteMap.put(ByteBuffer.wrap(votingIdentifier), ByteBuffer.wrap(newVerifierIdentifier));

        if (isLocalVote) {
            System.arraycopy(localVote, 0, newVerifierIdentifier, 0, FieldByteSize.identifier);
        }
    }

    public static synchronized void removeOldVotes() {

        // For simplicity, we remove all votes that are not in the current verifier cycle to conserve memory. This will
        // potentially remove verifiers that would be in the verification cycle when they are needed, but that's not
        // a huge concern, as this process does not require absolute or precise vote counts to work properly. In
        // reality, we are highly unlikely to ever lose more than one vote at a time due to this simplification.

        Set<ByteBuffer> verifiers = new HashSet<>(voteMap.keySet());
        Set<ByteBuffer> currentCycle = BlockManager.verifiersInPreviousCycle();
        for (ByteBuffer verifier : verifiers) {
            if (!currentCycle.contains(verifier)) {
                voteMap.remove(verifier);
            }
        }
    }

    public static synchronized List<ByteBuffer> topVerifiers() {

        List<ByteBuffer> topVerifiers = new ArrayList<>();

        Map<ByteBuffer, Integer> votesPerVerifier = new HashMap<>();
        Set<ByteBuffer> votingVerifiers = BlockManager.verifiersInPreviousCycle();

        // If the voting verifiers list is empty, accept votes from all verifiers. This will happen only rarely, if
        // ever, but this condition is helpful for testing.
        boolean acceptAllVotes = votingVerifiers.isEmpty();

        // Build the vote map.
        for (ByteBuffer votingVerifier : voteMap.keySet()) {
            if (votingVerifiers.contains(votingVerifier) || acceptAllVotes) {
                ByteBuffer newVerifierIdentifier = voteMap.get(votingVerifier);
                Integer votesForVerifier = votesPerVerifier.get(newVerifierIdentifier);
                if (votesForVerifier == null) {
                    votesPerVerifier.put(newVerifierIdentifier, 1);
                } else {
                    votesPerVerifier.put(newVerifierIdentifier, votesForVerifier + 1);
                }
            }
        }

        // Make and sort the list descending on votes.
        topVerifiers.addAll(votesPerVerifier.keySet());
        Collections.sort(topVerifiers, new Comparator<ByteBuffer>() {
            @Override
            public int compare(ByteBuffer verifierIdentifier1, ByteBuffer verifierIdentifier2) {
                Integer voteCount1 = votesPerVerifier.get(verifierIdentifier1);
                Integer voteCount2 = votesPerVerifier.get(verifierIdentifier2);
                return voteCount2.compareTo(voteCount1);
            }
        });


        return topVerifiers;
    }

    public static synchronized byte[] getLocalVote() {

        return Arrays.copyOf(localVote, FieldByteSize.identifier);
    }


}