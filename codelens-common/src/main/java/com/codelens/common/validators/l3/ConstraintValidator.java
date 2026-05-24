package com.codelens.common.validators.l3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConstraintValidator implements L3Verifier {
    private final L3Config config;
    private final List<KnownConstraint> constraints;

    public ConstraintValidator(L3Config config, List<KnownConstraint> constraints) {
        this.config = config;
        this.constraints = constraints;
    }

    @Override
    public VerificationResult verify(VerificationRequest request) {
        if (!config.isEnabled()) {
            return new VerificationResult(request.getClaim(), VerificationStatus.SKIPPED, "L3 verification disabled", ConfidenceLevel.HIGH);
        }
        if (constraints.isEmpty()) {
            return new VerificationResult(request.getClaim(), VerificationStatus.PENDING, "No known constraints available", ConfidenceLevel.MEDIUM);
        }

        List<String> mentionedMethods = extractMethodSignatures(request.getClaim());
        if (mentionedMethods.isEmpty()) {
            return new VerificationResult(request.getClaim(), VerificationStatus.PENDING,
                "No method signatures found in claim", ConfidenceLevel.MEDIUM);
        }

        for (String method : mentionedMethods) {
            boolean found = false;
            for (KnownConstraint constraint : constraints) {
                if (constraint.getValue().contains(method)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return new VerificationResult(request.getClaim(), VerificationStatus.REJECTED,
                    "Method " + method + " not found in known constraints", ConfidenceLevel.HIGH);
            }
        }

        return new VerificationResult(request.getClaim(), VerificationStatus.PASSED,
            "All mentioned methods match known constraints", ConfidenceLevel.HIGH);
    }

    static List<String> extractMethodSignatures(String text) {
        List<String> methods = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\b([A-Z]\\w*)\\.([a-z]\\w*)\\(\\)");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            methods.add(matcher.group(1) + "." + matcher.group(2) + "()");
        }
        return methods;
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
