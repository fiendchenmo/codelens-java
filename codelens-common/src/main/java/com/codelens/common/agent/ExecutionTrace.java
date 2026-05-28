package com.codelens.common.agent;

/**
 * 单次 Agent 执行的完整轨迹。
 */
public class ExecutionTrace {

    private final String taskId;
    private final TaskType taskType;
    private final ExecutionStatus status;
    private final boolean cacheHit;
    private final int retryCount;
    private final long latencyMs;

    public ExecutionTrace(String taskId, TaskType taskType, ExecutionStatus status,
                          boolean cacheHit, int retryCount, long latencyMs) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.status = status;
        this.cacheHit = cacheHit;
        this.retryCount = retryCount;
        this.latencyMs = latencyMs;
    }

    public String getTaskId() { return taskId; }
    public TaskType getTaskType() { return taskType; }
    public ExecutionStatus getStatus() { return status; }
    public boolean isCacheHit() { return cacheHit; }
    public int getRetryCount() { return retryCount; }
    public long getLatencyMs() { return latencyMs; }
}
