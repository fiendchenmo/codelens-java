package com.codelens.common.validators.l3;

public class VerificationResult {
    private final String originalClaim;
    private final VerificationStatus status;
    private final String evidence;
    private final ConfidenceLevel confidence;

    public VerificationResult(String originalClaim, VerificationStatus status, String evidence, ConfidenceLevel confidence) {
        this.originalClaim = originalClaim;
        this.status = status;
        this.evidence = evidence;
        this.confidence = confidence;
    }

    public String getOriginalClaim() { return originalClaim; }
    public VerificationStatus getStatus() { return status; }
    public String getEvidence() { return evidence; }
    public ConfidenceLevel getConfidence() { return confidence; }

    public boolean isPassed() { return status == VerificationStatus.PASSED; }
    public boolean isRejected() { return status == VerificationStatus.REJECTED; }
    public boolean isPending() { return status == VerificationStatus.PENDING; }
}
