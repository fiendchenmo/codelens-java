package com.codelens.common.agent;

import java.util.UUID;

/**
 * 泛型任务数据类，封装单个 Agent 任务的输入输出。
 *
 * @param <TInput>  输入类型
 * @param <TOutput> 输出类型
 */
public class AnalysisTask<TInput, TOutput> {

    private final String taskId;
    private final TaskType taskType;
    private final TInput input;
    private TOutput output;
    private ExecutionStatus status;
    private final long createdAt;
    private Long completedAt;

    public AnalysisTask(TaskType taskType, TInput input) {
        this.taskId = UUID.randomUUID().toString();
        this.taskType = taskType;
        this.input = input;
        this.output = null;
        this.status = ExecutionStatus.PENDING;
        this.createdAt = System.currentTimeMillis();
        this.completedAt = null;
    }

    public String getTaskId() {
        return taskId;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public TInput getInput() {
        return input;
    }

    public TOutput getOutput() {
        return output;
    }

    /**
     * 设置输出，同时更新 status 为 COMPLETED，completedAt 为当前时间。
     */
    public void setOutput(TOutput output) {
        this.output = output;
        this.status = ExecutionStatus.COMPLETED;
        this.completedAt = System.currentTimeMillis();
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }
}
