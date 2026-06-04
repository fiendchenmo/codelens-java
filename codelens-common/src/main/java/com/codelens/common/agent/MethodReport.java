package com.codelens.common.agent;

import java.util.List;

/**
 * 方法报告数据类。
 */
public class MethodReport {

    private String methodName;
    private String signature;
    private int line;
    private L1Evidence l1Evidence;
    private L2Confidence l2Confidence;
    private List<RiskItem> risks;

    public MethodReport() {}

    public MethodReport(String methodName, String signature, int line,
                        L1Evidence l1Evidence, L2Confidence l2Confidence) {
        this.methodName = methodName;
        this.signature = signature;
        this.line = line;
        this.l1Evidence = l1Evidence;
        this.l2Confidence = l2Confidence;
    }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }
    public L1Evidence getL1Evidence() { return l1Evidence; }
    public void setL1Evidence(L1Evidence l1Evidence) { this.l1Evidence = l1Evidence; }
    public L2Confidence getL2Confidence() { return l2Confidence; }
    public void setL2Confidence(L2Confidence l2Confidence) { this.l2Confidence = l2Confidence; }
    public List<RiskItem> getRisks() { return risks; }
    public void setRisks(List<RiskItem> risks) { this.risks = risks; }
}
