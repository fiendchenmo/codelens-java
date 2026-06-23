// SYNC_VERSION: 2026-06-23-v1
// IMPACT: LOGIC_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.db.query;

import com.codelens.common.agent.model.V3DbOperation;
import com.codelens.common.db.model.DbOperationRecord;
import com.codelens.common.db.model.FieldMappingRecord;
import com.codelens.common.db.model.TableSharingRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库分析查询仓库。
 * <p>
 * 提供对 graph.db 中 {@code db_operations} 和 {@code db_field_mappings}
 * 表的查询 API，回答以下问题：
 * </p>
 * <ul>
 *   <li>🔍 字段影响分析：哪些方法操作了指定表的指定字段？</li>
 *   <li>📊 表级影响分析：哪些方法操作了指定表？</li>
 *   <li>🔗 跨模块共享表检测：同一张表被几个 Mapper 操作？</li>
 *   <li>🔎 Mapper 方法关联：该方法操作了哪些表/字段？</li>
 *   <li>🗺️ Java 属性 ↔ 数据库列 映射查询</li>
 * </ul>
 */
public class DbAnalysisRepository {

    // ─── 字段影响分析 ─────────────────────────────────

    /**
     * 🔍 字段影响分析：哪些方法操作了指定表的指定字段？
     *
     * @param conn      graph.db 连接
     * @param tableName 数据库表名
     * @param fieldName 数据库字段名
     * @return 操作该字段的所有方法记录
     */
    public List<DbOperationRecord> findByTableAndField(Connection conn,
                                                        String tableName,
                                                        String fieldName)
            throws SQLException {
        List<DbOperationRecord> results = new ArrayList<DbOperationRecord>();
        if (tableName == null || fieldName == null) return results;

        String sql = "SELECT mapper_interface, method_name, sql_type, table_name," +
                " fields, xml_line, sql_text, source_type, source_file" +
                " FROM db_operations" +
                " WHERE table_name = ? AND (fields LIKE ? OR fields = '*')" +
                " ORDER BY sql_type, method_name";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, "%" + fieldName + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapOperationRecord(rs));
                }
            }
        }
        return results;
    }

    // ─── 表级影响分析 ─────────────────────────────────

    /**
     * 📊 表级影响分析：哪些方法操作了指定表？
     *
     * @param conn      graph.db 连接
     * @param tableName 数据库表名
     * @return 操作该表的所有方法记录
     */
    public List<DbOperationRecord> findByTable(Connection conn, String tableName)
            throws SQLException {
        List<DbOperationRecord> results = new ArrayList<DbOperationRecord>();
        if (tableName == null) return results;

        String sql = "SELECT mapper_interface, method_name, sql_type, table_name," +
                " fields, xml_line, sql_text, source_type, source_file" +
                " FROM db_operations WHERE table_name = ?" +
                " ORDER BY sql_type, method_name";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapOperationRecord(rs));
                }
            }
        }
        return results;
    }

    // ─── 跨模块共享表检测 ─────────────────────────────

    /**
     * 🔗 跨模块共享表检测：同一张表被几个 Mapper 操作？
     * <p>
     * 返回每张表的操作统计，按 Mapper 数量降序排列。
     * </p>
     *
     * @param conn graph.db 连接
     * @return 表共享记录列表
     */
    public List<TableSharingRecord> findTableSharing(Connection conn)
            throws SQLException {
        // 先收集原始数据：table_name → [(mapper_interface, package)]
        Map<String, Map<String, String>> tableMappers = new LinkedHashMap<String, Map<String, String>>();
        String sql = "SELECT DISTINCT table_name, mapper_interface" +
                " FROM db_operations ORDER BY table_name, mapper_interface";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String tableName = rs.getString("table_name");
                String mapperInterface = rs.getString("mapper_interface");
                if (tableName == null || mapperInterface == null) continue;

                Map<String, String> mappers = tableMappers.get(tableName);
                if (mappers == null) {
                    mappers = new LinkedHashMap<String, String>();
                    tableMappers.put(tableName, mappers);
                }
                mappers.put(mapperInterface, extractPackage(mapperInterface));
            }
        }

        // 转换为 TableSharingRecord
        List<TableSharingRecord> results = new ArrayList<TableSharingRecord>();
        for (Map.Entry<String, Map<String, String>> entry : tableMappers.entrySet()) {
            TableSharingRecord record = new TableSharingRecord();
            record.setTableName(entry.getKey());
            record.setMapperInterfaces(new ArrayList<String>(entry.getValue().keySet()));
            record.setMapperCount(entry.getValue().size());
            // 收集唯一的顶层包名（用于跨模块检测）
            java.util.Set<String> uniquePackages = new java.util.LinkedHashSet<String>();
            for (String pkg : entry.getValue().values()) {
                uniquePackages.add(extractTopLevelPackage(pkg, 3));
            }
            record.setPackages(new ArrayList<String>(uniquePackages));
            results.add(record);
        }

        // 按 mapperCount 降序排列
        java.util.Collections.sort(results, new java.util.Comparator<TableSharingRecord>() {
            @Override
            public int compare(TableSharingRecord a, TableSharingRecord b) {
                return Integer.compare(b.getMapperCount(), a.getMapperCount());
            }
        });

        return results;
    }

    // ─── Mapper 方法查询 ──────────────────────────────

    /**
     * 🔎 Mapper 方法 → SQL 操作：该方法操作了哪些表/字段？
     *
     * @param conn             graph.db 连接
     * @param mapperInterface  Mapper 接口全限定名
     * @param methodName       Mapper 方法名
     * @return 该方法的数据库操作记录列表
     */
    public List<DbOperationRecord> findByMapperMethod(Connection conn,
                                                       String mapperInterface,
                                                       String methodName)
            throws SQLException {
        List<DbOperationRecord> results = new ArrayList<DbOperationRecord>();
        if (mapperInterface == null || methodName == null) return results;

        String sql = "SELECT mapper_interface, method_name, sql_type, table_name," +
                " fields, xml_line, sql_text, source_type, source_file" +
                " FROM db_operations" +
                " WHERE mapper_interface = ? AND method_name = ?" +
                " ORDER BY table_name";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mapperInterface);
            ps.setString(2, methodName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapOperationRecord(rs));
                }
            }
        }
        return results;
    }

    // ─── 字段映射查询 ─────────────────────────────────

    /**
     * 🗺️ Java 属性 ↔ 数据库列 映射查询。
     *
     * @param conn             graph.db 连接
     * @param mapperInterface  Mapper 接口全限定名
     * @return 该 Mapper 的所有字段映射记录
     */
    public List<FieldMappingRecord> findFieldMapping(Connection conn,
                                                      String mapperInterface)
            throws SQLException {
        List<FieldMappingRecord> results = new ArrayList<FieldMappingRecord>();
        if (mapperInterface == null) return results;

        String sql = "SELECT mapper_interface, java_type, property_name," +
                " column_name, is_id, source_type, source_file" +
                " FROM db_field_mappings WHERE mapper_interface = ?" +
                " ORDER BY java_type, property_name";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, mapperInterface);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapFieldMappingRecord(rs));
                }
            }
        }
        return results;
    }

    /**
     * 🗺️ 按列名查找字段映射（反向查询：数据库列 → Java 属性）。
     *
     * @param conn       graph.db 连接
     * @param columnName 数据库列名
     * @return 所有映射到该列名的记录
     */
    public List<FieldMappingRecord> findFieldMappingByColumn(Connection conn,
                                                              String columnName)
            throws SQLException {
        List<FieldMappingRecord> results = new ArrayList<FieldMappingRecord>();
        if (columnName == null) return results;

        String sql = "SELECT mapper_interface, java_type, property_name," +
                " column_name, is_id, source_type, source_file" +
                " FROM db_field_mappings WHERE column_name = ?" +
                " ORDER BY mapper_interface, java_type";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapFieldMappingRecord(rs));
                }
            }
        }
        return results;
    }

    // ─── 表级统计 ─────────────────────────────────────

    /**
     * 获取所有被操作的表名列表。
     *
     * @param conn graph.db 连接
     * @return 去重的表名列表
     */
    public List<String> findAllTables(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<String>();
        String sql = "SELECT DISTINCT table_name FROM db_operations ORDER BY table_name";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tables.add(rs.getString("table_name"));
            }
        }
        return tables;
    }

    /**
     * 获取每个表的操作统计。
     *
     * @param conn graph.db 连接
     * @return Map<表名, Map<操作类型, 计数>>
     */
    public Map<String, Map<String, Integer>> getTableStats(Connection conn)
            throws SQLException {
        Map<String, Map<String, Integer>> stats = new LinkedHashMap<String, Map<String, Integer>>();
        String sql = "SELECT table_name, sql_type, COUNT(*) as cnt" +
                " FROM db_operations GROUP BY table_name, sql_type" +
                " ORDER BY table_name, sql_type";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String tableName = rs.getString("table_name");
                String sqlType = rs.getString("sql_type");
                int count = rs.getInt("cnt");
                Map<String, Integer> typeStats = stats.get(tableName);
                if (typeStats == null) {
                    typeStats = new LinkedHashMap<String, Integer>();
                    stats.put(tableName, typeStats);
                }
                typeStats.put(sqlType, count);
            }
        }
        return stats;
    }

    // ─── V3 桥接查询 ──────────────────────────────────

    /**
     * 🔗 根据调用链中的 Mapper 方法，查找关联的数据库操作。
     * <p>
     * 输入：V3Method.calls 中 target 含 *Mapper 的跨文件调用
     * 输出：每个 Mapper 方法对应的 V3DbOperation 列表，用于填充 V3Method.dbOperations。
     * </p>
     * <p>
     * 实现：遍历 Mapper 接口名列表，对每个接口-方法对查询 db_operations 表，
     * 收集操作的表名和字段名，去重合并后返回。
     * </p>
     *
     * @param conn              graph.db 连接
     * @param mapperInterfaces  Mapper 接口全限定名列表
     * @param methodNames       方法名列表（与 mapperInterfaces 对应）
     * @return V3DbOperation 列表（可能为空）
     */
    public List<V3DbOperation> findDbOperationsForCalls(Connection conn,
                                                         List<String> mapperInterfaces,
                                                         List<String> methodNames)
            throws SQLException {
        List<V3DbOperation> results = new ArrayList<V3DbOperation>();
        if (mapperInterfaces == null || methodNames == null
                || mapperInterfaces.isEmpty() || methodNames.isEmpty()) {
            return results;
        }

        // 为每个 Mapper 方法查询其数据库操作
        int size = Math.min(mapperInterfaces.size(), methodNames.size());
        java.util.Set<String> seenKeys = new java.util.LinkedHashSet<String>();

        for (int i = 0; i < size; i++) {
            String mapperInterface = mapperInterfaces.get(i);
            String methodName = methodNames.get(i);
            if (mapperInterface == null || methodName == null) continue;

            List<DbOperationRecord> records = findByMapperMethod(conn,
                    mapperInterface, methodName);
            for (DbOperationRecord record : records) {
                // 去重：同一个 Mapper 方法 + 表 + 操作类型 只记录一次
                String key = mapperInterface + "." + methodName
                        + "|" + record.getTableName() + "|" + record.getSqlType();
                if (seenKeys.contains(key)) continue;
                seenKeys.add(key);

                V3DbOperation v3Op = new V3DbOperation();
                v3Op.tableName = record.getTableName();
                v3Op.sqlType = record.getSqlType();
                v3Op.sourceMethod = mapperInterface + "." + methodName;
                // 解析字段列表
                if (record.getFields() != null && !record.getFields().isEmpty()) {
                    for (String f : record.getFields().split(",")) {
                        if (f != null && !f.trim().isEmpty()) {
                            v3Op.fields.add(f.trim());
                        }
                    }
                }
                results.add(v3Op);
            }
        }
        return results;
    }

    // ─── ResultSet 映射 ───────────────────────────────

    private static DbOperationRecord mapOperationRecord(ResultSet rs)
            throws SQLException {
        DbOperationRecord record = new DbOperationRecord();
        record.setMapperInterface(rs.getString("mapper_interface"));
        record.setMethodName(rs.getString("method_name"));
        record.setSqlType(rs.getString("sql_type"));
        record.setTableName(rs.getString("table_name"));
        record.setFields(rs.getString("fields"));
        record.setXmlLine(rs.getInt("xml_line"));
        record.setSqlText(rs.getString("sql_text"));
        record.setSourceType(rs.getString("source_type"));
        record.setSourceFile(rs.getString("source_file"));
        return record;
    }

    private static FieldMappingRecord mapFieldMappingRecord(ResultSet rs)
            throws SQLException {
        FieldMappingRecord record = new FieldMappingRecord();
        record.setMapperInterface(rs.getString("mapper_interface"));
        record.setJavaType(rs.getString("java_type"));
        record.setPropertyName(rs.getString("property_name"));
        record.setColumnName(rs.getString("column_name"));
        record.setId(rs.getInt("is_id") != 0);
        record.setSourceType(rs.getString("source_type"));
        record.setSourceFile(rs.getString("source_file"));
        return record;
    }

    // ─── 辅助方法 ─────────────────────────────────────

    /**
     * 从全限定类名提取包名。
     */
    static String extractPackage(String fullClassName) {
        if (fullClassName == null) return "";
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot > 0 ? fullClassName.substring(0, lastDot) : "";
    }

    /**
     * 提取前 N 级包名。
     */
    static String extractTopLevelPackage(String pkg, int levels) {
        if (pkg == null) return "";
        String[] parts = pkg.split("\\.");
        if (parts.length <= levels) return pkg;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels; i++) {
            if (i > 0) sb.append('.');
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}
