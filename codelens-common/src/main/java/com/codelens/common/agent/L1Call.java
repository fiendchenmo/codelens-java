package com.codelens.common.agent;

/**
 * L1 证据中的单条调用记录。
 * <p>
 * LLM 可输出两种格式：
 * <ul>
 *   <li>对象格式：{@code {"target": "methodName", "line": 42, "sourceLine": 40, "status": 1}}</li>
 *   <li>字符串格式（降级）：{@code "methodName"} → target=字符串，其余字段=0</li>
 * </ul>
 * </p>
 */
public class L1Call {

    private String target;
    private int line;
    private int sourceLine;
    private int status;

    public L1Call() {}

    public L1Call(String target, int line, int sourceLine, int status) {
        this.target = target;
        this.line = line;
        this.sourceLine = sourceLine;
        this.status = status;
    }

    /** 从纯字符串创建降级 L1Call（line/sourceLine/status 均为 0）。 */
    public L1Call(String target) {
        this(target, 0, 0, 0);
    }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }
    public int getSourceLine() { return sourceLine; }
    public void setSourceLine(int sourceLine) { this.sourceLine = sourceLine; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
}
