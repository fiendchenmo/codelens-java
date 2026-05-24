package com.codelens.common.validators.l3;

public class VerificationRequest {
    private final String claim;
    private final ConfidenceLevel confidence;
    private final String claimType;
    private final String context;

    public VerificationRequest(String claim, ConfidenceLevel confidence, String claimType, String context) {
        this.claim = claim;
        this.confidence = confidence;
        this.claimType = claimType;
        this.context = context;
    }

    public String getClaim() { return claim; }
    public ConfidenceLevel getConfidence() { return confidence; }
    public String getClaimType() { return claimType; }
    public String getContext() { return context; }
}
