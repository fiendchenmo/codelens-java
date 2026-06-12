package com.codelens.common.profile;

/**
 * 架构约束。
 */
public class Constraint {
    private String type;        // LAYER_VIOLATION / MISSING_PRACTICE / ANTI_PATTERN
    private String description; // "Controller不应直接调用Repository"
    private String severity;    // ERROR / WARN / INFO
    private String rationale;   // "项目采用标准分层架构"

    public Constraint() {}

    public Constraint(String type, String description, String severity, String rationale) {
        this.type = type;
        this.description = description;
        this.severity = severity;
        this.rationale = rationale;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
}
