// SYNC_VERSION: 2026-06-18-v1
// IMPACT: LOGIC_CHANGE
package com.codelens.platform;

import java.io.PrintStream;

/**
 * CLI 端进度报告实现 — 基于 System.out
 * 适用于无 IDE 环境的纯命令行分析场景，进度信息直接输出到终端
 */
public class CliProgressReporter implements ProgressReporter {

    private final PrintStream out;
    private final PrintStream err;
    private String taskName;

    public CliProgressReporter() {
        this(System.out, System.err);
    }

    public CliProgressReporter(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    @Override
    public void start(String taskName) {
        this.taskName = taskName;
        out.println("[START] " + taskName);
    }

    @Override
    public void update(int workDone, String message) {
        out.println("[" + workDone + "%] [" + taskName + "] " + message);
    }

    @Override
    public void finish() {
        out.println("[DONE] " + taskName);
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void error(String message, Throwable e) {
        err.println("[ERROR] [" + taskName + "] " + message);
        if (e != null) {
            e.printStackTrace(err);
        }
    }

    @Override
    public void warning(String message) {
        out.println("[WARN] [" + taskName + "] " + message);
    }
}
