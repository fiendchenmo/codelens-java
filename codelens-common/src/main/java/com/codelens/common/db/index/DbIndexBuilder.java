// SYNC_VERSION: 2026-06-23-v1
// IMPACT: LOGIC_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.db.index;

import com.codelens.common.db.model.FieldMapEntry;
import com.codelens.common.db.model.FieldMapping;
import com.codelens.common.db.model.SqlOperation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 数据库操作索引构建器。
 * <p>
 * 接收 {@link SqlOperation} 列表，将数据库操作映射关系写入 graph.db。
 * 支持全量构建和增量构建（基于文件 MD5 hash）。
 * </p>
 *
 * <p>写入的表：</p>
 * <ul>
 *   <li>{@code db_operations} — 方法 → SQL 操作 → 表/字段 映射</li>
 *   <li>{@code db_field_mappings} — Java 属性 ↔ 数据库列 映射</li>
 * </ul>
 */
public class DbIndexBuilder {

    // ─── DDL ──────────────────────────────────────────

    private static final String CREATE_DB_OPERATIONS =
            "CREATE TABLE IF NOT EXISTS db_operations (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    mapper_interface TEXT NOT NULL," +
            "    method_name TEXT NOT NULL," +
            "    sql_type TEXT NOT NULL," +
            "    table_name TEXT NOT NULL," +
            "    fields TEXT," +
            "    xml_line INTEGER," +
            "    sql_text TEXT," +
            "    source_type TEXT NOT NULL," +
            "    source_file TEXT NOT NULL," +
            "    file_hash TEXT," +
            "    created_at TEXT DEFAULT (datetime('now'))" +
            ")";

    private static final String CREATE_DB_FIELD_MAPPINGS =
            "CREATE TABLE IF NOT EXISTS db_field_mappings (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "    mapper_interface TEXT NOT NULL," +
            "    java_type TEXT NOT NULL," +
            "    property_name TEXT NOT NULL," +
            "    column_name TEXT NOT NULL," +
            "    is_id INTEGER DEFAULT 0," +
            "    source_type TEXT NOT NULL," +
            "    source_file TEXT NOT NULL" +
            ")";

    private static final String CREATE_IDX_DB_OPS_TABLE =
            "CREATE INDEX IF NOT EXISTS idx_db_ops_table ON db_operations(table_name)";
    private static final String CREATE_IDX_DB_OPS_MAPPER =
            "CREATE INDEX IF NOT EXISTS idx_db_ops_mapper ON db_operations(mapper_interface, method_name)";
    private static final String CREATE_IDX_DB_FM_COLUMN =
            "CREATE INDEX IF NOT EXISTS idx_db_fm_column ON db_field_mappings(column_name)";
    private static final String CREATE_IDX_DB_FM_MAPPER =
            "CREATE INDEX IF NOT EXISTS idx_db_fm_mapper ON db_field_mappings(mapper_interface)";

    private static final String INSERT_OPERATION =
            "INSERT INTO db_operations" +
            " (mapper_interface, method_name, sql_type, table_name, fields," +
            "  xml_line, sql_text, source_type, source_file, file_hash)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_FIELD_MAPPING =
            "INSERT INTO db_field_mappings" +
            " (mapper_interface, java_type, property_name, column_name, is_id," +
            "  source_type, source_file)" +
            " VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String DELETE_OPS_BY_FILE =
            "DELETE FROM db_operations WHERE source_file = ?";
    private static final String DELETE_FM_BY_FILE =
            "DELETE FROM db_field_mappings WHERE source_file = ?";

    // ─── 公共方法 ─────────────────────────────────────

    /**
     * 初始化数据库表结构（幂等）。
     * <p>
     * 如果表/索引已存在则跳过。应在首次使用 graph.db 时调用。
     * </p>
     *
     * @param conn graph.db 连接
     */
    public static void initSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // 检查表是否已存在
            boolean hasDbOps = false;
            boolean hasDbFm = false;
            try (java.sql.ResultSet rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='db_operations'")) {
                hasDbOps = rs.next();
            }
            try (java.sql.ResultSet rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='db_field_mappings'")) {
                hasDbFm = rs.next();
            }

            if (!hasDbOps) {
                stmt.execute(CREATE_DB_OPERATIONS);
                stmt.execute(CREATE_IDX_DB_OPS_TABLE);
                stmt.execute(CREATE_IDX_DB_OPS_MAPPER);
            }
            if (!hasDbFm) {
                stmt.execute(CREATE_DB_FIELD_MAPPINGS);
                stmt.execute(CREATE_IDX_DB_FM_COLUMN);
                stmt.execute(CREATE_IDX_DB_FM_MAPPER);
            }
        }
    }

    /**
     * 将 SQL 操作写入 graph.db。
     *
     * @param conn       graph.db 连接（由调用方提供）
     * @param operations MyBatisXmlParser 的解析结果
     * @param fileHash   文件 MD5 hash（用于增量判断）
     */
    public static void build(Connection conn, List<SqlOperation> operations,
                             String fileHash) throws SQLException {
        if (operations == null || operations.isEmpty()) {
            return;
        }

        initSchema(conn);

        // 如果有 sourceFile，先删除该文件的旧记录（幂等覆盖）
        String sourceFile = operations.get(0).getSourceFile();
        if (sourceFile != null && !sourceFile.isEmpty()) {
            deleteByFile(conn, sourceFile);
        }

        try (PreparedStatement opsStmt = conn.prepareStatement(INSERT_OPERATION);
             PreparedStatement fmStmt = conn.prepareStatement(INSERT_FIELD_MAPPING)) {

            for (SqlOperation op : operations) {
                // 跳过没有表名和字段名的操作（空 SQL 片段引用等）
                if (op.getTables().isEmpty()) {
                    continue;
                }

                String fieldsStr = op.getFields().isEmpty()
                        ? null
                        : String.join(",", op.getFields());

                // 每个表写入一行
                for (String tableName : op.getTables()) {
                    int idx = 1;
                    opsStmt.setString(idx++, op.getMapperInterface());
                    opsStmt.setString(idx++, op.getMethodName());
                    opsStmt.setString(idx++, op.getSqlType() != null
                            ? op.getSqlType().name() : "SELECT");
                    opsStmt.setString(idx++, tableName);
                    opsStmt.setString(idx++, fieldsStr);
                    opsStmt.setInt(idx++, op.getXmlLine());
                    opsStmt.setString(idx++, op.getSqlText());
                    opsStmt.setString(idx++, op.getSourceType() != null
                            ? op.getSourceType() : SqlOperation.SOURCE_MYBATIS_XML);
                    opsStmt.setString(idx++, sourceFile);
                    opsStmt.setString(idx++, fileHash);
                    opsStmt.addBatch();
                }

                // 写入字段映射（如果有）
                FieldMapping fm = op.getFieldMapping();
                if (fm != null && fm.getEntries() != null) {
                    for (FieldMapEntry entry : fm.getEntries()) {
                        int fi = 1;
                        fmStmt.setString(fi++, op.getMapperInterface());
                        fmStmt.setString(fi++, fm.getJavaType());
                        fmStmt.setString(fi++, entry.getProperty());
                        fmStmt.setString(fi++, entry.getColumn());
                        fmStmt.setInt(fi++, entry.isId() ? 1 : 0);
                        fmStmt.setString(fi++,
                                FieldMappingRecordSource.MYBATIS_RESULTMAP);
                        fmStmt.setString(fi++, sourceFile);
                        fmStmt.addBatch();
                    }
                }
            }

            opsStmt.executeBatch();
            fmStmt.executeBatch();
        }
    }

    /**
     * 增量构建：仅处理 hash 变化的文件。
     *
     * @param conn      graph.db 连接
     * @param mapperDir resources/mapper 目录路径
     */
    public static void buildIncremental(Connection conn, String mapperDir)
            throws SQLException, IOException {
        File dir = new File(mapperDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        initSchema(conn);
        buildIncrementalRecursive(conn, dir);
    }

    private static void buildIncrementalRecursive(Connection conn, File dir)
            throws SQLException, IOException {
        File[] xmlFiles = dir.listFiles(new java.io.FilenameFilter() {
            @Override
            public boolean accept(File d, String name) {
                return name.endsWith(".xml");
            }
        });

        if (xmlFiles != null) {
            for (File xmlFile : xmlFiles) {
                String content = new String(Files.readAllBytes(xmlFile.toPath()),
                        StandardCharsets.UTF_8);
                String hash = md5(content);

                // 检查 hash 是否变化
                if (!isHashChanged(conn, xmlFile.getAbsolutePath(), hash)) {
                    continue;
                }

                // 解析并构建
                java.util.List<SqlOperation> ops =
                        com.codelens.common.db.parser.MyBatisXmlParser.parse(
                                content, xmlFile.getAbsolutePath());
                build(conn, ops, hash);
            }
        }

        // 递归子目录
        File[] subDirs = dir.listFiles(new java.io.FileFilter() {
            @Override
            public boolean accept(File f) { return f.isDirectory(); }
        });
        if (subDirs != null) {
            for (File subDir : subDirs) {
                buildIncrementalRecursive(conn, subDir);
            }
        }
    }

    // ─── 辅助方法 ─────────────────────────────────────

    /**
     * 删除指定文件的所有索引记录。
     */
    private static void deleteByFile(Connection conn, String filePath)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(DELETE_OPS_BY_FILE)) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(DELETE_FM_BY_FILE)) {
            ps.setString(1, filePath);
            ps.executeUpdate();
        }
    }

    /**
     * 检查文件的 hash 是否与已索引的记录不同。
     */
    private static boolean isHashChanged(Connection conn, String filePath, String newHash)
            throws SQLException {
        String sql = "SELECT file_hash FROM db_operations WHERE source_file = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filePath);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String oldHash = rs.getString("file_hash");
                    return !newHash.equals(oldHash);
                }
            }
        }
        return true; // 没找到记录，需要索引
    }

    /**
     * 计算字符串的 MD5 hash。
     */
    static String md5(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 总是可用的
            return Integer.toHexString(content.hashCode());
        }
    }

    /**
     * 字段映射来源常量（内部使用）。
     */
    static final class FieldMappingRecordSource {
        static final String MYBATIS_RESULTMAP = "MYBATIS_RESULTMAP";
        static final String JPA_COLUMN = "JPA_COLUMN";
    }
}
