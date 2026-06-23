// SYNC_VERSION: 2026-06-23-v1
// IMPACT: SCHEMA_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.agent.model;

import java.util.ArrayList;
import java.util.List;

/**
 * V3 方法分析结果中的数据库操作条目。
 * <p>
 * 描述一个方法在执行过程中触发的数据库操作。
 * 由 {@link com.codelens.common.db.query.DbAnalysisRepository#findDbOperationsForCalls}
 * 填充，用于扩展 V3Result 的输出。
 * </p>
 *
 * <p>在 V3 JSON 中的位置：</p>
 * <pre>{@code
 *   methods[].db_operations: [
 *     {
 *       "table_name": "sys_user",
 *       "sql_type": "SELECT",
 *       "fields": ["user_name", "status"],
 *       "source_method": "SysUserMapper.selectUserByUserName"
 *     }
 *   ]
 * }</pre>
 */
public class V3DbOperation {

    /** 操作的表名 */
    public String tableName;

    /** SQL 操作类型（SELECT/INSERT/UPDATE/DELETE） */
    public String sqlType;

    /** 涉及的字段列表 */
    public List<String> fields;

    /** 实际执行 SQL 的 Mapper 方法全名（MapperInterface.methodName） */
    public String sourceMethod;

    public V3DbOperation() {
        this.fields = new ArrayList<String>();
    }

    public V3DbOperation(String tableName, String sqlType,
                         List<String> fields, String sourceMethod) {
        this.tableName = tableName;
        this.sqlType = sqlType;
        this.fields = fields != null ? fields : new ArrayList<String>();
        this.sourceMethod = sourceMethod;
    }
}
