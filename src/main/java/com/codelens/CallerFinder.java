package com.codelens;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.logging.*;
import java.util.regex.*;

/**
 * 反向依赖查询 - findCallers
 * 支持接口穿透（查找接口实现类）
 */
public class CallerFinder {
    
    private static final Logger LOGGER = Logger.getLogger(CallerFinder.class.getName());
    
    private final CallIndex callIndex;
    private final Path projectRoot;
    
    // 用于接口穿透的缓存
    private Map<String, Set<String>> interfaceImplementations = new HashMap<>();
    
    public CallerFinder(CallIndex callIndex, Path projectRoot) {
        this.callIndex = callIndex;
        this.projectRoot = projectRoot;
    }
    
    /**
     * 查找依赖指定类的所有调用方
     */
    public List<CallerInfo> findCallers(String className) throws SQLException {
        List<CallerInfo> results = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        // 1. 查找 import 了该类的文件
        findImportCallers(className, results, visited);
        
        // 2. 查找方法调用了该类方法的文件
        findMethodCallers(className, results, visited);
        
        return results;
    }
    
    /**
     * 查找 import 了指定类的文件
     */
    private void findImportCallers(String className, List<CallerInfo> results, Set<String> visited) 
            throws SQLException {
        
        List<CallIndex.IndexResult> imports = callIndex.findByClass(className);
        
        for (CallIndex.IndexResult ir : imports) {
            if (!visited.contains(ir.filePath)) {
                visited.add(ir.filePath);
                CallerInfo info = new CallerInfo();
                info.filePath = ir.filePath;
                info.type = CallerType.IMPORT;
                info.description = "import " + className;
                info.lineNumber = ir.lineNumber;
                results.add(info);
            }
        }
    }
    
    /**
     * 查找方法调用了指定类方法的文件
     */
    private void findMethodCallers(String className, List<CallerInfo> results, Set<String> visited) 
            throws SQLException {
        
        // 查找所有类方法
        List<CallIndex.IndexResult> methods = callIndex.findByType(CallIndex.TYPE_METHOD);
        
        for (CallIndex.IndexResult method : methods) {
            if (method.term.equals(className)) {
                // 找到了同名的方法声明
                continue;
            }
            
            // 查找调用该方法的地方
            List<CallIndex.IndexResult> callers = callIndex.findCallers(method.term);
            for (CallIndex.IndexResult caller : callers) {
                if (!visited.contains(caller.filePath)) {
                    visited.add(caller.filePath);
                    CallerInfo info = new CallerInfo();
                    info.filePath = caller.filePath;
                    info.type = CallerType.METHOD_CALL;
                    info.description = "calls " + method.term + "()";
                    info.lineNumber = caller.lineNumber;
                    results.add(info);
                }
            }
        }
    }
    
    /**
     * 查找接口的所有实现类（接口穿透）
     */
    public Set<String> findInterfaceImplementations(String interfaceName) throws IOException, SQLException {
        if (interfaceImplementations.containsKey(interfaceName)) {
            return interfaceImplementations.get(interfaceName);
        }
        
        Set<String> implementations = new HashSet<>();
        
        // 1. 查找所有类定义
        List<CallIndex.IndexResult> classes = callIndex.findAllClasses();
        
        for (CallIndex.IndexResult cls : classes) {
            if (implementsInterface(cls.filePath, cls.term, interfaceName)) {
                implementations.add(cls.term);
            }
        }
        
        // 2. 查找标注了 @Service/@Component/@Repository 的类
        List<CallIndex.IndexResult> services = callIndex.findByType(CallIndex.TYPE_ANNOTATION);
        for (CallIndex.IndexResult ann : services) {
            if (ann.term.equals("Service") || ann.term.equals("Component") || 
                ann.term.equals("Repository")) {
                // 尝试从文件名推断类名
                Path file = Paths.get(ann.filePath);
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".java")) {
                    String className = fileName.replace(".java", "");
                    implementations.add(className);
                }
            }
        }
        
        interfaceImplementations.put(interfaceName, implementations);
        return implementations;
    }
    
    /**
     * 检查类是否实现了指定接口
     */
    private boolean implementsInterface(String filePath, String className, String interfaceName) 
            throws IOException {
        
        // 使用正则快速检查
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            String content = String.join("\n", lines);
            
            // 查找 implements 语句
            Pattern implPattern = Pattern.compile(
                "class\\s+" + className + "\\s+implements\\s+([^\\{]+)");
            Matcher m = implPattern.matcher(content);
            
            while (m.find()) {
                String interfaces = m.group(1);
                if (interfaces.contains(interfaceName)) {
                    return true;
                }
            }
            
            // 查找 extends 语句（接口继承）
            Pattern extPattern = Pattern.compile(
                "interface\\s+" + className + "\\s+extends\\s+([^\\{]+)");
            Matcher m2 = extPattern.matcher(content);
            
            while (m2.find()) {
                String interfaces = m2.group(1);
                if (interfaces.contains(interfaceName)) {
                    return true;
                }
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to read file: " + filePath);
        }
        
        return false;
    }
    
    /**
     * 完整的反向依赖分析（包含接口穿透）
     */
    public List<CallerInfo> findCallersWithInterfacePenetration(String className) 
            throws SQLException, IOException {
        
        List<CallerInfo> results = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        // 1. 首先查找直接的依赖
        findCallers(className);
        List<CallerInfo> directCallers = findCallers(className);
        for (CallerInfo info : directCallers) {
            if (!visited.contains(info.filePath)) {
                visited.add(info.filePath);
                results.add(info);
            }
        }
        
        // 2. 如果是接口，查找其实现类并递归
        if (isInterface(className)) {
            Set<String> impls = findInterfaceImplementations(className);
            for (String impl : impls) {
                if (!impl.equals(className) && !visited.contains(impl)) {
                    List<CallerInfo> implCallers = findCallers(impl);
                    for (CallerInfo info : implCallers) {
                        if (!visited.contains(info.filePath)) {
                            visited.add(info.filePath);
                            info.description = info.description + " (via interface " + className + ")";
                            results.add(info);
                        }
                    }
                }
            }
        }
        
        return results;
    }
    
    /**
     * 检查是否为接口
     */
    private boolean isInterface(String className) throws SQLException {
        List<CallIndex.IndexResult> classes = callIndex.findAllClasses();
        for (CallIndex.IndexResult cls : classes) {
            if (cls.term.equals(className)) {
                try {
                    List<String> lines = Files.readAllLines(Paths.get(cls.filePath));
                    String content = String.join("\n", lines);
                    return content.contains("interface " + className);
                } catch (IOException e) {
                    return false;
                }
            }
        }
        return false;
    }
    
    /**
     * 打印依赖分析报告
     */
    public void printReport(String className, List<CallerInfo> callers) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                  反向依赖分析报告                            ║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.println("║ 目标类: " + padRight(className, 49) + "║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        
        if (callers.isEmpty()) {
            System.out.println("║  未发现依赖方                                                 ║");
        } else {
            System.out.println("║  发现 " + callers.size() + " 个依赖方:                                         ║");
            for (CallerInfo info : callers) {
                System.out.println("║  [" + info.type + "] " + padRight(info.filePath, 40) + "║");
                System.out.println("║    L" + padLeft(String.valueOf(info.lineNumber), 4) + " | " + padRight(info.description, 39) + "║");
            }
        }
        
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }
    
    private String padRight(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return s + repeat(" ", n - s.length());
    }
    
    private String padLeft(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return repeat(" ", n - s.length()) + s;
    }
    
    private String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
    
    // 内部类：调用方信息
    public static class CallerInfo {
        public String filePath;
        public CallerType type;
        public String description;
        public int lineNumber;
    }
    
    // 调用类型枚举
    public enum CallerType {
        IMPORT,
        METHOD_CALL,
        FIELD_ACCESS,
        ANNOTATION_USAGE
    }
}
