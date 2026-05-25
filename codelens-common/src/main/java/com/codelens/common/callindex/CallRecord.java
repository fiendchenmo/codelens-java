// SYNC_VERSION: 2026-05-25-v1
// IMPACT: LOGIC_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.callindex;

/**
 * 调用记录数据类。
 * 表示源码中一个方法调用关系。
 */
public class CallRecord {

    private final String callerClass;
    private final String callerMethod;
    private final String calleeClass;
    private final String calleeMethod;
    private final String callType;   // DIRECT, REFLECTION, SPRING_INJECTION
    private final String filePath;
    private final int lineNumber;
    private final String confidence; // HIGH, MEDIUM, LOW, 可为 null

    public CallRecord(String callerClass, String callerMethod, String calleeClass,
                      String calleeMethod, String callType, String filePath,
                      int lineNumber, String confidence) {
        this.callerClass = callerClass;
        this.callerMethod = callerMethod;
        this.calleeClass = calleeClass;
        this.calleeMethod = calleeMethod;
        this.callType = callType;
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.confidence = confidence;
    }

    public String getCallerClass() { return callerClass; }
    public String getCallerMethod() { return callerMethod; }
    public String getCalleeClass() { return calleeClass; }
    public String getCalleeMethod() { return calleeMethod; }
    public String getCallType() { return callType; }
    public String getFilePath() { return filePath; }
    public int getLineNumber() { return lineNumber; }
    public String getConfidence() { return confidence; }
}
