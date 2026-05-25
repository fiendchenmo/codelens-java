// SYNC_VERSION: 2026-05-25-v1
// IMPACT: LOGIC_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.callindex;

import java.util.List;

/**
 * 调用索引接口。
 * 管理源码中方法调用关系的持久化存储。
 */
public interface CallIndex extends AutoCloseable {

    /**
     * 插入一条调用记录。
     */
    void insert(CallRecord record);

    /**
     * 按调用方查询。
     */
    List<CallRecord> queryByCaller(String className, String methodName);

    /**
     * 按被调用方查询。
     */
    List<CallRecord> queryByCallee(String className, String methodName);

    /**
     * 删除某文件的所有记录（增量更新前清理）。
     */
    void deleteByFile(String filePath);

    /**
     * 关闭索引，释放资源。
     */
    void close();
}
