package com.codelens;

import com.codelens.common.utils.ColorUtil;
import com.codelens.CallIndex;

import com.codelens.CallerFinder;

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
        List<String> filteredArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("--no-color")) {
                ColorUtil.setColorEnabled(false);
            } else if (arg.equals("--no-validate")) {
                noValidate = true;
            } else if (arg.equals("--no-cache")) {
                noCache = true;
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
                handleAnalyze(args, noValidate, noCache);
                break;
            case "index":
                handleIndex(args);
                break;
            case "callers":
                handleCallers(args);
                break;
            case "full":
                handleFull(args, noValidate, noCache);
                break;
            case "--help":
            case "-h":
                printUsage();
                break;
            default:
                handleAnalyze(args, noValidate, noCache);
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
        System.out.println("  java -jar codelens.jar index <目录路径>");
        System.out.println("                              - 建立代码索引");
        System.out.println("  java -jar codelens.jar callers <类名>");
        System.out.println("                              - 查询反向依赖关系");
        System.out.println("  java -jar codelens.jar full <Java文件路径> [API_KEY] [--api-url=URL] [--model=MODEL] [--temperature=TEMP]");
        System.out.println("                              - 一键完成 index + callers + analyze");
        System.out.println("  java -jar codelens.jar --help");
        System.out.println("                              - 显示帮助信息");
        System.out.println("  --no-color                   禁用颜色输出");
        System.out.println("  --no-validate                跳过L1+L2证据校验");
        System.out.println("  --no-cache                   禁用LLM摘要缓存，强制重新分析");
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
    }
    
    private static void handleAnalyze(String[] args, boolean noValidate, boolean noCache) {
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
            
            System.out.println(ColorUtil.heading("━━━ CodeLens 分析中 ━━━"));
            System.out.println("文件: " + filePath);
            System.out.println("模型: " + (model != null ? model : "deepseek-v4-flash"));
            System.out.println();
            
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
                System.out.println(ColorUtil.heading("━━━ 分析结果 ━━━"));
                System.out.println(jsonResult);
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
        try (CallIndex indexer = new CallIndex(projectRoot)) {
            indexer.indexDirectory(dir.toPath());
            System.out.println("\n✅ 索引建立完成");
        }
    }
    
    private static void handleCallers(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("⚠️ 请提供类名");
            System.out.println("用法: java -jar codelens.jar callers <类名>");
            return;
        }
        
        String className = args[1];
        
        // 查找项目根目录（支持嵌套的 .codelens 目录）
        Path startPath = Paths.get("").toAbsolutePath();
        Path projectRoot = JavaParserService.findProjectRoot(startPath);
        
        // 如果没找到，尝试在子目录中搜索 .codelens
        if (projectRoot == null) {
            projectRoot = findCodelensDirInSubtree(startPath);
        }
        
        if (projectRoot == null) {
            System.out.println("⚠️ 未找到 .codelens 索引目录");
            return;
        }
        
        System.out.println(ColorUtil.heading("━━━ 查询反向依赖 ━━━"));
        System.out.println("查找: " + className);
        System.out.println("项目根: " + projectRoot);
        
        try (CallIndex indexer = new CallIndex(projectRoot)) {
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
    
    private static void handleFull(String[] args, boolean noValidate, boolean noCache) {
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
            try (CallIndex indexer = new CallIndex(projectRoot)) {
            Path srcRoot = JavaParserService.findSrcRoot(sourceFile.toPath());
            if (srcRoot != null && srcRoot.toFile().exists()) {
                System.out.println("\n" + ColorUtil.heading("━━━ Step 1: 建立索引 ━━━"));
                indexer.indexDirectory(srcRoot);
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
            }
            
            // 4. 读取源代码
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
                System.out.println("\n" + ColorUtil.heading("━━━ 分析结果 ━━━"));
                System.out.println(jsonResult);
            }
            
            indexer.close();
        } catch (Exception e) {
            System.out.println("⚠️ JavaParser 解析或 LLM 分析失败: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "分析失败", e);
        }
        
        System.out.println("\n" + ColorUtil.heading("━━━ full 命令执行完成 ━━━"));
    }
}
