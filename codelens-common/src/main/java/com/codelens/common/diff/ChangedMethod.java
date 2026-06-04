package com.codelens.common.diff;

/**
 * 变更方法信息
 */
public class ChangedMethod {
    public String className;
    public String methodName;
    public String signature;       // 完整签名，用于 CallIndex 匹配
    public ChangeType changeType;
    public String impactScope;     // 影响范围描述，如 "Calls影响: 2个调用变更"
    public int oldStartLine;       // 旧版本行号（MODIFIED/DELETED）
    public int newStartLine;       // 新版本行号（ADDED/MODIFIED）

    public ChangedMethod() {
    }

    public ChangedMethod(String className, String methodName, String signature,
                         ChangeType changeType, int oldStartLine, int newStartLine) {
        this(className, methodName, signature, changeType, oldStartLine, newStartLine, null);
    }

    public ChangedMethod(String className, String methodName, String signature,
                         ChangeType changeType, int oldStartLine, int newStartLine,
                         String impactScope) {
        this.className = className;
        this.methodName = methodName;
        this.signature = signature;
        this.changeType = changeType;
        this.oldStartLine = oldStartLine;
        this.newStartLine = newStartLine;
        this.impactScope = impactScope;
    }
}
