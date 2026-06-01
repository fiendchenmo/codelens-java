package com.codelens.common.diff;

/**
 * 变更方法信息
 */
public class ChangedMethod {
    public String className;
    public String methodName;
    public String signature;       // 完整签名，用于 CallIndex 匹配
    public ChangeType changeType;
    public int oldStartLine;       // 旧版本行号（MODIFIED/DELETED）
    public int newStartLine;       // 新版本行号（ADDED/MODIFIED）

    public ChangedMethod() {
    }

    public ChangedMethod(String className, String methodName, String signature,
                         ChangeType changeType, int oldStartLine, int newStartLine) {
        this.className = className;
        this.methodName = methodName;
        this.signature = signature;
        this.changeType = changeType;
        this.oldStartLine = oldStartLine;
        this.newStartLine = newStartLine;
    }
}
