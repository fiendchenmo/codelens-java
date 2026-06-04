package com.codelens.common.agent;

/**
 * LLM 输出 methods[].risks[] 中的单条风险项。
 * 保留原始行号，避免从 riskIndicators 纯字符串重建时丢失行号信息。
 */
public class RiskItem {

    private String type;
    private String description;
    private int line;
    private String severity;
    private String impact;
    private String suggestion;
    private double confidence;

    public RiskItem() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getImpact() { return impact; }
    public void setImpact(String impact) { this.impact = impact; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
}
