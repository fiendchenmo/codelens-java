package com.codelens.common.validators.l3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class CrossValidator implements L3Verifier {
    private final L3Config config;
    private final BiFunction<String, String, VerificationVerdict> reVerifier;

    public CrossValidator(L3Config config) {
        this(config, (claim, context) -> VerificationVerdict.CONFIRMED);
    }

    public CrossValidator(L3Config config, BiFunction<String, String, VerificationVerdict> reVerifier) {
        this.config = config;
        this.reVerifier = reVerifier;
    }

    @Override
    public VerificationResult verify(VerificationRequest request) {
        if (!config.isEnabled()) {
            return new VerificationResult(request.getClaim(), VerificationStatus.SKIPPED, "L3 verification disabled", ConfidenceLevel.HIGH);
        }
        if (!config.isCrossValidationEnabled()) {
            return new VerificationResult(request.getClaim(), VerificationStatus.PENDING, "Cross-validation disabled", ConfidenceLevel.MEDIUM);
        }

        VerificationVerdict verdict = reVerifier.apply(request.getClaim(), request.getContext());

        switch (verdict) {
            case CONFIRMED:
                return new VerificationResult(request.getClaim(), VerificationStatus.PASSED, "Cross-validation confirmed", ConfidenceLevel.HIGH);
            case REJECTED:
                return new VerificationResult(request.getClaim(), VerificationStatus.REJECTED, "Cross-validation rejected", ConfidenceLevel.HIGH);
            default:
                return new VerificationResult(request.getClaim(), VerificationStatus.PENDING, "Cross-validation uncertain", ConfidenceLevel.MEDIUM);
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
