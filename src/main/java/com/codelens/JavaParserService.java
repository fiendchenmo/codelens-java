package com.codelens;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaParser 解析服务 - 解析 Java 文件并提取结构化信息
 * 
 * 职责：
 * - 使用 JavaParser 解析 Java 源代码
 * - 提取类信息（类名、包名、字段、方法、调用）
 * - 提供路径查找工具方法
 */
public class JavaParserService {

    private static final Logger LOGGER = Logger.getLogger(JavaParserService.class.getName());

    private JavaParserService() {
        // 工具类，禁止实例化
    }

    // ========== 数据类 ==========

    /**
     * 类信息
     */
    public static class ClassInfo {
        public String name;
        public boolean isInterface;
        public List<FieldInfo> fields = new ArrayList<>();
        public List<MethodInfo> methods = new ArrayList<>();
        public List<CallInfo> calls = new ArrayList<>();
    }

    /**
     * 字段信息
     */
    public static class FieldInfo {
        public String name;
        public String type;
        public int line;
    }

    /**
     * 方法信息
     */
    public static class MethodInfo {
        public String name;
        public String returnType;
        public String params;
        public String annotations;
        public String visibility;
        public int line;
    }

    /**
     * 方法调用信息
     */
    public static class CallInfo {
        public String methodName;
        public String caller;
        public int line;
    }

    // ========== 解析方法 ==========

    /**
     * 解析 Java 文件并提取类信息列表
     * 
     * @param sourceFile Java 源文件
     * @return 类信息列表
     */
    public static List<ClassInfo> parseFile(File sourceFile) {
        List<ClassInfo> classInfos = new ArrayList<>();
        try {
            CompilationUnit cu = StaticJavaParser.parse(sourceFile);
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                ClassInfo ci = new ClassInfo();
                ci.name = packageName.isEmpty() ? cls.getNameAsString() : packageName + "." + cls.getNameAsString();
                ci.isInterface = cls.isInterface();
                
                // 提取字段
                for (FieldDeclaration field : cls.getFields()) {
                    FieldInfo fi = new FieldInfo();
                    fi.name = field.getVariable(0).getNameAsString();
                    fi.type = field.getVariable(0).getTypeAsString();
                    fi.line = field.getBegin().map(p -> p.line).orElse(0);
                    ci.fields.add(fi);
                }

                // 提取方法
                for (MethodDeclaration method : cls.getMethods()) {
                    MethodInfo mi = new MethodInfo();
                    mi.name = method.getNameAsString();
                    mi.returnType = method.getTypeAsString();
                    mi.params = method.getParameters().stream()
                            .map(p -> p.getTypeAsString() + " " + p.getNameAsString())
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
                    mi.annotations = method.getAnnotations().stream()
                            .map(a -> a.getNameAsString())
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
                    mi.visibility = method.getAccessSpecifier().asString();
                    mi.line = method.getBegin().map(p -> p.line).orElse(0);
                    ci.methods.add(mi);
                }

                // 提取方法调用
                for (MethodCallExpr call : cls.findAll(MethodCallExpr.class)) {
                    CallInfo c = new CallInfo();
                    c.methodName = call.getNameAsString();
                    c.line = call.getBegin().map(p -> p.line).orElse(0);
                    
                    // 尝试获取调用者
                    if (call.getScope().isPresent()) {
                        if (call.getScope().get() instanceof NameExpr) {
                            c.caller = ((NameExpr) call.getScope().get()).getNameAsString();
                        }
                    }
                    
                    // 过滤掉简单调用
                    if (!MethodFilter.isTrivialCall(c.methodName)) {
                        ci.calls.add(c);
                    }
                }

                classInfos.add(ci);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "解析文件失败: " + sourceFile.getPath(), e);
        }
        return classInfos;
    }

    /**
     * 获取包名
     */
    public static String getPackageName(File sourceFile) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(sourceFile);
            return cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "获取包名失败: " + sourceFile.getPath(), e);
        }
        return "";
    }

    /**
     * 提取类名
     */
    public static String extractClassName(File file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");
            
            for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (!cls.getNameAsString().isEmpty()) {
                    if (packageName.isEmpty()) {
                        return cls.getNameAsString();
                    }
                    return packageName + "." + cls.getNameAsString();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to extract class name", e);
        }
        return file.getName().replace(".java", "");
    }

    /**
     * 构建结构化上下文文本（用于 LLM 分析）
     */
    public static String buildStructContext(String packageName, List<ClassInfo> classInfos) {
        StringBuilder sb = new StringBuilder();
        sb.append("包名: ").append(packageName).append("\n");
        for (ClassInfo ci : classInfos) {
            sb.append(ci.isInterface ? "接口" : "类").append(": ").append(ci.name).append("\n");
            for (FieldInfo f : ci.fields) {
                if (f.type.equals("Logger") || f.type.equals("Log")) continue;
                sb.append("  字段 L").append(f.line).append(" | ").append(f.name).append(": ").append(f.type);
                // 标记依赖注入字段
                if (f.type.contains("Mapper") || f.type.contains("Service") || f.type.contains("Repository")
                        || f.type.contains("Component") || f.type.contains("Scheduler") || f.type.contains("Template")
                        || f.type.contains("Client") || f.type.contains("Manager") || f.type.contains("Factory")
                        || f.type.contains("Handler") || f.type.contains("Provider") || f.type.contains("Builder")
                        || f.type.contains("Config") || f.type.contains("DataSource") || f.type.contains("Redis")
                        || f.type.contains("Cache") || f.type.contains("Queue") || f.type.contains("Pool")) {
                    sb.append(" [依赖注入]");
                }
                sb.append("\n");
            }
            for (MethodInfo m : ci.methods) {
                sb.append("  方法 L").append(m.line).append(" | ");
                if (m.visibility != null && !m.visibility.isEmpty()) {
                    sb.append(m.visibility).append(" ");
                }
                if (m.annotations != null && !m.annotations.isEmpty()) {
                    sb.append("@").append(m.annotations.replace(", ", " @")).append(" ");
                }
                sb.append(m.returnType).append(" ").append(m.name).append("(").append(m.params).append(")\n");
            }
            for (CallInfo c : ci.calls) {
                String prefix = c.caller != null ? c.caller + "." : "";
                sb.append("  调用 L").append(c.line).append(" | ").append(prefix).append(c.methodName).append("()");
                if (c.caller != null && c.caller.contains("Mapper") && MethodFilter.isTableNameParamMethod(c.methodName)) {
                    sb.append(" [注意:表名/列名参数传入Mapper，需检查SQL是否使用${}拼接]");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    // ========== 路径查找方法 ==========

    /**
     * 查找项目根目录（包含 .codelens 目录）
     */
    public static Path findProjectRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.exists(current.resolve(".codelens"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * 查找 src 根目录
     */
    public static Path findSrcRoot(Path filePath) {
        // 尝试找到 src/main/java 或 src/test/java
        Path current = filePath.getParent();
        String sep = FileSystems.getDefault().getSeparator();
        while (current != null) {
            if (current.endsWith("src" + sep + "main" + sep + "java") ||
                current.endsWith("src" + sep + "test" + sep + "java")) {
                return current;
            }
            // 继续往上找 src
            if (current.getFileName() != null && current.getFileName().toString().equals("src")) {
                return current;
            }
            current = current.getParent();
        }
        // 找不到则返回文件父目录
        return filePath.getParent();
    }

    /**
     * 查找 full 命令使用的项目根目录（优先 .git 根目录，其次 pom.xml 根目录）
     */
    public static Path findProjectRootForFull(Path start) {
        Path current = start.toAbsolutePath().normalize();
        
        // 优先查找 .git 根目录
        Path gitRoot = null;
        Path temp = current;
        while (temp != null) {
            if (Files.exists(temp.resolve(".git"))) {
                gitRoot = temp;
                break;
            }
            temp = temp.getParent();
        }
        if (gitRoot != null) {
            return gitRoot;
        }
        
        // 其次查找最顶层 pom.xml
        Path topmostPom = null;
        current = start.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))) {
                topmostPom = current;
            }
            current = current.getParent();
        }
        return topmostPom;
    }
}
