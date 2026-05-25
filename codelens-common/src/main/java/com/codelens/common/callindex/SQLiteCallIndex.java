// SYNC_VERSION: 2026-05-25-v1
// IMPACT: LOGIC_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.callindex;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite 实现的 CallIndex。
 * 使用 WAL 模式支持并发读写。
 */
public class SQLiteCallIndex implements CallIndex {

    private Connection conn;
    private final Object lock = new Object();

    public SQLiteCallIndex(String dbPath) {
        try {
            // 确保父目录存在（sqlite-jdbc 不会自动创建目录）
            File parentDir = new File(dbPath).getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            // 先初始化表结构和 PRAGMA（WAL 必须在事务外设置）
            initDatabase();
            conn.setAutoCommit(false);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SQLiteCallIndex at " + dbPath, e);
        }
    }

    private void initDatabase() throws SQLException {
        // 自动提交模式下运行，每条 DDL 自动提交
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("CREATE TABLE IF NOT EXISTS call_records (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "caller_class TEXT NOT NULL, " +
                "caller_method TEXT NOT NULL, " +
                "callee_class TEXT NOT NULL, " +
                "callee_method TEXT, " +
                "call_type TEXT NOT NULL, " +
                "file_path TEXT NOT NULL, " +
                "line_number INTEGER, " +
                "confidence TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_caller ON call_records(caller_class, caller_method)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_callee ON call_records(callee_class, callee_method)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file ON call_records(file_path)");
        }
    }

    @Override
    public void insert(CallRecord record) {
        String sql = "INSERT INTO call_records(caller_class, caller_method, callee_class, " +
                    "callee_method, call_type, file_path, line_number, confidence) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        synchronized (lock) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setInsertParams(ps, record);
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert call record", e);
        }
        }
    }

    @Override
    public void batchInsert(List<CallRecord> records) {
        if (records == null || records.isEmpty()) return;
        String sql = "INSERT INTO call_records(caller_class, caller_method, callee_class, " +
                    "callee_method, call_type, file_path, line_number, confidence) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        synchronized (lock) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (CallRecord record : records) {
                setInsertParams(ps, record);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to batch insert call records", e);
        }
        }
    }

    @Override
    public List<CallRecord> queryByCaller(String className, String methodName) {
        String sql = "SELECT caller_class, caller_method, callee_class, callee_method, " +
                    "call_type, file_path, line_number, confidence FROM call_records " +
                    "WHERE caller_class = ? AND caller_method = ? ORDER BY line_number";
        List<CallRecord> results = new ArrayList<CallRecord>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, className);
            ps.setString(2, methodName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRecord(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query by caller", e);
        }
        return results;
    }

    @Override
    public List<CallRecord> queryByCallee(String className, String methodName) {
        String sql = "SELECT caller_class, caller_method, callee_class, callee_method, " +
                    "call_type, file_path, line_number, confidence FROM call_records " +
                    "WHERE callee_class = ? AND callee_method = ? ORDER BY line_number";
        List<CallRecord> results = new ArrayList<CallRecord>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, className);
            ps.setString(2, methodName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRecord(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query by callee", e);
        }
        return results;
    }

    @Override
    public void deleteByFile(String filePath) {
        String sql = "DELETE FROM call_records WHERE file_path = ?";
        synchronized (lock) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filePath);
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete records for file: " + filePath, e);
        }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to close database connection", e);
            }
            conn = null;
        }
        }
    }

    private void setInsertParams(PreparedStatement ps, CallRecord record) throws SQLException {
        ps.setString(1, record.getCallerClass());
        ps.setString(2, record.getCallerMethod());
        ps.setString(3, record.getCalleeClass());
        ps.setString(4, record.getCalleeMethod());
        ps.setString(5, record.getCallType());
        ps.setString(6, record.getFilePath());
        ps.setInt(7, record.getLineNumber());
        if (record.getConfidence() != null) {
            ps.setString(8, record.getConfidence());
        } else {
            ps.setNull(8, Types.VARCHAR);
        }
    }

    private CallRecord mapRecord(ResultSet rs) throws SQLException {
        return new CallRecord(
            rs.getString("caller_class"),
            rs.getString("caller_method"),
            rs.getString("callee_class"),
            rs.getString("callee_method"),
            rs.getString("call_type"),
            rs.getString("file_path"),
            rs.getInt("line_number"),
            rs.getString("confidence")
        );
    }

    // 仅用于测试：获取当前总记录数
    int getRecordCount() {
        synchronized (lock) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM call_records")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count records", e);
        }
        return 0;
        }
    }
}
