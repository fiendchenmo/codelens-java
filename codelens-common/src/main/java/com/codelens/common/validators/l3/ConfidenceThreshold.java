package com.codelens.common.validators.l3;

public class ConfidenceThreshold {
    private final ConfidenceLevel threshold;

    public ConfidenceThreshold(ConfidenceLevel threshold) {
        this.threshold = threshold;
    }

    public ConfidenceLevel getThreshold() { return threshold; }

    public boolean shouldVerify(ConfidenceLevel level) {
        return level.ordinal() < threshold.ordinal();
    }
}
