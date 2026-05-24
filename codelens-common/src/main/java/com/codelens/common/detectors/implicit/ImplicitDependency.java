package com.codelens.common.detectors.implicit;

import java.util.Map;

public class ImplicitDependency {
    private final String target;
    private final String resolvedTarget;
    private final DetectionDimension type;
    private final ConfidenceLevel confidence;
    private final String resolution;
    private final Map<String, String> evidence;

    public ImplicitDependency(String target, DetectionDimension type, ConfidenceLevel confidence, String resolution) {
        this(target, null, type, confidence, resolution, null);
    }

    public ImplicitDependency(String target, String resolvedTarget, DetectionDimension type, ConfidenceLevel confidence, String resolution, Map<String, String> evidence) {
        this.target = target;
        this.resolvedTarget = resolvedTarget;
        this.type = type;
        this.confidence = confidence;
        this.resolution = resolution;
        this.evidence = evidence;
    }

    public String getTarget() { return target; }
    public String getResolvedTarget() { return resolvedTarget; }
    public DetectionDimension getType() { return type; }
    public ConfidenceLevel getConfidence() { return confidence; }
    public String getResolution() { return resolution; }
    public Map<String, String> getEvidence() { return evidence; }
}
