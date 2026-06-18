// SYNC_VERSION: 2026-06-18-v1
// IMPACT: LOGIC_CHANGE
package com.codelens.platform;

/**
 * 进度报告抽象层 — 替代 IDEA ProgressIndicator
 * 插件端实现用 ProgressIndicator，CLI 端实现用 System.out
 */
public interface ProgressReporter {

    /**
     * 开始一个任务
     *
     * @param taskName 任务名称
     */
    void start(String taskName);

    /**
     * 更新进度
     *
     * @param workDone 已完成工作量
     * @param message  当前状态描述
     */
    void update(int workDone, String message);

    /**
     * 任务完成
     */
    void finish();

    /**
     * 检查是否被取消
     *
     * @return true 如果用户取消了操作
     */
    boolean isCancelled();

    /**
     * 显示错误信息
     */
    void error(String message, Throwable e);

    /**
     * 显示警告信息
     */
    void warning(String message);
}
