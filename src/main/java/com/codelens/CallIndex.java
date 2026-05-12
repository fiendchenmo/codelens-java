package com.codelens;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.logging.*;
import java.util.regex.*;

/**
 * FTS5 倒排索引模块 - 轻量级 Java 代码扫描器
 * 不使用完整 JavaParser AST，仅词法扫描 + 正则提取
 */
public class CallIndex {
    
    private static final Logger LOGGER = Logger.getLogger(CallIndex.class.getName());
    
    // 索引项类型
    public static final String TYPE_CLASS = "CLASS";
    public static final String TYPE_ANNOTATION = "ANNOTATION";
    public static final String TYPE_IMPORT = "IMPORT";
    public static final String TYPE_METHOD = "METHOD";
    public static final String TYPE_CALLEE = "CALLEE";
    public static final String TYPE_NULL_LITERAL = "NULL_LITERAL";
    
    // 索引存储目录
    private final Path indexDir;
    private final Path dbPath;
    private Connection conn;
    
    // 正则模式
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "(?:public|private|protected)?\\s*(?:static)?\\s*(?:final)?\\s*class\\s+(\\w+)|" +
        "(?:public|private|protected)?\\s*interface\\s+(\\w+)|" +
        "(?:public|private|protected)?\\s*(?:static)?\\s*enum\\s+(\\w+)"
    );
    
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("@(\\w+)");
    
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+([\\w.]+);");
    
    private static final Pattern METHOD_PATTERN = Pattern.compile(
        "(?:public|private|protected)?\\s*(?:static)?\\s*(?:synchronized)?\\s*" +
        "(?:\\w+(?:<[^>]+>)?|void)\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws[^{]*)?\\{?"
    );
    
    private static final Pattern METHOD_CALL_PATTERN = Pattern.compile(
        "(?:(\\w+)\\s*\\.\\s*)?(\\w+)\\s*\\([^)]*\\)\\s*;?"
    );
    
    private static final Pattern NULL_PATTERN = Pattern.compile("\\bnull\\b");
    
    // 常见框架注解
    private static final Set<String> FRAMEWORK_ANNOTATIONS = new HashSet<>(Arrays.asList(
        "Service", "Component", "Controller", "RestController", "Repository",
        "Transactional", "Autowired", "Value", "Bean", "Configuration",
        "Aspect", "Before", "After", "Around", "Pointcut"
    ));
    
    public CallIndex(Path projectRoot) throws SQLException {
        this.indexDir = projectRoot.resolve(".codelens");
        this.dbPath = indexDir.resolve("code_index.db");
        initIndexDir();
        initDatabase();
    }
    
    private void initIndexDir() throws SQLException {
        try {
            Files.createDirectories(indexDir);
        } catch (IOException e) {
            throw new SQLException("Cannot create index directory: " + indexDir, e);
        }
    }
    
    private void initDatabase() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            
            // PRAGMA 必须在 autoCommit=true 时执行
            try (Statement pragmaStmt = conn.createStatement()) {
                pragmaStmt.execute("PRAGMA journal_mode=WAL");
                pragmaStmt.execute("PRAGMA synchronous=NORMAL");
            }
            
            conn.setAutoCommit(false);


            
            // 创建 FTS5 虚拟表
            try (Statement stmt = conn.createStatement()) {
                
                stmt.execute("CREATE VIRTUAL TABLE IF NOT EXISTS code_index USING fts5(" +
                    "term, term_type, file_path, line_number, content='none', tokenize='unicode61')");
                
                stmt.execute("CREATE TABLE IF NOT EXISTS index_meta (" +
                    "file_path TEXT PRIMARY KEY, " +
                    "file_hash TEXT, " +
                    "last_indexed TEXT)");
                
            }
            conn.commit();
            LOGGER.info("Database initialized: " + dbPath);
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }
    }
    
    /**
     * 索引指定目录
     */
    public int indexDirectory(Path dir) throws SQLException {
        LOGGER.info("Indexing directory: " + dir);
        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
        
        try {
            Files.walk(dir)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(file -> {
                    try {
                        if (shouldIndexFile(file)) {
                            indexFile(file);
                            count.incrementAndGet();
                        }
                    } catch (Exception e) {
                        LOGGER.warning("Failed to index " + file + ": " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            throw new SQLException("Directory walk failed", e);
        }
        
        int total = count.get();
        LOGGER.info("Indexed " + total + " files");
        return total;
    }
    
    /**
     * 检查文件是否需要索引（基于 MD5 增量）
     */
    private boolean shouldIndexFile(Path file) throws IOException, java.security.NoSuchAlgorithmException {
        String hash = computeMD5(file);
        String filePath = file.toString();
        
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT file_hash FROM index_meta WHERE file_path = ?")) {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return !rs.getString(1).equals(hash);
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("Failed to check file hash: " + e.getMessage());
        }
        return true;
    }
    
    /**
     * 计算文件 MD5
     */
    private String computeMD5(Path file) throws IOException, java.security.NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * 索引单个文件
     */
    public void indexFile(Path file) throws IOException, SQLException, java.security.NoSuchAlgorithmException {
        List<String> lines = Files.readAllLines(file);
        String content = String.join("\n", lines);
        String filePath = file.toString();
        String hash = computeMD5(file);
        
        // 删除旧索引
        deleteFileIndex(filePath);
        
        // 提取并索引各项
        List<IndexEntry> entries = extractEntries(content, lines, filePath);
        
        // 批量插入
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO code_index(term, term_type, file_path, line_number) VALUES (?, ?, ?, ?)")) {
            for (IndexEntry entry : entries) {
                ps.setString(1, entry.term);
                ps.setString(2, entry.type);
                ps.setString(3, entry.filePath);
                ps.setInt(4, entry.lineNumber);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        
        // 更新元数据
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO index_meta(file_path, file_hash, last_indexed) VALUES (?, ?, ?)")) {
            ps.setString(1, filePath);
            ps.setString(2, hash);
            ps.setString(3, new java.sql.Timestamp(System.currentTimeMillis()).toString());
            ps.execute();
        }
        
        conn.commit();
    }
    
    /**
     * 从文件中提取索引项
     */
    private List<IndexEntry> extractEntries(String content, List<String> lines, String filePath) {
        List<IndexEntry> entries = new ArrayList<>();
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int lineNum = i + 1;
            
            // 提取类/接口/枚举声明
            Matcher classMatcher = CLASS_PATTERN.matcher(line);
            while (classMatcher.find()) {
                for (int g = 1; g <= classMatcher.groupCount(); g++) {
                    if (classMatcher.group(g) != null) {
                        entries.add(new IndexEntry(classMatcher.group(g), TYPE_CLASS, filePath, lineNum));
                    }
                }
            }
            
            // 提取注解（仅框架注解）
            Matcher annMatcher = ANNOTATION_PATTERN.matcher(line);
            while (annMatcher.find()) {
                String annName = annMatcher.group(1);
                if (FRAMEWORK_ANNOTATIONS.contains(annName) || annName.length() > 3) {
                    entries.add(new IndexEntry(annName, TYPE_ANNOTATION, filePath, lineNum));
                }
            }
            
            // 提取 import 语句
            Matcher impMatcher = IMPORT_PATTERN.matcher(line);
            while (impMatcher.find()) {
                String imp = impMatcher.group(1);
                entries.add(new IndexEntry(imp, TYPE_IMPORT, filePath, lineNum));
                // 同时索引 import 的最后一部分（类名）
                int lastDot = imp.lastIndexOf('.');
                if (lastDot > 0) {
                    entries.add(new IndexEntry(imp.substring(lastDot + 1), TYPE_IMPORT, filePath, lineNum));
                }
            }
            
            // 提取方法声明
            Matcher methodMatcher = METHOD_PATTERN.matcher(line);
            while (methodMatcher.find()) {
                String methodName = methodMatcher.group(1);
                if (!isTrivialMethod(methodName)) {
                    entries.add(new IndexEntry(methodName, TYPE_METHOD, filePath, lineNum));
                }
            }
            
            // 提取方法调用
            Matcher callMatcher = METHOD_CALL_PATTERN.matcher(line);
            while (callMatcher.find()) {
                String caller = callMatcher.group(1);
                String methodName = callMatcher.group(2);
                
                if (caller != null && !isTrivialMethod(methodName)) {
                    entries.add(new IndexEntry(caller + "." + methodName, TYPE_CALLEE, filePath, lineNum));
                    entries.add(new IndexEntry(methodName, TYPE_CALLEE, filePath, lineNum));
                }
            }
            
            // 标记 null 使用
            if (NULL_PATTERN.matcher(line).find()) {
                entries.add(new IndexEntry("null", TYPE_NULL_LITERAL, filePath, lineNum));
            }
        }
        
        return entries;
    }
    
    private boolean isTrivialMethod(String name) {
        if (name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) return true;
        if (name.startsWith("set") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) return true;
        if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))) return true;
        return false;
    }
    
    /**
     * 删除文件的索引
     */
    private void deleteFileIndex(String filePath) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM code_index WHERE file_path = ?")) {
            ps.setString(1, filePath);
            ps.execute();
        }
    }
    
    /**
     * 查询包含特定类的文件
     */
    public List<IndexResult> findByClass(String className) throws SQLException {
        List<IndexResult> results = new ArrayList<>();
        
        // 查询 import 和 class 定义中的类
        String sql = "SELECT term, term_type, file_path, line_number FROM code_index " +
                    "WHERE term = ? AND term_type IN (?, ?) ORDER BY file_path, line_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, className);
            ps.setString(2, TYPE_IMPORT);
            ps.setString(3, TYPE_CLASS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new IndexResult(
                        rs.getString("term"),
                        rs.getString("term_type"),
                        rs.getString("file_path"),
                        rs.getInt("line_number")
                    ));
                }
            }
        }
        
        return results;
    }
    
    /**
     * 查询方法被谁调用
     */
    public List<IndexResult> findCallers(String methodName) throws SQLException {
        List<IndexResult> results = new ArrayList<>();
        
        String sql = "SELECT term, term_type, file_path, line_number FROM code_index " +
                    "WHERE term LIKE ? AND term_type = ? ORDER BY file_path, line_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%." + methodName);
            ps.setString(2, TYPE_CALLEE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new IndexResult(
                        rs.getString("term"),
                        rs.getString("term_type"),
                        rs.getString("file_path"),
                        rs.getInt("line_number")
                    ));
                }
            }
        }
        
        return results;
    }
    
    /**
     * 查询所有类/接口定义
     */
    public List<IndexResult> findAllClasses() throws SQLException {
        List<IndexResult> results = new ArrayList<>();
        
        String sql = "SELECT term, term_type, file_path, line_number FROM code_index " +
                    "WHERE term_type = ? ORDER BY file_path, line_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TYPE_CLASS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new IndexResult(
                        rs.getString("term"),
                        rs.getString("term_type"),
                        rs.getString("file_path"),
                        rs.getInt("line_number")
                    ));
                }
            }
        }
        
        return results;
    }
    
    /**
     * 查询特定类型的索引项
     */
    public List<IndexResult> findByType(String type) throws SQLException {
        List<IndexResult> results = new ArrayList<>();
        
        String sql = "SELECT term, term_type, file_path, line_number FROM code_index " +
                    "WHERE term_type = ? ORDER BY term, file_path";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new IndexResult(
                        rs.getString("term"),
                        rs.getString("term_type"),
                        rs.getString("file_path"),
                        rs.getInt("line_number")
                    ));
                }
            }
        }
        
        return results;
    }
    
    public void close() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                LOGGER.warning("Failed to close database: " + e.getMessage());
            }
        }
    }
    
    // 内部类：索引项
    static class IndexEntry {
        String term;
        String type;
        String filePath;
        int lineNumber;
        
        IndexEntry(String term, String type, String filePath, int lineNumber) {
            this.term = term;
            this.type = type;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
        }
    }
    
    // 内部类：查询结果
    public static class IndexResult {
        public final String term;
        public final String type;
        public final String filePath;
        public final int lineNumber;
        
        IndexResult(String term, String type, String filePath, int lineNumber) {
            this.term = term;
            this.type = type;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
        }
        
        @Override
        public String toString() {
            return String.format("%s:%d | %s [%s]", filePath, lineNumber, term, type);
        }
    }
}
