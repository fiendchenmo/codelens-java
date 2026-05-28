package com.codelens.common.benchmark;

/**
 * 基准测试结果数据类。
 */
public class BenchmarkResult {

    private final String sampleName;
    private final double l1Coverage;
    private final double l2Accuracy;
    private final int tokenCount;
    private final long latencyMs;
    private final boolean passed;

    public BenchmarkResult(String sampleName, double l1Coverage, double l2Accuracy,
                           int tokenCount, long latencyMs, boolean passed) {
        this.sampleName = sampleName;
        this.l1Coverage = l1Coverage;
        this.l2Accuracy = l2Accuracy;
        this.tokenCount = tokenCount;
        this.latencyMs = latencyMs;
        this.passed = passed;
    }

    public String getSampleName() {
        return sampleName;
    }

    public double getL1Coverage() {
        return l1Coverage;
    }

    public double getL2Accuracy() {
        return l2Accuracy;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public boolean isPassed() {
        return passed;
    }
}
