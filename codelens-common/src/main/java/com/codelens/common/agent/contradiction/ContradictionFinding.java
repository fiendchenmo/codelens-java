package com.codelens.common.agent.contradiction;

/**
 * 单条矛盾发现记录。
 * <p>
 * 由 {@link ContradictionDetector} 在执行跨 Agent 交叉验证时生成，
 * 描述两个或多个 Agent 输出之间的矛盾点。
 * </p>
 *
 * <p>矛盾类型：</p>
 * <ul>
 *   <li>{@link ContradictionType#CALL_GRAPH_MISMATCH C1} — 调用图互斥</li>
 *   <li>{@link ContradictionType#SUMMARY_DETAIL_CONFLICT C2} — 摘要-细节冲突</li>
 *   <li>{@link ContradictionType#RISK_EVIDENCE_CONTRADICTION C3} — 风险-证据矛盾</li>
 *   <li>{@link ContradictionType#FIELD_SELF_CONTRADICTION C4} — 字段自相矛盾</li>
 *   <li>{@link ContradictionType#DB_COUPLING C5} — 数据层跨模块耦合</li>
 * </ul>
 */
public class ContradictionFinding {

    /** 矛盾类型 */
    private ContradictionType type;

    /** 严重度 */
    private Severity severity;

    /** 涉及的源方法名（简单名，如 "process"） */
    private String sourceMethod;

    /** 涉及的目标方法名（C1 场景） */
    private String targetMethod;

    /** 置信度惩罚值（负数，如 -0.2），INCOMPLETE 状态时为 0 */
    private double confidencePenalty;

    /** 人类可读的矛盾描述 */
    private String description;

    /** 矛盾证据（源码片段 / Agent 输出片段） */
    private String evidence;

    /** 矛盾状态：CONTRADICTORY（确认矛盾）或 INCOMPLETE（数据不完整） */
    private Status status = Status.CONTRADICTORY;

    public ContradictionFinding() {}

    public ContradictionFinding(ContradictionType type, Severity severity,
                                String sourceMethod, String targetMethod,
                                double confidencePenalty, String description,
                                String evidence, Status status) {
        this.type = type;
        this.severity = severity;
        this.sourceMethod = sourceMethod;
        this.targetMethod = targetMethod;
        this.confidencePenalty = confidencePenalty;
        this.description = description;
        this.evidence = evidence;
        this.status = status;
    }

    // ─── 枚举 ────────────────────────────────────────

    public enum ContradictionType {
        /** C1: A.calls 包含 B，但 B.calledBy 不含 A */
        CALL_GRAPH_MISMATCH,
        /** C2: SUMMARY.complexity=LOW 但多数方法 complexity=HIGH */
        SUMMARY_DETAIL_CONFLICT,
        /** C3: risk 行号指向注释/空行，或 L1 校验 FAILED */
        RISK_EVIDENCE_CONTRADICTION,
        /** C4: complexity 与 complexityValue 不一致 */
        FIELD_SELF_CONTRADICTION,
        /** C5: 同一张表被 ≥3 个不同包的 Mapper 操作，存在隐式耦合风险 */
        DB_COUPLING
    }

    public enum Severity { HIGH, MEDIUM, LOW }

    /**
     * 矛盾状态。
     * <ul>
     *   <li>{@link #CONTRADICTORY} — 确认矛盾，应用置信度惩罚</li>
     *   <li>{@link #INCOMPLETE} — 数据不完整，不降置信度（避免误报）</li>
     * </ul>
     */
    public enum Status { CONTRADICTORY, INCOMPLETE }

    // ─── getters / setters ──────────────────────────

    public ContradictionType getType() { return type; }
    public void setType(ContradictionType type) { this.type = type; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public String getSourceMethod() { return sourceMethod; }
    public void setSourceMethod(String sourceMethod) { this.sourceMethod = sourceMethod; }

    public String getTargetMethod() { return targetMethod; }
    public void setTargetMethod(String targetMethod) { this.targetMethod = targetMethod; }

    public double getConfidencePenalty() { return confidencePenalty; }
    public void setConfidencePenalty(double confidencePenalty) { this.confidencePenalty = confidencePenalty; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
