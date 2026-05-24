// SYNC_VERSION: 2026-05-24-v1
// IMPACT: LOGIC_CHANGE
// 维护方：喵呜（CLI端）
// 同步说明：零 IntelliJ SDK 依赖，纯文本处理，CLI 单测可覆盖

package com.codelens.common.validators;

/**
 * 方法范围数据类 — 表示源码中一个方法的起始和结束行号。
 *
 * 用于 EvidenceValidator 方法级行号校验：
 * 当提供 methodRanges 时，校验 risks.line 是否在对应方法范围内。
 */
public class MethodRange {

    private final String methodName;
    private final int startLine;
    private final int endLine;

    public MethodRange(String methodName, int startLine, int endLine) {
        this.methodName = methodName;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public String getMethodName() {
        return methodName;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    /**
     * 判断指定行号是否在此方法范围内（含边界）。
     */
    public boolean contains(int line) {
        return line >= startLine && line <= endLine;
    }
}
