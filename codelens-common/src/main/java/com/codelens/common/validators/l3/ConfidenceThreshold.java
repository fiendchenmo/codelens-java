package com.codelens.common.validators.l3;

public class ConfidenceThreshold {
    private final ConfidenceLevel threshold;

    public ConfidenceThreshold(ConfidenceLevel threshold) {
        this.threshold = threshold;
    }

    public ConfidenceLevel getThreshold() { return threshold; }

    public boolean shouldVerify(ConfidenceLevel level) {
        if (level == ConfidenceLevel.HIGH) return false;
        if (threshold == ConfidenceLevel.HIGH) return level == ConfidenceLevel.LOW;
        return level == ConfidenceLevel.LOW || level == ConfidenceLevel.MEDIUM;
    }
}
