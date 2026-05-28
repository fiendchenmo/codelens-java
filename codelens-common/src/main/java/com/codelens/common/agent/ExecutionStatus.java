package com.codelens.common.agent;

/**
 * 任务执行状态。
 * <p>
 * Phase 1 定义基础状态，后续 Phase 扩展更多状态值。
 */
public enum ExecutionStatus {
    PENDING,
    COMPLETED,
    FAILED
}
