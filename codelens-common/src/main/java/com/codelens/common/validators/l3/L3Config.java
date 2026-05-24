package com.codelens.common.validators.l3;

import java.util.Map;

public class L3Config {
    private boolean enabled = false;
    private ConfidenceLevel confidenceThreshold = ConfidenceLevel.MEDIUM;
    private boolean crossValidationEnabled = true;
    private boolean votingEnabled = false;
    private int maxRetries = 1;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public ConfidenceLevel getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(ConfidenceLevel confidenceThreshold) { this.confidenceThreshold = confidenceThreshold; }
    public boolean isCrossValidationEnabled() { return crossValidationEnabled; }
    public void setCrossValidationEnabled(boolean crossValidationEnabled) { this.crossValidationEnabled = crossValidationEnabled; }
    public boolean isVotingEnabled() { return votingEnabled; }
    public void setVotingEnabled(boolean votingEnabled) { this.votingEnabled = votingEnabled; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public static L3Config fromMap(Map<String, String> overrides) {
        L3Config config = new L3Config();
        if (overrides.containsKey("l3.enabled")) {
            config.setEnabled(Boolean.parseBoolean(overrides.get("l3.enabled")));
        }
        if (overrides.containsKey("l3.confidence.threshold")) {
            config.setConfidenceThreshold(ConfidenceLevel.valueOf(overrides.get("l3.confidence.threshold")));
        }
        if (overrides.containsKey("l3.max-retries")) {
            config.setMaxRetries(Integer.parseInt(overrides.get("l3.max-retries")));
        }
        return config;
    }
}
