package com.codelens.common.agent;

import java.util.List;

/**
 * L2 二级置信度数据类。
 */
public class L2Confidence {

    private double overallScore;
    private String reasoningBasis;
    private List<String> riskIndicators;
    /** 矛盾检测导致的额外置信度惩罚（负数，如 -0.2，P1 白盒矛盾检测） */
    private double contradictionPenalty;

    public L2Confidence() {}

    public L2Confidence(double overallScore, String reasoningBasis, List<String> riskIndicators) {
        this.overallScore = overallScore;
        this.reasoningBasis = reasoningBasis;
        this.riskIndicators = riskIndicators;
    }

    public double getOverallScore() { return overallScore; }
    public void setOverallScore(double overallScore) { this.overallScore = overallScore; }
    public String getReasoningBasis() { return reasoningBasis; }
    public void setReasoningBasis(String reasoningBasis) { this.reasoningBasis = reasoningBasis; }
    public List<String> getRiskIndicators() { return riskIndicators; }
    public void setRiskIndicators(List<String> riskIndicators) { this.riskIndicators = riskIndicators; }
    public double getContradictionPenalty() { return contradictionPenalty; }
    public void setContradictionPenalty(double contradictionPenalty) { this.contradictionPenalty = contradictionPenalty; }
}
