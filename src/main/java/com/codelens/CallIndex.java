package com.codelens;

import com.codelens.common.utils.MethodFilter;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Expression;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.logging.*;
import java.util.Optional;

import com.github.javaparser.ast.ImportDeclaration;

/**
 * 代码索引模块 - 使用 JavaParser 进行精确解析
 * 不使用正则表达式，能正确处理泛型、Lambda、Builder 链式调用等复杂语法
 */
public class CallIndex implements AutoCloseable {
    
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
    private final java.util.Set<String> indexedSrcRoots = new java.util.HashSet<>();
    
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
            conn.setAutoCommit(false);
            
            // 检查旧版FTS5虚拟表残留
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='code_index'")) {
                    if (rs.next()) {
                        try (ResultSet rs2 = stmt.executeQuery(
                                "SELECT sql FROM sqlite_master WHERE name='code_index'")) {
                            if (rs2.next()) {
                                String sql = rs2.getString(1);
                                if (sql != null && sql.contains("VIRTUAL TABLE")) {
                                    stmt.execute("DROP TABLE IF EXISTS code_index");
                                    stmt.execute("DROP TABLE IF EXISTS code_index_data");
                                    stmt.execute("DROP TABLE IF EXISTS code_index_idx");
                                    stmt.execute("DROP TABLE IF EXISTS code_index_content");
                                    stmt.execute("DROP TABLE IF EXISTS code_index_docsize");
                                    stmt.execute("DROP TABLE IF EXISTS code_index_config");
                                    conn.commit();
                                    LOGGER.info("Cleaned up legacy FTS5 tables");
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                // 忽略检查失败
            }
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS code_index (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "term TEXT NOT NULL, " +
                    "term_type TEXT NOT NULL, " +
                    "file_path TEXT NOT NULL, " +
                    "line_number INTEGER NOT NULL)");
                
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_term ON code_index(term)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_term_type ON code_index(term_type)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_path ON code_index(file_path)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_term_and_type ON code_index(term, term_type)");
                
                stmt.execute("CREATE TABLE IF NOT EXISTS index_meta (" +
                    "file_path TEXT PRIMARY KEY, " +
                    "file_hash TEXT, " +
                    "last_indexed TEXT)");
            }
            conn.commit();
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }
    }
    
    /**
     * 索引指定目录
     */
    public int indexDirectory(Path dir) throws SQLException {
        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
        indexedSrcRoots.clear();

        try {
            Files.walk(dir).forEach(p -> {
                if (p.toString().endsWith(".java")) {
                    if (isSrcRoot(p)) {
                        String srcRoot = findSrcRoot(p);
                        if (!srcRoot.isEmpty() && !indexedSrcRoots.contains(srcRoot)) {
                            indexedSrcRoots.add(srcRoot);
                        }
                    }
                    try {
                        if (shouldIndexFile(p)) {
                            indexFile(p);
                            count.incrementAndGet();
                        }
                    } catch (Exception e) {
                        LOGGER.warning("Failed to index " + p + ": " + e.getMessage());
                    }
                }
            });
        } catch (IOException e) {
            throw new SQLException("Directory walk failed", e);
        }

        int total = count.get();
        return total;
    }
    
    /**
     * 索引指定目录并返回详细信息
     * @return String数组: [文件数量, 源码根目录列表(逗号分隔)]
     */
    public String[] indexDirectoryWithSrcRoots(Path dir) throws SQLException {
        indexDirectory(dir); // 先执行索引
        int count = 0;
        try {
            java.sql.Statement stmt = conn.createStatement();
            java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM code_index");
            if (rs.next()) {
                count = rs.getInt(1);
            }
            rs.close();
            stmt.close();
        } catch (java.sql.SQLException e) {
            // 忽略
        }
        String srcRootsStr = String.join(",", indexedSrcRoots);
        return new String[]{String.valueOf(count), srcRootsStr};
    }
    
    /**
     * 获取索引数据库路径
     */
    public Path getDbPath() {
        return dbPath;
    }

    /**
     * 获取已索引的源码根目录列表
     */
    public List<String> getIndexedSrcRoots() {
        return new ArrayList<>(indexedSrcRoots);
    }
    
    private boolean isSrcRoot(Path javaFile) {
        String path = javaFile.toString();
        return path.contains("/src/main/java/") || path.contains("/src/test/java/") ||
               path.contains("\\src\\main\\java\\") || path.contains("\\src\\test\\java\\");
    }
    
    private String findSrcRoot(Path javaFile) {
        String path = javaFile.toString();
        int idx;
        if ((idx = path.indexOf("/src/main/java/")) >= 0) {
            return path.substring(0, idx + 5); // /src
        }
        if ((idx = path.indexOf("/src/test/java/")) >= 0) {
            return path.substring(0, idx + 5);
        }
        if ((idx = path.indexOf("\\src\\main\\java\\")) >= 0) {
            return path.substring(0, idx + 5);
        }
        if ((idx = path.indexOf("\\src\\test\\java\\")) >= 0) {
            return path.substring(0, idx + 5);
        }
        // fallback: 返回父目录
        return javaFile.getParent() != null ? javaFile.getParent().toString() : "";
    }
    
    private boolean shouldIndexFile(Path file) throws IOException, NoSuchAlgorithmException {
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
    
    private String computeMD5(Path file) throws IOException, NoSuchAlgorithmException {
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
    public void indexFile(Path file) throws IOException, SQLException, NoSuchAlgorithmException {
        String filePath = file.toString();
        String hash = computeMD5(file);
        
        deleteFileIndex(filePath);
        
        List<IndexEntry> entries = extractEntriesWithJavaParser(file);
        
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
     * 使用 JavaParser 从文件中提取索引项
     */
    private List<IndexEntry> extractEntriesWithJavaParser(Path file) {
        List<IndexEntry> entries = new ArrayList<>();
        String filePath = file.toString();
        
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            
            // 提取 CLASS/INTERFACE 声明
            for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                String className = cls.getNameAsString();
                int lineNumber = cls.getRange().map(r -> r.begin.line).orElse(0);
                
                entries.add(new IndexEntry(className, TYPE_CLASS, filePath, lineNumber));
                
                // 提取类上的注解
                cls.getAnnotations().forEach(ann -> {
                    String annName = ann.getNameAsString();
                    int annLine = ann.getRange().map(r -> r.begin.line).orElse(lineNumber);
                    entries.add(new IndexEntry(annName, TYPE_ANNOTATION, filePath, annLine));
                });
            }
            
            // 提取 ENUM 声明
            for (EnumDeclaration enumDecl : cu.findAll(EnumDeclaration.class)) {
                String enumName = enumDecl.getNameAsString();
                int lineNumber = enumDecl.getRange().map(r -> r.begin.line).orElse(0);
                
                entries.add(new IndexEntry(enumName, TYPE_CLASS, filePath, lineNumber));
                
                enumDecl.getAnnotations().forEach(ann -> {
                    String annName = ann.getNameAsString();
                    int annLine = ann.getRange().map(r -> r.begin.line).orElse(lineNumber);
                    entries.add(new IndexEntry(annName, TYPE_ANNOTATION, filePath, annLine));
                });
            }
            
            // 提取 IMPORT 语句
            for (ImportDeclaration imp : cu.getImports()) {
                String fullName = imp.getNameAsString();
                int lineNumber = imp.getRange().map(r -> r.begin.line).orElse(0);
                
                entries.add(new IndexEntry(fullName, TYPE_IMPORT, filePath, lineNumber));
                
                int lastDot = fullName.lastIndexOf('.');
                if (lastDot > 0) {
                    String shortName = fullName.substring(lastDot + 1);
                    entries.add(new IndexEntry(shortName, TYPE_IMPORT, filePath, lineNumber));
                }
            }
            
            // 提取方法声明
            for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                String methodName = method.getNameAsString();
                int lineNumber = method.getRange().map(r -> r.begin.line).orElse(0);
                
                if (!MethodFilter.isTrivialCall(methodName)) {
                    entries.add(new IndexEntry(methodName, TYPE_METHOD, filePath, lineNumber));
                }
            }
            
            // 提取方法调用
            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                int lineNumber = call.getRange().map(r -> r.begin.line).orElse(0);
                String methodName = call.getNameAsString();
                
                if (!MethodFilter.isTrivialCall(methodName)) {
                    Optional<Expression> scopeOpt = call.getScope();
                    if (scopeOpt.isPresent()) {
                        Expression scope = scopeOpt.get();
                        String scopeStr = scope.toString();
                        entries.add(new IndexEntry(scopeStr + "." + methodName, TYPE_CALLEE, filePath, lineNumber));
                    }
                    entries.add(new IndexEntry(methodName, TYPE_CALLEE, filePath, lineNumber));
                }
            }
            
            // 简单检查 null 字面量
            List<String> lines = Files.readAllLines(file);
            java.util.regex.Pattern nullPattern = java.util.regex.Pattern.compile("\\bnull\\b");
            for (int i = 0; i < lines.size(); i++) {
                if (nullPattern.matcher(lines.get(i)).find()) {
                    entries.add(new IndexEntry("null", TYPE_NULL_LITERAL, filePath, i + 1));
                }
            }
            
        } catch (Exception e) {
            LOGGER.warning("Failed to parse " + filePath + " with JavaParser: " + e.getMessage());
        }
        
        return entries;
    }
    
    
    private void deleteFileIndex(String filePath) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM code_index WHERE file_path = ?")) {
            ps.setString(1, filePath);
            ps.execute();
        }
    }
    
    public List<IndexResult> findByClass(String className) throws SQLException {
        List<IndexResult> results = new ArrayList<>();
        
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
     * M-P1-2 fix: Escape SQL LIKE pattern special characters
     */
    private String escapeLikePattern(String pattern) {
        if (pattern == null) return "";
        return pattern.replace("\\", "\\\\");
    }

    public List<IndexResult> findCallers(String methodName) throws SQLException {
        List<IndexResult> results = new ArrayList<>();
        
        String sql = "SELECT term, term_type, file_path, line_number FROM code_index " +
                    "WHERE term LIKE ? AND term_type = ? ORDER BY file_path, line_number";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String escapedMethodName = escapeLikePattern(methodName);
            ps.setString(1, "%." + escapedMethodName);
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
    
    public List<IndexResult> findByTermPrefix(String prefix, String type) throws SQLException {
        List<IndexResult> results = new ArrayList<>();
        
        String sql = "SELECT term, term_type, file_path, line_number FROM code_index " +
                    "WHERE term LIKE ? AND term_type = ? ORDER BY term, file_path";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String escapedPrefix = escapeLikePattern(prefix);
            ps.setString(1, escapedPrefix + "%");
            ps.setString(2, type);
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
