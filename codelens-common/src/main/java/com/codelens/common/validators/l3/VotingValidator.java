package com.codelens.common.validators.l3;

import java.util.ArrayList;
import java.util.List;

public class VotingValidator implements L3Verifier {

    @FunctionalInterface
    public interface VoteFunction {
        String vote(String claim);
    }

    private final L3Config config;
    private final List<VoteFunction> voters;

    public VotingValidator(L3Config config, List<VoteFunction> voters) {
        this.config = config;
        this.voters = voters;
    }

    @Override
    public VerificationResult verify(VerificationRequest request) {
        if (!config.isEnabled()) {
            return new VerificationResult(request.getClaim(), VerificationStatus.SKIPPED, "L3 verification disabled", ConfidenceLevel.HIGH);
        }
        if (voters.isEmpty()) {
            return new VerificationResult(request.getClaim(), VerificationStatus.PENDING, "No voters available", ConfidenceLevel.MEDIUM);
        }

        int affirmative = 0;
        int negative = 0;

        for (VoteFunction voter : voters) {
            String vote = voter.vote(request.getClaim());
            if (vote.contains("确认")) {
                affirmative++;
            } else if (vote.contains("否认")) {
                negative++;
            }
        }

        String evidence = "Votes: " + affirmative + "/" + negative + "/" + voters.size();
        if (affirmative > negative) {
            return new VerificationResult(request.getClaim(), VerificationStatus.PASSED,
                evidence, ConfidenceLevel.HIGH);
        } else if (negative > affirmative) {
            return new VerificationResult(request.getClaim(), VerificationStatus.REJECTED,
                evidence, ConfidenceLevel.HIGH);
        } else {
            return new VerificationResult(request.getClaim(), VerificationStatus.PENDING,
                "Tie: " + affirmative + "/" + negative, ConfidenceLevel.MEDIUM);
        }
    }

    @Override
    public List<VerificationResult> verifyAll(List<VerificationRequest> requests) {
        List<VerificationResult> results = new ArrayList<>();
        for (VerificationRequest req : requests) {
            results.add(verify(req));
        }
        return results;
    }
}
