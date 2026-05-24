package com.codelens.common.detectors.implicit;

public enum DetectionDimension {
    SPRING_INJECTION(ConfidenceLevel.HIGH),
    INTERFACE_IMPL(ConfidenceLevel.HIGH),
    EVENT_LISTENER(ConfidenceLevel.HIGH),
    CONDITIONAL_BEAN(ConfidenceLevel.MEDIUM),
    REFLECTION_CALL(ConfidenceLevel.MEDIUM),
    CONFIG_REFERENCE(ConfidenceLevel.LOW);

    private final ConfidenceLevel defaultConfidence;

    DetectionDimension(ConfidenceLevel defaultConfidence) {
        this.defaultConfidence = defaultConfidence;
    }

    public ConfidenceLevel getDefaultConfidence() {
        return defaultConfidence;
    }
}
