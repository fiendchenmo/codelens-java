package com.codelens.common.agent;

/**
 * 任务执行状态。
 * <p>
 * Phase 4 扩展：RUNNING、SKIPPED、CACHED。
 */
public enum ExecutionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED,
    CACHED
}
