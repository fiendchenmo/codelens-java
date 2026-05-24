package com.codelens.common.validators.l3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class CrossValidator implements L3Verifier {
    private final L3Config config;
    private final BiFunction<String, String, String> reVerifier;

    public CrossValidator(L3Config config) {
        this(config, (claim, context) -> "确认");
    }

    public CrossValidator(L3Config config, BiFunction<String, String, String> reVerifier) {
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

        String result = reVerifier.apply(request.getClaim(), request.getContext());

        if (result.contains("未调用") || result.contains("并未") || result.contains("否认")) {
            return new VerificationResult(request.getClaim(), VerificationStatus.REJECTED, result, ConfidenceLevel.HIGH);
        }

        if (result.contains("不确定") || result.contains("无法验证")) {
            return new VerificationResult(request.getClaim(), VerificationStatus.PENDING, result, ConfidenceLevel.MEDIUM);
        }

        if (result.contains("确认") || result.contains("确实") || result.contains("调用了")) {
            return new VerificationResult(request.getClaim(), VerificationStatus.PASSED, result, ConfidenceLevel.HIGH);
        }

        return new VerificationResult(request.getClaim(), VerificationStatus.PENDING, result, ConfidenceLevel.MEDIUM);
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
