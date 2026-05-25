// SYNC_VERSION: 2026-05-25-v1
// IMPACT: LOGIC_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.callindex;

import java.nio.file.Path;

/**
 * CallIndex 生命周期管理器。
 * 负责创建、获取和关闭 CallIndex 实例。
 */
public class CallIndexManager {

    private final Path projectRoot;
    private SQLiteCallIndex index;
    private boolean closed;

    private static final String DB_SUBDIR = ".codelens";
    private static final String DB_FILE = "callindex.db";

    public CallIndexManager(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.closed = false;
    }

    /**
     * 获取或创建 CallIndex 实例。
     * @return CallIndex 实例
     * @throws IllegalStateException 如果已关闭
     */
    public synchronized CallIndex getIndex() {
        if (closed) {
            throw new IllegalStateException("CallIndexManager is already closed");
        }
        if (index == null) {
            Path dbDir = projectRoot.resolve(DB_SUBDIR);
            dbDir.toFile().mkdirs();
            Path dbPath = dbDir.resolve(DB_FILE);
            index = new SQLiteCallIndex(dbPath.toString());
        }
        return index;
    }

    /**
     * 关闭 CallIndex，释放资源。
     */
    public synchronized void close() {
        if (index != null) {
            index.close();
            index = null;
        }
        closed = true;
    }

    /**
     * 检查管理器是否已关闭。
     */
    public boolean isClosed() {
        return closed;
    }
}
