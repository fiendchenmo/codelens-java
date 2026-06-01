package com.codelens;

import com.codelens.common.callindex.CallRecord;
import com.codelens.common.callindex.SQLiteCallIndex;
import com.codelens.common.utils.MethodFilter;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CallIndex 构建器。
 * <p>
 * 用 JavaParser 扫描源码，构建结构化的调用索引，写入 callindex.db。
 * 两遍扫描：第一遍收集类型映射，第二遍提取方法调用。
 * </p>
 */
public class CallIndexBuilder {

    private static final Logger LOGGER = Logger.getLogger(CallIndexBuilder.class.getName());

    /** 源代码根目录（相对于 projectRoot） */
    private static final String MAIN_SRC = "src/main/java/";

    private CallIndexBuilder() {}

    // ==================== 公共方法 ====================

    /**
     * 构建调用索引到 callindex.db。
     * 如果 callindex.db 已存在且有记录，跳过构建。
     *
     * @param projectRoot 项目根目录
     * @return 构建的调用记录数；跳过时返回已有记录数；-1 表示失败
     */
    public static int buildIfEmpty(Path projectRoot) {
        if (projectRoot == null) return -1;

        Path dbDir = projectRoot.resolve(".codelens");
        Path dbPath = dbDir.resolve("callindex.db");

        // 检查是否已有记录（用原生 JDBC 避免依赖 SQLiteCallIndex 包级私有方法）
        if (Files.exists(dbPath)) {
            int existingCount = countRecords(dbPath);
            if (existingCount > 0) {
                return existingCount; // 已有记录，跳过构建
            }
        }

        return build(projectRoot);
    }

    /**
     * 用原生 JDBC 统计 callindex.db 中的记录数。
     */
    private static int countRecords(Path dbPath) {
        java.sql.Connection conn = null;
        java.sql.Statement stmt = null;
        java.sql.ResultSet rs = null;
        try {
            conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
            stmt = conn.createStatement();
            rs = stmt.executeQuery("SELECT COUNT(*) FROM call_records");
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (Exception e) {
            return -1;
        } finally {
            if (rs != null) try { rs.close(); } catch (Exception ignored) { }
            if (stmt != null) try { stmt.close(); } catch (Exception ignored) { }
            if (conn != null) try { conn.close(); } catch (Exception ignored) { }
        }
    }

    /**
     * 强制重新构建调用索引。
     *
     * @param projectRoot 项目根目录
     * @return 构建的调用记录数；-1 表示失败
     */
    public static int build(Path projectRoot) {
        if (projectRoot == null) return -1;

        // 查找源码目录
        Path mainSrc = projectRoot.resolve(MAIN_SRC).normalize();
        if (!Files.isDirectory(mainSrc)) {
            // 尝试直接扫描项目根
            mainSrc = projectRoot;
        }

        // 收集所有 .java 文件
        List<Path> javaFiles = new ArrayList<>();
        try {
            scanJavaFiles(mainSrc, javaFiles);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "扫描源码失败", e);
            return -1;
        }

        if (javaFiles.isEmpty()) {
            return 0;
        }

        // 解析所有文件
        List<FileParseResult> parsedFiles = new ArrayList<>();
        for (Path f : javaFiles) {
            try {
                FileParseResult parsed = parseFile(f, projectRoot);
                if (parsed != null) {
                    parsedFiles.add(parsed);
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "解析文件失败: " + f, e);
            }
        }

        // 第二遍：提取方法调用
        List<CallRecord> allRecords = new ArrayList<>();
        for (FileParseResult parsed : parsedFiles) {
            extractCalls(parsed, allRecords);
        }

        // 批量写入
        if (allRecords.isEmpty()) {
            return 0;
        }

        Path dbDir = projectRoot.resolve(".codelens");
        try {
            Files.createDirectories(dbDir);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "创建 .codelens 目录失败", e);
            return -1;
        }
        Path dbPath = dbDir.resolve("callindex.db");

        SQLiteCallIndex index = new SQLiteCallIndex(dbPath.toString());
        try {
            index.batchInsert(allRecords);
            return allRecords.size();
        } finally {
            index.close();
        }
    }

    // ==================== 第一遍：解析文件 ====================

    /**
     * 解析单个文件：收集 importMap, fieldMap, methodMap, CompilationUnit。
     */
    private static FileParseResult parseFile(Path filePath, Path projectRoot) throws IOException {
        File sourceFile = filePath.toFile();
        if (!sourceFile.exists() || !sourceFile.isFile()) return null;

        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(sourceFile);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "JavaParser 解析失败: " + filePath, e);
            return null;
        }

        FileParseResult result = new FileParseResult();
        result.filePath = projectRoot.relativize(filePath).toString().replace('\\', '/');
        result.cu = cu;

        // 包名
        result.packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        // 当前类全限定名
        result.currentClass = deriveClassName(result.filePath);

        // importMap: 简单名 → 全限定名
        Map<String, String> importMap = new HashMap<>();
        for (com.github.javaparser.ast.ImportDeclaration imp : cu.getImports()) {
            String importName = imp.getNameAsString();
            String simpleName;
            int lastDot = importName.lastIndexOf('.');
            if (lastDot >= 0) {
                simpleName = importName.substring(lastDot + 1);
            } else {
                simpleName = importName;
            }
            // 静态导入和 on-demand 导入不处理
            if (!imp.isStatic() && !imp.isAsterisk()) {
                importMap.put(simpleName, importName);
            }
        }
        result.importMap = importMap;

        // fieldMap: 变量名 → 类型简单名
        Map<String, String> fieldMap = new HashMap<>();
        for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
            String typeName = field.getElementType().asString();
            // 泛型处理：去掉 <...>
            int genericIdx = typeName.indexOf('<');
            if (genericIdx > 0) {
                typeName = typeName.substring(0, genericIdx);
            }
            // 数组处理
            int arrayIdx = typeName.indexOf('[');
            if (arrayIdx > 0) {
                typeName = typeName.substring(0, arrayIdx);
            }
            // 所有变量
            for (int i = 0; i < field.getVariables().size(); i++) {
                String varName = field.getVariable(i).getNameAsString();
                fieldMap.put(varName, typeName);
            }
        }
        result.fieldMap = fieldMap;

        // methodMap: 方法名 → 行号范围
        Map<String, int[]> methodMap = new HashMap<>();
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            int begin = method.getBegin().map(p -> p.line).orElse(0);
            int end = method.getRange().map(r -> r.end.line).orElse(begin);
            String methodName = method.getNameAsString();
            methodMap.put(methodName, new int[]{begin, end});
        }
        result.methodMap = methodMap;

        return result;
    }

    // ==================== 第二遍：提取调用 ====================

    /**
     * 从解析结果中提取方法调用记录。
     */
    private static void extractCalls(FileParseResult parsed, List<CallRecord> records) {
        if (parsed == null || parsed.cu == null) return;

        List<MethodCallExpr> calls;
        try {
            calls = parsed.cu.findAll(MethodCallExpr.class);
        } catch (Exception e) {
            return;
        }

        for (MethodCallExpr call : calls) {
            try {
                String methodName = call.getNameAsString();

                // 过滤 trivial 调用
                if (MethodFilter.isTrivialCall(methodName)) continue;

                // 行号
                int lineNumber = call.getBegin().map(p -> p.line).orElse(0);

                // 解析被调用类
                String calleeClass = resolveCalleeClass(call, parsed.importMap,
                        parsed.fieldMap, parsed.currentClass);

                // 如果 callee 是基础设施调用，跳过
                if (MethodFilter.isInfrastructureCall(methodName, calleeClass)) continue;

                // 确定 caller 方法
                String callerMethod = findEnclosingMethod(lineNumber, parsed.methodMap);

                // 构建 CallRecord
                CallRecord record = new CallRecord(
                        parsed.currentClass, // callerClass
                        callerMethod,        // callerMethod
                        calleeClass,         // calleeClass
                        methodName,          // calleeMethod
                        "DIRECT",            // callType
                        parsed.filePath,     // filePath
                        lineNumber,          // lineNumber
                        null                 // confidence
                );
                records.add(record);
            } catch (Exception e) {
                // 单条调用解析失败不影响整体
                LOGGER.log(Level.FINE, "解析调用失败", e);
            }
        }
    }

    // ==================== 类型解析 ====================

    /**
     * 解析方法调用的被调用类全限定名。
     * <p>
     * 策略：
     * <ul>
     *   <li>有 scope + scope 首字母大写 → 查 importMap，未命中则尝试同包</li>
     *   <li>有 scope + scope 是 this/super → 当前类</li>
     *   <li>有 scope + scope 首字母小写 → 查 fieldMap → importMap，未命中则尝试同包</li>
     *   <li>无 scope → UNKNOWN</li>
     * </ul>
     * </p>
     */
    private static String resolveCalleeClass(MethodCallExpr call,
                                              Map<String, String> importMap,
                                              Map<String, String> fieldMap,
                                              String currentClass) {
        if (!call.getScope().isPresent()) {
            return "UNKNOWN";
        }

        String scopeStr = call.getScope().get().toString();
        if (scopeStr.isEmpty()) return "UNKNOWN";

        // scope 是 "this" 或 "super"
        if ("this".equals(scopeStr) || "super".equals(scopeStr)) {
            return currentClass;
        }

        char firstChar = scopeStr.charAt(0);

        // scope 首字母大写 → 可能是类名
        if (Character.isUpperCase(firstChar)) {
            // 去掉可能的泛型参数或链式调用
            String simpleName = scopeStr.split("<")[0].split("\\.")[0];
            String resolved = importMap.get(simpleName);
            if (resolved != null) return resolved;
            // 同包回退：当前类所在包 + 类名
            return qualifyWithPackage(simpleName, currentClass);
        }

        // scope 首字母小写 → 可能是变量名，查字段映射
        String fieldType = fieldMap.get(scopeStr);
        if (fieldType != null) {
            String resolved = importMap.get(fieldType);
            if (resolved != null) return resolved;
            // 同包回退
            return qualifyWithPackage(fieldType, currentClass);
        }

        return "UNKNOWN";
    }

    /**
     * 用当前类的包名限定简单类名。
     * AgentRunner, com.example.ServiceA → com.example.AgentRunner
     */
    private static String qualifyWithPackage(String simpleName, String currentClass) {
        if (simpleName == null || simpleName.isEmpty() ||
            currentClass == null || currentClass.isEmpty()) {
            return simpleName;
        }
        int lastDot = currentClass.lastIndexOf('.');
        if (lastDot > 0) {
            return currentClass.substring(0, lastDot + 1) + simpleName;
        }
        return simpleName;
    }

    /**
     * 查找方法调用所在的 enclosing 方法。
     */
    private static String findEnclosingMethod(int lineNumber,
                                               Map<String, int[]> methodMap) {
        String bestMethod = "";
        int bestStart = 0;

        for (Map.Entry<String, int[]> entry : methodMap.entrySet()) {
            int begin = entry.getValue()[0];
            int end = entry.getValue()[1];
            if (lineNumber >= begin && lineNumber <= end) {
                // 选择最内层方法（begin 最大者）
                if (begin > bestStart) {
                    bestStart = begin;
                    bestMethod = entry.getKey();
                }
            }
        }
        return bestMethod;
    }

    // ==================== 文件扫描 ====================

    /**
     * 递归扫描目录下所有 .java 文件。
     */
    private static void scanJavaFiles(Path dir, List<Path> result) throws IOException {
        if (!Files.isDirectory(dir)) return;

        File[] files = dir.toFile().listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scanJavaFiles(f.toPath(), result);
            } else if (f.isFile() && f.getName().endsWith(".java")) {
                result.add(f.toPath());
            }
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 从文件路径推导类名（全限定名）。
     * 支持多模块路径如 codelens-cli/src/main/java/com/example/Service。
     */
    private static String deriveClassName(String filePath) {
        if (filePath == null || filePath.isEmpty()) return "";
        String normalized = filePath.replace('\\', '/');
        String withoutExt = normalized.replaceAll("\\.java$", "");

        // 搜索 src/main/java/ 或 src/test/java/ 标记（支持多模块）
        String[] markers = {"src/main/java/", "src/test/java/"};
        for (String marker : markers) {
            int idx = withoutExt.indexOf(marker);
            if (idx >= 0) {
                withoutExt = withoutExt.substring(idx + marker.length());
                return withoutExt.replace('/', '.');
            }
        }

        // Fallback: 尝试去掉 src/ 前缀
        if (withoutExt.startsWith("src/")) {
            withoutExt = withoutExt.substring("src/".length());
        }

        return withoutExt.replace('/', '.');
    }

    // ==================== 内部数据结构 ====================

    /**
     * 单文件解析结果（第一遍的输出）。
     */
    private static class FileParseResult {
        String filePath;
        String packageName;
        String currentClass;               // callerClass 全限定名
        CompilationUnit cu;
        Map<String, String> importMap;     // 简单名 → 全限定名
        Map<String, String> fieldMap;      // 变量名 → 类型简单名
        Map<String, int[]> methodMap;      // 方法名 → {beginLine, endLine}
    }
}
