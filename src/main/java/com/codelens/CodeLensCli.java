package com.codelens;

import com.codelens.common.utils.ColorUtil;
import com.codelens.common.utils.MethodFilter;
import com.codelens.CallIndex;
import com.codelens.CallerFinder;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CodeLens CLI 入口
 * 
 * 支持的命令:
 * - analyze <Java文件路径> [API_KEY] [--api-url=URL] [--model=MODEL] [--temperature=TEMP]
 * - index <目录路径>                    : 建立代码索引
 * - callers <类名>                      : 查询反向依赖
 * - full <Java文件路径> [API_KEY] [--api-url=URL] [--model=MODEL] [--temperature=TEMP]
 * 
 * 环境变量:
 * - CODELENS_API_KEY: API Key
 * - CODELENS_API_URL: API 地址（默认 https://api.deepseek.com/v1/chat/completions）
 * - CODELENS_MODEL: 模型名（默认 deepseek-v4-flash）
 * - CODELENS_TEMPERATURE: 温度参数（默认 0.1）
 */
public class CodeLensCli {
    
    private static final Logger LOGGER = Logger.getLogger(CodeLensCli.class.getName());
    private static final Gson gson = new Gson();
    
    /**
     * 在当前目录及其子目录中查找 .codelens 目录
     * 返回 .codelens 目录所在的父目录
     */
    private static Path findCodelensDirInSubtree(Path startPath) {
        try {
            return Files.walk(startPath, 10)
                .filter(p -> p.getFileName() != null && p.getFileName().toString().equals(".codelens"))
                .filter(Files::isDirectory)
                .map(Path::getParent)
                .findFirst()
                .orElse(null);
        } catch (Exception e) {
            LOGGER.warning("搜索 .codelens 目录失败: " + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        // 检测 --no-color 和 --no-validate 参数
        boolean noValidate = false;
        boolean noCache = false;
        boolean rawJson = false;
        List<String> filteredArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("--no-color")) {
                ColorUtil.setColorEnabled(false);
            } else if (arg.equals("--no-validate")) {
                noValidate = true;
            } else if (arg.equals("--no-cache")) {
                noCache = true;
            } else if (arg.equals("--json")) {
                rawJson = true;
            } else {
                filteredArgs.add(arg);
            }
        }
        args = filteredArgs.toArray(new String[0]);
        
        if (args.length < 1) {
            printUsage();
            return;
        }

        String command = args[0];
        
        switch (command) {
            case "analyze":
                handleAnalyze(args, noValidate, noCache, rawJson);
                break;
            case "index":
                handleIndex(args);
                break;
            case "callers":
                handleCallers(args);
                break;
            case "full":
                handleFull(args, noValidate, noCache, rawJson);
                break;
            case "--help":
            case "-h":
                printUsage();
                break;
            default:
                handleAnalyze(args, noValidate, noCache, rawJson);
                break;
        }
    }
    
    /**
     * 解析命令行参数中的选项
     */
    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--api-url=")) {
                options.put("apiUrl", arg.substring("--api-url=".length()));
            } else if (arg.startsWith("--model=")) {
                options.put("model", arg.substring("--model=".length()));
            } else if (arg.startsWith("--temperature=")) {
                options.put("temperature", arg.substring("--temperature=".length()));
            }
        }
        return options;
    }
    
    /**
     * 获取 API 配置，优先级：命令行参数 > 环境变量 > 默认值
     */
    private static String getApiUrl(Map<String, String> options) {
        if (options.containsKey("apiUrl")) {
            return options.get("apiUrl");
        }
        String env = System.getenv("CODELENS_API_URL");
        return (env != null && !env.isEmpty()) ? env : null;
    }
    
    private static String getModel(Map<String, String> options) {
        if (options.containsKey("model")) {
            return options.get("model");
        }
        String env = System.getenv("CODELENS_MODEL");
        return (env != null && !env.isEmpty()) ? env : null;
    }
    
    private static double getTemperature(Map<String, String> options) {
        if (options.containsKey("temperature")) {
            try {
                return Double.parseDouble(options.get("temperature"));
            } catch (NumberFormatException e) {
                // 忽略非法值，使用后续逻辑
            }
        }
        String env = System.getenv("CODELENS_TEMPERATURE");
        if (env != null && !env.isEmpty()) {
            try {
                return Double.parseDouble(env);
            } catch (NumberFormatException e) {
                // 忽略非法值
            }
        }
        return Double.NaN; // 表示使用默认值
    }
    
    private static void printUsage() {
        System.out.println(ColorUtil.heading("CodeLens - Java 代码分析工具"));
        System.out.println("");
        System.out.println("用法:");
        System.out.println("  java -jar codelens.jar analyze <Java文件路径> [API_KEY] [--api-url=URL] [--model=MODEL] [--temperature=TEMP]");
        System.out.println("                              - 分析 Java 文件（使用 LLM）");
        System.out.println("  java -jar codelens.jar index <目录路径> [--force]");
        System.out.println("                              - 建立代码索引（--force 强制重建）");
        System.out.println("  java -jar codelens.jar callers <类名> [--dir=<项目目录>]");
        System.out.println("                              - 查询反向依赖关系（--dir 指定项目目录）");
        System.out.println("  java -jar codelens.jar full <Java文件路径> [API_KEY] [--api-url=URL] [--model=MODEL] [--temperature=TEMP]");
        System.out.println("                              - 一键完成 index + callers + analyze");
        System.out.println("  java -jar codelens.jar --help");
        System.out.println("                              - 显示帮助信息");
        System.out.println("  --no-color                   禁用颜色输出");
        System.out.println("  --no-validate                跳过L1+L2证据校验");
        System.out.println("  --no-cache                   禁用LLM摘要缓存，强制重新分析");
        System.out.println("  --json                       输出原始JSON格式");
        System.out.println("");
        System.out.println("选项:");
        System.out.println("  --api-url=URL                API 地址（默认 https://api.deepseek.com/v1/chat/completions）");
        System.out.println("  --model=MODEL                模型名（默认 deepseek-v4-flash）");
        System.out.println("  --temperature=TEMP           温度参数（默认 0.1）");
        System.out.println("");
        System.out.println("环境变量:");
        System.out.println("  CODELENS_API_KEY     - API Key（优先级低于命令行参数）");
        System.out.println("  CODELENS_API_URL     - API 地址（默认 https://api.deepseek.com/v1/chat/completions）");
        System.out.println("  CODELENS_MODEL       - 模型名（默认 deepseek-v4-flash）");
        System.out.println("  CODELENS_TEMPERATURE - 温度参数（默认 0.1）");
        System.out.println("");
        System.out.println("优先级: 命令行选项 > 环境变量 > 默认值");
        System.out.println("");
        System.out.println("示例:");
        System.out.println("  java -jar codelens.jar analyze src/main/java/MyService.java");
        System.out.println("  CODELENS_API_KEY=xxx java -jar codelens.jar analyze src/main/java/MyService.java");
        System.out.println("  java -jar codelens.jar analyze src/main/java/MyService.java YOUR_API_KEY");
        System.out.println("  java -jar codelens.jar analyze src/main/java/MyService.java --model=gpt-4");
        System.out.println("  java -jar codelens.jar analyze src/main/java/MyService.java --api-url=https://api.openai.com/v1/chat/completions --model=gpt-4 --temperature=0.7");
        System.out.println("  CODELENS_API_KEY=xxx java -jar codelens.jar full src/main/java/MyService.java");
        System.out.println("  java -jar codelens.jar analyze src/main/java/MyService.java --json  # 输出原始JSON");
    }
    
    private static void handleAnalyze(String[] args, boolean noValidate, boolean noCache, boolean rawJson) {
        if (args.length < 2) {
            System.out.println("⚠️ 请提供 Java 文件路径");
            System.out.println("用法: java -jar codelens.jar analyze <Java文件路径> [API_KEY]");
            return;
        }
        
        String filePath = args[1];
        File sourceFile = new File(filePath);
        
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            System.out.println("⚠️ 文件不存在: " + filePath);
            return;
        }
        
        try {
            // 获取 API 配置
            String apiKey = args.length > 2 ? args[2] : System.getenv("CODELENS_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                System.out.println("⚠️ 请设置 CODELENS_API_KEY 环境变量或提供 API_KEY 参数");
                return;
            }
            
            Map<String, String> options = parseOptions(args);
            String apiUrl = getApiUrl(options);
            String model = getModel(options);
            double temperature = getTemperature(options);
            
            // 读取源代码
            String sourceCode = Files.readString(sourceFile.toPath());
            
            // 解析文件结构概览
            List<JavaParserService.ClassInfo> classInfos = JavaParserService.parseFile(sourceFile);
            String packageName = JavaParserService.getPackageName(sourceFile);
            if (!classInfos.isEmpty()) {
                printStructOverview(packageName, classInfos);
            }
            
            System.out.println(ColorUtil.heading("━━━ CodeLens 分析中 ━━━"));
            System.out.println("文件: " + filePath);
            printLlmConfig(apiUrl, model, temperature);
            
            // 调用分析服务
            String jsonResult = AnalysisService.analyzeFile(
                sourceCode,
                sourceFile.toPath(),
                new ArrayList<>(), // analyze 命令不需要 callers
                apiKey,
                apiUrl,
                model,
                temperature,
                noValidate,
                noCache,
                true // enable validation
            );
            
            if (jsonResult != null && !jsonResult.isEmpty() && !"{}".equals(jsonResult)) {
                if (rawJson) {
                    System.out.println(jsonResult);
                } else {
                    formatAnalysisResult(jsonResult);
                }
            }
            
        } catch (Exception e) {
            System.out.println("⚠️ JavaParser 解析或 LLM 分析失败: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "分析失败", e);
        }
    }
    
    private static void handleIndex(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("⚠️ 请提供目录路径");
            System.out.println("用法: java -jar codelens.jar index <目录路径>");
            return;
        }
        
        String dirPath = args[1];
        File dir = new File(dirPath);
        
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("⚠️ 目录不存在: " + dirPath);
            return;
        }
        
        System.out.println(ColorUtil.heading("━━━ 建立代码索引 ━━━"));
        System.out.println("目录: " + dirPath);
        
        Path projectRoot = JavaParserService.findProjectRoot(dir.toPath());
        if (projectRoot == null) {
            // 如果没有 .codelens 目录，使用当前目录作为项目根
            projectRoot = dir.toPath().toAbsolutePath().normalize();
            System.out.println("⚠️ 未找到 .codelens 目录，使用传入目录作为项目根: " + projectRoot);
        }
        CallIndex indexer = new CallIndex(projectRoot);
        try {
            int count = indexer.indexDirectory(dir.toPath(), false);
            System.out.println("\n✅ 索引建立完成 (索引了 " + count + " 个Java文件)");
            System.out.println("索引位置: " + projectRoot + "/.codelens/code_index.db");
        } finally {
            indexer.close();
        }
    }
    
    private static void handleCallers(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("⚠️ 请提供类名");
            System.out.println("用法: java -jar codelens.jar callers <类名> [--dir=<项目目录>]");
            return;
        }
        
        String className = args[1];
        String dirPath = null;
        for (String arg : args) {
            if (arg.startsWith("--dir=")) {
                dirPath = arg.substring("--dir=".length());
            }
        }
        
        // 查找项目根目录
        Path startPath;
        if (dirPath != null && !dirPath.isEmpty()) {
            startPath = Paths.get(dirPath).toAbsolutePath().normalize();
        } else {
            startPath = Paths.get("").toAbsolutePath();
        }
        Path projectRoot = JavaParserService.findProjectRoot(startPath);
        
        // 如果没找到，尝试在子目录中搜索 .codelens
        if (projectRoot == null) {
            projectRoot = findCodelensDirInSubtree(startPath);
        }
        
        if (projectRoot == null) {
            System.out.println("⚠️ 未找到 .codelens 索引目录");
            System.out.println("请先使用 index 命令建立索引，或使用 --dir 指定项目目录");
            return;
        }
        
        System.out.println(ColorUtil.heading("━━━ 查询反向依赖 ━━━"));
        System.out.println("查找: " + className);
        System.out.println("项目根: " + projectRoot);
        
        CallIndex indexer = new CallIndex(projectRoot);
        CallerFinder searcher = new CallerFinder(indexer, projectRoot);
        List<CallerFinder.CallerInfo> callers = searcher.findCallers(className);
        
        if (callers.isEmpty()) {
            System.out.println("未找到调用该类的代码");
        } else {
            System.out.println("找到 " + callers.size() + " 处调用:");
            for (CallerFinder.CallerInfo caller : callers) {
                System.out.println("  " + caller.filePath + ":" + caller.lineNumber);
                System.out.println("    " + caller.description);
            }
        }
    }
    
    private static void handleFull(String[] args, boolean noValidate, boolean noCache, boolean rawJson) {
        if (args.length < 2) {
            System.out.println("⚠️ 请提供 Java 文件路径");
            System.out.println("用法: java -jar codelens.jar full <Java文件路径> [API_KEY]");
            return;
        }
        
        String filePath = args[1];
        File sourceFile = new File(filePath);
        
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            System.out.println("⚠️ 文件不存在: " + filePath);
            return;
        }
        
        System.out.println(ColorUtil.heading("━━━ full 命令执行中 ━━━"));
        
        try {
            // 获取 API 配置
            String apiKey = args.length > 2 ? args[2] : System.getenv("CODELENS_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                System.out.println("⚠️ 请设置 CODELENS_API_KEY 环境变量或提供 API_KEY 参数");
                return;
            }
            
            Map<String, String> options = parseOptions(args);
            String apiUrl = getApiUrl(options);
            String model = getModel(options);
            double temperature = getTemperature(options);
            
            // 查找项目根目录
            Path projectRoot = JavaParserService.findProjectRootForFull(sourceFile.toPath());
            if (projectRoot == null) {
                projectRoot = sourceFile.toPath().toAbsolutePath().getParent();
            }
            
            System.out.println("文件: " + filePath);
            System.out.println("项目根: " + projectRoot);
            
            // 1. 建立索引
            CallIndex indexer = new CallIndex(projectRoot);
            Path srcRoot = JavaParserService.findSrcRoot(sourceFile.toPath());
            if (srcRoot != null && srcRoot.toFile().exists()) {
                System.out.println("\n" + ColorUtil.heading("━━━ Step 1: 建立索引 ━━━"));
                int indexCount = indexer.indexDirectory(srcRoot, true);  // full command always forces reindex
                System.out.println("✅ 已索引 " + indexCount + " 个Java文件");
            }
            
            // 2. 提取类名
            String className = JavaParserService.extractClassName(sourceFile);
            System.out.println("\n" + ColorUtil.heading("━━━ Step 2: 查找调用者 ━━━"));
            System.out.println("查找: " + className);
            
            // 3. 查找调用者
            List<CallerFinder.CallerInfo> callers = new ArrayList<>();
            if (projectRoot.toFile().exists()) {
                CallerFinder searcher = new CallerFinder(indexer, projectRoot);
                callers = searcher.findCallers(className);
                if (callers.isEmpty()) {
                    System.out.println("未找到调用该类的代码");
                } else {
                    System.out.println("找到 " + callers.size() + " 处调用:");
                    for (CallerFinder.CallerInfo caller : callers) {
                        System.out.println("  " + caller.filePath + ":" + caller.lineNumber);
                    }
                }
            }
            
            // 4. 解析结构 + 读取源代码
            List<JavaParserService.ClassInfo> classInfos = JavaParserService.parseFile(sourceFile);
            String packageName = JavaParserService.getPackageName(sourceFile);
            if (!classInfos.isEmpty()) {
                System.out.println();
                printStructOverview(packageName, classInfos);
            }
            
            System.out.println("\n" + ColorUtil.heading("━━━ Step 3: LLM 分析 ━━━"));
            String sourceCode = Files.readString(sourceFile.toPath());
            
            // 5. 调用分析服务
            String jsonResult = AnalysisService.analyzeFile(
                sourceCode,
                sourceFile.toPath(),
                callers,
                apiKey,
                apiUrl,
                model,
                temperature,
                noValidate,
                noCache,
                true // enable validation
            );
            
            if (jsonResult != null && !jsonResult.isEmpty() && !"{}".equals(jsonResult)) {
                if (rawJson) {
                    System.out.println(jsonResult);
                } else {
                    formatAnalysisResult(jsonResult);
                }
            }
            
            indexer.close();
        } catch (Exception e) {
            System.out.println("⚠️ JavaParser 解析或 LLM 分析失败: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "分析失败", e);
        }
        
        System.out.println("\n" + ColorUtil.heading("━━━ full 命令执行完成 ━━━"));
    }


    /**
     * 打印结构概览：包名 + 字段 + 方法 + 业务调用，区分业务和框架
     */
    private static void printStructOverview(String packageName, List<JavaParserService.ClassInfo> classInfos) {
        System.out.println("包名: " + ColorUtil.info(packageName));
        
        for (JavaParserService.ClassInfo ci : classInfos) {
            System.out.println("\n" + ColorUtil.heading((ci.isInterface ? "接口" : "类") + ": " + ci.name));
            
            // 字段
            if (!ci.fields.isEmpty()) {
                System.out.println("  字段:");
                for (JavaParserService.FieldInfo f : ci.fields) {
                    System.out.println("    L" + f.line + " | " + f.name + ": " + ColorUtil.framework(f.type));
                }
            }
            
            // 方法（区分业务/框架）
            int businessCount = 0;
            int trivialCount = 0;
            if (!ci.methods.isEmpty()) {
                System.out.println("  方法:");
                for (JavaParserService.MethodInfo m : ci.methods) {
                    boolean trivial = MethodFilter.isTrivialCall(m.name);
                    if (trivial) {
                        trivialCount++;
                        System.out.println("    L" + m.line + " | " + ColorUtil.framework(m.name + "(" + m.params + ")"));
                    } else {
                        businessCount++;
                        String annot = (m.annotations != null && !m.annotations.isEmpty()) ? m.annotations + " " : "";
                        System.out.println("    L" + m.line + " | " + ColorUtil.business(annot + m.name + "(" + m.params + ")"));
                    }
                }
            }
            
            // 业务调用（过滤getter/setter，区分业务/框架调用）
            if (!ci.calls.isEmpty()) {
                System.out.println("  业务调用（已过滤getter/setter/框架调用）:");
                for (JavaParserService.CallInfo c : ci.calls) {
                    String prefix = c.caller != null ? c.caller + "." : "";
                    String callDisplay = prefix + c.methodName + "()";
                    if (MethodFilter.isInfrastructureCall(c.methodName, c.caller)) {
                        System.out.println("    L" + c.line + " | " + ColorUtil.framework("→ " + callDisplay));
                    } else {
                        System.out.println("    L" + c.line + " | " + ColorUtil.business("→ " + callDisplay));
                    }
                }
            }
            
            System.out.println();
            System.out.println("  方法统计: " + ColorUtil.business(businessCount + " 业务") + " / " + ColorUtil.framework(trivialCount + " 框架") + " (共 " + ci.methods.size() + " 个)");
            System.out.println();
        }
    }
    
    /**
     * 打印 LLM 配置信息
     */
    private static void printLlmConfig(String apiUrl, String model, double temperature) {
        String url = apiUrl != null ? apiUrl : LLMClient.getDefaultApiUrl();
        String mdl = model != null ? model : LLMClient.getDefaultModel();
        double temp = Double.isNaN(temperature) ? LLMClient.getDefaultTemperature() : temperature;
        System.out.println(ColorUtil.info("API 地址: ") + url);
        System.out.println(ColorUtil.info("模型: ") + mdl);
        System.out.println(ColorUtil.info("温度: ") + temp);
        System.out.println();
    }


    /**
     * 格式化数据流展示 — 解析箭头分隔的步骤，每步单独一行带颜色
     */
    private static void formatDataFlow(String classAnalysis) {
        // Split by -> or → or arrows
        String[] steps = classAnalysis.split("\s*[-→>]+\s*");
        if (steps.length <= 1) {
            // No arrows found, just print as-is
            System.out.println("  " + classAnalysis);
            return;
        }
        
        // Split by arrows while keeping delimiters
        java.util.List<String> parts = new java.util.ArrayList<>();
        String remaining = classAnalysis;
        java.util.regex.Pattern arrowPattern = java.util.regex.Pattern.compile("\s*(?:->|→|->)\s*");
        java.util.regex.Matcher matcher = arrowPattern.matcher(classAnalysis);
        int lastEnd = 0;
        while (matcher.find()) {
            String step = classAnalysis.substring(lastEnd, matcher.start()).trim();
            if (!step.isEmpty()) parts.add(step);
            lastEnd = matcher.end();
        }
        String lastStep = classAnalysis.substring(lastEnd).trim();
        if (!lastStep.isEmpty()) parts.add(lastStep);
        
        // Handle semicolon-separated branches
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            // Check if this part contains branches (semicolon separated)
            if (part.contains(";") && part.contains(":")) {
                String[] branches = part.split("\s*;\s*");
                for (String branch : branches) {
                    branch = branch.trim();
                    if (branch.contains(":")) {
                        String[] bv = branch.split(":", 2);
                        System.out.println("  " + ColorUtil.info("├ " + bv[0].trim() + ":") + " " + bv[1].trim());
                    } else {
                        System.out.println("  " + ColorUtil.info("├") + " " + branch);
                    }
                }
            } else {
                // Determine if it's input/output or intermediate step
                if (i == 0) {
                    System.out.println("  " + ColorUtil.certain("⬇ 输入: ") + part);
                } else if (i == parts.size() - 1) {
                    System.out.println("  " + ColorUtil.business("⬆ 输出: ") + part);
                } else {
                    System.out.println("  " + ColorUtil.info("→ ") + part);
                }
            }
        }
        System.out.println();
    }
    /**
     * 格式化分析结果为可读的分section输出
     * 
     * @param json JSON字符串
     */
    private static void formatAnalysisResult(String json) {
        try {
            // 清理JSON字符串，移除可能的markdown代码块标记
            json = json.trim();
            if (json.startsWith("```json")) {
                json = json.substring(7);
            } else if (json.startsWith("```")) {
                json = json.substring(3);
            }
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3);
            }
            json = json.trim();
            
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root == null) {
                System.out.println(json);
                return;
            }
            
            System.out.println();
            
            // 概要 & 设计意图
            if (root.has("summary") && !root.get("summary").isJsonNull()) {
                String summary = root.get("summary").getAsString();
                if (!summary.isEmpty()) {
                    System.out.println(ColorUtil.info("📋 ") + "概要: " + summary);
                }
            }
            
            if (root.has("design_intent") && !root.get("design_intent").isJsonNull()) {
                String designIntent = root.get("design_intent").getAsString();
                if (!designIntent.isEmpty()) {
                    System.out.println(ColorUtil.info("🎯 ") + "设计意图: " + designIntent);
                }
            }
            
            if (root.has("class_analysis") && !root.get("class_analysis").isJsonNull()) {
                String classAnalysis = root.get("class_analysis").getAsString();
                if (!classAnalysis.isEmpty()) {
                    System.out.println(ColorUtil.info("📊 ") + "数据流: " + classAnalysis);
                }
            }
            
            // 依赖关系
            if (root.has("dependencies") && root.get("dependencies").isJsonArray()) {
                JsonArray deps = root.getAsJsonArray("dependencies");
                if (deps.size() > 0) {
                    System.out.println();
                    System.out.println(ColorUtil.heading("━━━ 依赖关系 ━━━"));
                    for (JsonElement elem : deps) {
                        JsonObject dep = elem.getAsJsonObject();
                        String name = getStringField(dep, "name", "?");
                        String type = getStringField(dep, "type", "");
                        String line = getStringField(dep, "line", "");
                        String desc = getStringField(dep, "description", "");
                        
                        String lineInfo = line.isEmpty() ? "" : ", L" + line;
                        System.out.println("  • " + name + " (" + type + lineInfo + ") — " + desc);
                    }
                }
            }
            
            // 风险项
            if (root.has("risks") && root.get("risks").isJsonArray()) {
                JsonArray risks = root.getAsJsonArray("risks");
                if (risks.size() > 0) {
                    System.out.println();
                    System.out.println(ColorUtil.heading("━━━ 风险项 ━━━"));
                    for (JsonElement elem : risks) {
                        JsonObject risk = elem.getAsJsonObject();
                        String severity = getStringField(risk, "severity", "LOW");
                        String desc = getStringField(risk, "description", "");
                        String line = getStringField(risk, "line", "");
                        
                        String severityTag = formatSeverity(severity);
                        String lineInfo = line.isEmpty() ? "" : "(行" + line + ")";
                        System.out.println("  " + ColorUtil.warning("⚠️ ") + severityTag + " " + lineInfo + " " + desc);
                        
                        if (risk.has("suggestion") && !risk.get("suggestion").isJsonNull()) {
                            String suggestion = risk.get("suggestion").getAsString();
                            if (!suggestion.isEmpty()) {
                                System.out.println("    💡 建议: " + suggestion);
                            }
                        }
                    }
                }
            }
            
            // 关键方法
            if (root.has("keyMethods") && root.get("keyMethods").isJsonArray()) {
                JsonArray methods = root.getAsJsonArray("keyMethods");
                if (methods.size() > 0) {
                    System.out.println();
                    System.out.println(ColorUtil.heading("━━━ 关键方法 ━━━"));
                    for (JsonElement elem : methods) {
                        JsonObject method = elem.getAsJsonObject();
                        String name = getStringField(method, "name", "?");
                        String line = getStringField(method, "line", "");
                        String complexity = getStringField(method, "complexity", "LOW");
                        String visibility = getStringField(method, "visibility", "");
                        String desc = getStringField(method, "description", "");
                        
                        String complexityTag = formatComplexity(complexity);
                        String lineInfo = line.isEmpty() ? "" : "L" + line;
                        String visInfo = visibility.isEmpty() ? "" : visibility + " ";
                        System.out.println("  • " + name + " (" + lineInfo + ", " + complexityTag + ") — " + visInfo + desc);
                    }
                }
            }
            
            // 框架集成
            if (root.has("framework_integration") && !root.get("framework_integration").isJsonNull()) {
                String framework = root.get("framework_integration").getAsString();
                if (!framework.isEmpty()) {
                    System.out.println();
                    System.out.println(ColorUtil.heading("━━━ 框架集成 ━━━"));
                    // 框架集成内容可能较长，分行显示
                    String[] lines = framework.split("\n");
                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            System.out.println("  " + line.trim());
                        }
                    }
                }
            }
            
            // 架构问题
            if (root.has("architecture_issues") && root.get("architecture_issues").isJsonArray()) {
                JsonArray issues = root.getAsJsonArray("architecture_issues");
                if (issues.size() > 0) {
                    System.out.println();
                    System.out.println(ColorUtil.heading("━━━ 架构问题 ━━━"));
                    for (JsonElement elem : issues) {
                        JsonObject issue = elem.getAsJsonObject();
                        String category = getStringField(issue, "category", "");
                        String issueText = getStringField(issue, "issue", "");
                        String impact = getStringField(issue, "impact", "");
                        String suggestion = getStringField(issue, "suggestion", "");
                        
                        if (!category.isEmpty()) {
                            System.out.println("  " + ColorUtil.warning("⚠️ ") + "[" + category + "] " + issueText);
                        } else {
                            System.out.println("  " + ColorUtil.warning("⚠️ ") + issueText);
                        }
                        
                        if (!impact.isEmpty()) {
                            System.out.println("    📌 影响: " + impact);
                        }
                        if (!suggestion.isEmpty()) {
                            System.out.println("    💡 建议: " + suggestion);
                        }
                    }
                }
            }
            
            System.out.println();
            
        } catch (Exception e) {
            // 解析失败时输出原始JSON
            LOGGER.log(Level.WARNING, "格式化分析结果失败，输出原始JSON", e);
            System.out.println(json);
        }
    }
    
    /**
     * 获取JSON对象中的字符串字段
     */
    private static String getStringField(JsonObject obj, String field, String defaultValue) {
        if (obj.has(field) && !obj.get(field).isJsonNull()) {
            return obj.get(field).getAsString();
        }
        return defaultValue;
    }
    
    /**
     * 格式化风险等级
     */
    private static String formatSeverity(String severity) {
        switch (severity.toUpperCase()) {
            case "HIGH": return ColorUtil.high("[" + severity + "]");
            case "MEDIUM": return ColorUtil.medium("[" + severity + "]");
            case "LOW": return ColorUtil.low("[" + severity + "]");
            default: return ColorUtil.info("[" + severity + "]");
        }
    }
    
    /**
     * 格式化复杂度
     */
    private static String formatComplexity(String complexity) {
        switch (complexity.toUpperCase()) {
            case "HIGH": return ColorUtil.high(complexity);
            case "MEDIUM": return ColorUtil.medium(complexity);
            case "LOW": return ColorUtil.low(complexity);
            default: return complexity;
        }
    }
}
