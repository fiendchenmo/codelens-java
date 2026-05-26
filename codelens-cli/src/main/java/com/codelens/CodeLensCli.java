package com.codelens;

import com.codelens.ColorUtil;
import com.codelens.CallIndex;

import com.codelens.CallerFinder;
import com.codelens.common.models.SchemaVersion;
import com.codelens.common.utils.MethodFilter;
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
    
    public static void main(String[] args) throws Exception {
        // 抑制 java.util.logging 输出到终端
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }
        java.util.logging.Logger codelensLogger = java.util.logging.Logger.getLogger("com.codelens");
        codelensLogger.setUseParentHandlers(false);

        // 检测 --no-color、--no-validate、--no-cache、--json、--schema 参数
        boolean noValidate = false;
        boolean noCache = false;
        boolean rawJson = false;
        SchemaVersion schemaVersion = null; // null 表示默认 V2
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
            } else if (arg.startsWith("--schema=")) {
                String v = arg.substring("--schema=".length());
                SchemaVersion parsed = SchemaVersion.fromString(v);
                if (parsed != null) {
                    schemaVersion = parsed;
                } else {
                    System.out.println("[!] 无效的 Schema 版本: " + v + "，支持: v2, v3");
                }
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
                handleAnalyze(args, noValidate, noCache, rawJson, schemaVersion);
                break;
            case "index":
                handleIndex(args);
                break;
            case "callers":
                handleCallers(args);
                break;
            case "full":
                handleFull(args, noValidate, noCache, rawJson, schemaVersion);
                break;
            case "--help":
            case "-h":
                printUsage();
                break;
            default:
                handleAnalyze(args, noValidate, noCache, rawJson, schemaVersion);
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
            } else if (arg.startsWith("--dir=")) {
                options.put("dir", arg.substring("--dir=".length()));
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
        System.out.println("  java -jar codelens.jar callers <类名> [--dir=<目录>]");
        System.out.println("                              - 查询反向依赖关系");
        System.out.println("  java -jar codelens.jar full <Java文件路径> [API_KEY] [--api-url=URL] [--model=MODEL] [--temperature=TEMP]");
        System.out.println("                              - 一键完成 index + callers + analyze");
        System.out.println("  java -jar codelens.jar --help");
        System.out.println("                              - 显示帮助信息");
        System.out.println("  --no-color                   禁用颜色输出");
        System.out.println("  --no-validate                跳过L1+L2证据校验");
        System.out.println("  --no-cache                   禁用LLM摘要缓存，强制重新分析");
        System.out.println("  --json                       JSON 格式输出（raw JSON，不格式化）");
        System.out.println("  --schema=v3                  Schema版本（默认v2，支持: v2, v3）");
        System.out.println("");
        System.out.println("选项:");
        System.out.println("  --api-url=URL                API 地址（默认 https://api.deepseek.com/v1/chat/completions）");
        System.out.println("  --model=MODEL                模型名（默认 deepseek-v4-flash）");
        System.out.println("  --temperature=TEMP           温度参数（默认 0.1）");
        System.out.println("  --dir=<目录>                  callers 命令指定搜索目录");
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
        System.out.println("  java -jar codelens.jar callers MyService --dir=/path/to/project");
    }
    
    private static void handleAnalyze(String[] args, boolean noValidate, boolean noCache, boolean rawJson, SchemaVersion schemaVersion) {
        if (args.length < 2) {
            System.out.println("[!] 请提供 Java 文件路径");
            System.out.println("用法: java -jar codelens.jar analyze <Java文件路径> [API_KEY]");
            return;
        }

        String filePath = args[1];
        File sourceFile = new File(filePath);

        if (!sourceFile.exists() || !sourceFile.isFile()) {
            System.out.println("[!] 文件不存在: " + filePath);
            return;
        }

        try {
            // 获取 API 配置
            String apiKey = args.length > 2 ? args[2] : System.getenv("CODELENS_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                System.out.println("[!] 请设置 CODELENS_API_KEY 环境变量或提供 API_KEY 参数");
                return;
            }

            Map<String, String> options = parseOptions(args);
            String apiUrl = getApiUrl(options);
            String model = getModel(options);
            double temperature = getTemperature(options);

            // 读取源代码
            String sourceCode = new String(Files.readAllBytes(sourceFile.toPath()));

            // 解析文件结构概览
            List<JavaParserService.ClassInfo> classInfos = JavaParserService.parseFile(sourceFile);
            String packageName = JavaParserService.getPackageName(sourceFile);
            if (!classInfos.isEmpty()) {
                System.out.println(ColorUtil.heading("━━━ 结构概览: " + classInfos.get(0).name + " ━━━"));
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
                true, // enable validation
                schemaVersion
            );

            if (jsonResult != null && !jsonResult.isEmpty() && !"{}".equals(jsonResult)) {
                if (!jsonResult.startsWith("CACHED_DISPLAYED:")) {
                    System.out.println(ColorUtil.heading("━━━ 分析结果 ━━━"));
                    if (rawJson) {
                        System.out.println(jsonResult);
                    } else {
                        formatAnalysisResult(jsonResult);
                    }
                }
            }
            
        } catch (Exception e) {
            System.out.println("[!] JavaParser 解析或 LLM 分析失败: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "分析失败", e);
        }
    }
    
    private static void handleIndex(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("[!] 请提供目录路径");
            System.out.println("用法: java -jar codelens.jar index <目录路径>");
            return;
        }
        
        String dirPath = args[1];
        File dir = new File(dirPath);
        
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("[!] 目录不存在: " + dirPath);
            return;
        }
        
        System.out.println(ColorUtil.heading("━━━ 建立代码索引 ━━━"));
        System.out.println("目录: " + dirPath);

        Path projectRoot = JavaParserService.findProjectRoot(dir.toPath());
        if (projectRoot == null) {
            projectRoot = dir.toPath().toAbsolutePath().normalize();
            System.out.println("[!] 未找到 .codelens 目录，使用传入目录作为项目根: " + projectRoot);
        }

        long startTime = System.currentTimeMillis();
        try (CallIndex indexer = new CallIndex(projectRoot)) {
            int fileCount = indexer.indexDirectory(dir.toPath());
            int totalEntries = indexer.getTotalIndexedCount();
            long elapsed = System.currentTimeMillis() - startTime;

            Path dbPath = indexer.getDbPath();
            String dbSize = dbPath.toFile().exists() ? formatFileSize(dbPath.toFile().length()) : "N/A";

            System.out.println("\n[OK] 索引建立完成");
            System.out.println("  本次新增: " + fileCount + " 个文件");
            System.out.println("  数据库累计: " + totalEntries + " 条索引");
            System.out.println("  数据库: " + dbPath);
            System.out.println("  数据库大小: " + dbSize);
            System.out.println("  耗时: " + elapsed + "ms");
        }
    }
    
    private static void handleCallers(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("[!] 请提供类名");
            System.out.println("用法: java -jar codelens.jar callers <类名>");
            return;
        }
        
        String className = args[1];

        // 解析 --dir 选项
        Map<String, String> options = parseOptions(args);
        String dir = options.get("dir");
        Path startPath = dir != null ? Paths.get(dir).toAbsolutePath() : Paths.get("").toAbsolutePath();

        // 使用三级查找策略定位项目根目录
        Path projectRoot = JavaParserService.findProjectRoot(startPath);
        
        // callers 命令依赖索引，找不到 .codelens 时直接报错退出（不使用第三级兜底）
        Path codelensDir = projectRoot.resolve(".codelens");
        if (!codelensDir.toFile().exists()) {
            System.out.println("[FAIL] 未找到 .codelens 目录，请先执行 `java -jar codelens.jar index <项目路径>` 创建索引");
            return;
        }
        
        System.out.println(ColorUtil.heading("━━━ 查询反向依赖 ━━━"));
        System.out.println("查找: " + className);
        System.out.println("项目根: " + projectRoot);
        
        try (CallIndex indexer = new CallIndex(projectRoot)) {
            CallerFinder searcher = new CallerFinder(indexer, projectRoot);
            List<CallerFinder.CallerInfo> callers = searcher.findCallers(className);
            
            // 获取索引的源码目录列表
            List<String> indexedSrcRoots = indexer.getIndexedSrcRoots();
            String srcRootsStr = indexedSrcRoots.isEmpty() ? "未知" : String.join(", ", indexedSrcRoots);
            
            if (callers.isEmpty()) {
                System.out.println("未找到调用该类的代码");
            } else {
                System.out.println("找到 " + callers.size() + " 处调用:");
                for (CallerFinder.CallerInfo caller : callers) {
                    System.out.println("  " + caller.filePath + ":" + caller.lineNumber);
                    System.out.println("    " + caller.description);
                }
            }
            System.out.println("\n索引范围: " + srcRootsStr + "，未索引目录的调用关系可能缺失");
        }
    }
    
    private static void handleFull(String[] args, boolean noValidate, boolean noCache, boolean rawJson, SchemaVersion schemaVersion) {
        if (args.length < 2) {
            System.out.println("[!] 请提供 Java 文件路径");
            System.out.println("用法: java -jar codelens.jar full <Java文件路径> [API_KEY]");
            return;
        }

        String filePath = args[1];
        File sourceFile = new File(filePath);

        if (!sourceFile.exists() || !sourceFile.isFile()) {
            System.out.println("[!] 文件不存在: " + filePath);
            return;
        }

        System.out.println(ColorUtil.heading("━━━ full 命令执行中 ━━━"));

        try {
            // 获取 API 配置
            String apiKey = args.length > 2 ? args[2] : System.getenv("CODELENS_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                System.out.println("[!] 请设置 CODELENS_API_KEY 环境变量或提供 API_KEY 参数");
                return;
            }

            Map<String, String> options = parseOptions(args);
            String apiUrl = getApiUrl(options);
            String model = getModel(options);
            double temperature = getTemperature(options);

            // 使用三级查找策略定位项目根目录
            Path projectRoot = JavaParserService.findProjectRoot(sourceFile.toPath());

            System.out.println("文件: " + filePath);
            System.out.println("项目根: " + projectRoot);

            // 声明变量
            List<CallerFinder.CallerInfo> callers = new ArrayList<>();
            String sourceCode = new String(Files.readAllBytes(sourceFile.toPath()));

            // 1. 建立索引
            try (CallIndex indexer = new CallIndex(projectRoot)) {
                Path srcRoot = JavaParserService.findSrcRoot(sourceFile.toPath());
                if (srcRoot != null && srcRoot.toFile().exists()) {
                    indexer.indexDirectory(srcRoot);
                }
                // 提取类名用于 callers 查询
                String className = JavaParserService.extractClassName(sourceFile);

                // 2. 解析结构概览
                List<JavaParserService.ClassInfo> classInfos = JavaParserService.parseFile(sourceFile);
                String packageName = JavaParserService.getPackageName(sourceFile);
                if (!classInfos.isEmpty()) {
                    System.out.println(ColorUtil.heading("━━━ 结构概览: " + classInfos.get(0).name + " ━━━"));
                    printStructOverview(packageName, classInfos);
                }

                // 3. LLM 分析
                System.out.println("\n" + ColorUtil.heading("━━━ LLM 分析 ━━━"));

                // callers 暂时为空，分析完再查询
                callers = new ArrayList<>();

                String jsonResult = AnalysisService.analyzeFile(
                    sourceCode,
                    sourceFile.toPath(),
                    callers,  // 传空，分析时不合并 callers
                    apiKey,
                    apiUrl,
                    model,
                    temperature,
                    noValidate,
                    noCache,
                    true, // enable validation
                    schemaVersion
                );

                // 4. 输出分析结果
                if (jsonResult != null && !jsonResult.isEmpty() && !"{}".equals(jsonResult)) {
                    if (!jsonResult.startsWith("CACHED_DISPLAYED:")) {
                        System.out.println("\n" + ColorUtil.heading("━━━ 分析结果 ━━━"));
                        if (rawJson) {
                            System.out.println(jsonResult);
                        } else {
                            formatAnalysisResult(jsonResult);
                        }
                    }
                }

                // 5. Callers 反向依赖（基于索引，独立 section）
                if (projectRoot.toFile().exists()) {
                    CallerFinder searcher = new CallerFinder(indexer, projectRoot);
                    callers = searcher.findCallers(className);
                    
                    System.out.println("\n" + ColorUtil.heading("━━━ Callers 反向依赖（基于索引查询） ━━━"));
                    System.out.println("查找: " + className);
                    
                    if (callers.isEmpty()) {
                        System.out.println("未找到调用该类的代码");
                    } else {
                        System.out.println("找到 " + callers.size() + " 处调用:");
                        for (CallerFinder.CallerInfo caller : callers) {
                            System.out.println("  " + caller.filePath + ":" + caller.lineNumber);
                        }
                    }
                    
                    // 显示索引范围
                    List<String> indexedSrcRoots = indexer.getIndexedSrcRoots();
                    String srcRootsStr = indexedSrcRoots.isEmpty() ? "未知" : String.join(", ", indexedSrcRoots);
                    System.out.println("\n索引范围: " + srcRootsStr);
                }
            } // try-with-resources 自动关闭 indexer
            
        } catch (Exception e) {
            System.out.println("[!] JavaParser 解析或 LLM 分析失败: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "分析失败", e);
        }
        
        System.out.println("\n" + ColorUtil.heading("━━━ full 命令执行完成 ━━━"));
    }

    // ==================== 格式化输出方法 ====================

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

    private static void printLlmConfig(String apiUrl, String model, double temperature) {
        String url = apiUrl != null ? apiUrl : LLMClient.getDefaultApiUrl();
        String mdl = model != null ? model : LLMClient.getDefaultModel();
        double temp = Double.isNaN(temperature) ? LLMClient.getDefaultTemperature() : temperature;
        System.out.println(ColorUtil.info("API 地址: ") + url);
        System.out.println(ColorUtil.info("模型: ") + mdl);
        System.out.println(ColorUtil.info("温度: ") + temp);
        System.out.println();
    }

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

            // 概要（V2/V3 共用）
            if (root.has("summary") && !root.get("summary").isJsonNull()) {
                String summary = root.get("summary").getAsString();
                if (!summary.isEmpty()) {
                    System.out.println(ColorUtil.info("概要: ") + summary);
                }
            }

            // 检测 Schema 版本：V3 有 methods 字段，V2 有 keyMethods 字段
            boolean isV3Format = root.has("methods") && root.get("methods").isJsonArray();

            if (isV3Format) {
                // ==================== V3 渲染 ====================

                // framework
                if (root.has("framework") && !root.get("framework").isJsonNull()) {
                    String framework = root.get("framework").getAsString();
                    if (!framework.isEmpty()) {
                        System.out.println();
                        System.out.println(ColorUtil.heading("━━━ 框架集成 ━━━"));
                        System.out.println("  " + framework.trim());
                    }
                }

                // fields
                if (root.has("fields") && root.get("fields").isJsonArray()) {
                    JsonArray fields = root.getAsJsonArray("fields");
                    if (fields.size() > 0) {
                        System.out.println();
                        System.out.println(ColorUtil.heading("━━━ 字段 ━━━"));
                        for (JsonElement elem : fields) {
                            JsonObject f = elem.getAsJsonObject();
                            String name = getStringField(f, "name", "?");
                            String type = getStringField(f, "type", "");
                            String injectType = getStringField(f, "injectType", "");
                            String line = getStringField(f, "line", "");
                            String desc = getStringField(f, "description", "");

                            String lineInfo = line.isEmpty() ? "" : " L" + line;
                            String injectInfo = injectType.isEmpty() ? "" : " [" + injectType + "]";
                            System.out.println("  * " + name + " (" + type + ")" + injectInfo + lineInfo
                                + (desc.isEmpty() ? "" : " - " + desc));
                        }
                    }
                }

                // methods (含 calls + risks)
                if (root.has("methods") && root.get("methods").isJsonArray()) {
                    JsonArray methods = root.getAsJsonArray("methods");
                    if (methods.size() > 0) {
                        System.out.println();
                        System.out.println(ColorUtil.heading("━━━ 方法 ━━━"));
                        for (JsonElement elem : methods) {
                            JsonObject m = elem.getAsJsonObject();
                            String name = getStringField(m, "name", "?");
                            String line = getStringField(m, "line", "");
                            String visibility = getStringField(m, "visibility", "");
                            String complexity = getStringField(m, "complexity", "MEDIUM");
                            String desc = getStringField(m, "description", "");

                            String lineInfo = line.isEmpty() ? "" : "L" + line;
                            String visInfo = formatVisibility(visibility);
                            // V3 用 complexity 字段，降级兼容 visibility
                            String badge = !visibility.isEmpty() ? visInfo : formatSeverity(complexity);
                            System.out.println("  * " + name + " (" + lineInfo + ", " + badge + ")"
                                + (desc.isEmpty() ? "" : " - " + desc));

                            // methods[].calls
                            if (m.has("calls") && m.get("calls").isJsonArray()) {
                                JsonArray calls = m.getAsJsonArray("calls");
                                for (JsonElement ce : calls) {
                                    if (ce.isJsonPrimitive()) {
                                        // 字符串格式调用（兼容旧输出）
                                        System.out.println("    调用: " + ce.getAsString());
                                        continue;
                                    }
                                    if (!ce.isJsonObject()) continue;
                                    JsonObject call = ce.getAsJsonObject();
                                    String target = getStringField(call, "target", getStringField(call, "method", "?"));
                                    String callLine = getStringField(call, "line", "");
                                    String callType = getStringField(call, "type", "");
                                    String callInfo = callLine.isEmpty() ? "" : " [" + callLine;
                                    callInfo += callType.isEmpty() ? "" : ", " + callType;
                                    callInfo += callLine.isEmpty() ? "" : "]";
                                    System.out.println("    调用: " + target + callInfo);
                                }
                            }

                            // methods[].risks
                            if (m.has("risks") && m.get("risks").isJsonArray()) {
                                JsonArray risks = m.getAsJsonArray("risks");
                                for (JsonElement re : risks) {
                                    if (!re.isJsonObject()) continue;
                                    JsonObject risk = re.getAsJsonObject();
                                    String severity = getStringField(risk, "severity", "MEDIUM");
                                    String riskDesc = getStringField(risk, "description", "");
                                    String riskLine = getStringField(risk, "line", "");
                                    String riskConfidence = getStringField(risk, "confidence", "");
                                    String riskInfo = riskLine.isEmpty() ? "" : " (行" + riskLine + ")";

                                    // confidence 显示：CERTAIN→[确定], POSSIBLE→[可能], 无→[确定]（兼容旧输出）
                                    String confidenceTag = "";
                                    if ("POSSIBLE".equals(riskConfidence)) {
                                        confidenceTag = ColorUtil.medium("[可能] ");
                                    } else {
                                        confidenceTag = ColorUtil.business("[确定] ");
                                    }
                                    System.out.println("    " + ColorUtil.warning("⚠ ") + confidenceTag
                                        + formatSeverity(severity) + riskInfo + ": " + riskDesc);
                                }
                            }
                        }
                    }
                }

                // 顶层 risks（跨方法/跨字段审计）
                if (root.has("risks") && root.get("risks").isJsonArray()) {
                    JsonArray topRisks = root.getAsJsonArray("risks");
                    if (topRisks.size() > 0) {
                        System.out.println();
                        System.out.println(ColorUtil.heading("━━━ 文件级风险 ━━━"));
                        for (JsonElement re : topRisks) {
                            if (!re.isJsonObject()) continue;
                            JsonObject risk = re.getAsJsonObject();
                            String severity = getStringField(risk, "severity", "MEDIUM");
                            String riskDesc = getStringField(risk, "description", "");
                            String riskLine = getStringField(risk, "line", "");
                            String riskConfidence = getStringField(risk, "confidence", "");
                            String riskInfo = riskLine.isEmpty() ? "" : " (行" + riskLine + ")";

                            String confidenceTag = "";
                            if ("POSSIBLE".equals(riskConfidence)) {
                                confidenceTag = ColorUtil.medium("[可能] ");
                            } else {
                                confidenceTag = ColorUtil.business("[确定] ");
                            }
                            System.out.println("  " + ColorUtil.warning("⚠ ") + confidenceTag
                                + formatSeverity(severity) + riskInfo + ": " + riskDesc);

                            if (risk.has("suggestion") && !risk.get("suggestion").isJsonNull()) {
                                String suggestion = risk.get("suggestion").getAsString();
                                if (!suggestion.isEmpty()) {
                                    System.out.println("    建议: " + suggestion);
                                }
                            }
                        }
                    }
                }

            } else {
                // ==================== V2 渲染（保持不变） ====================

                if (root.has("design_intent") && !root.get("design_intent").isJsonNull()) {
                    String designIntent = root.get("design_intent").getAsString();
                    if (!designIntent.isEmpty()) {
                        System.out.println(ColorUtil.info("设计意图: ") + designIntent);
                    }
                }

                if (root.has("class_analysis") && !root.get("class_analysis").isJsonNull()) {
                    String classAnalysis = root.get("class_analysis").getAsString();
                    if (!classAnalysis.isEmpty()) {
                        System.out.println(ColorUtil.info("数据流: ") + classAnalysis);
                    }
                }

                // 依赖关系
                if (root.has("dependencies") && root.get("dependencies").isJsonArray()) {
                    JsonArray deps = root.getAsJsonArray("dependencies");
                    if (deps.size() > 0) {
                        System.out.println();
                        System.out.println(ColorUtil.heading("━━━ 依赖关系 ━━━"));
                        for (JsonElement depElem : deps) {
                            JsonObject dep = depElem.getAsJsonObject();
                            String depName = getStringField(dep, "name", "?");
                            String depType = getStringField(dep, "type", "");
                            String depLine = getStringField(dep, "line", "");
                            String depDesc = getStringField(dep, "description", "");

                            String lineInfo = depLine.isEmpty() ? "" : ", L" + depLine;
                            System.out.println("  * " + depName + " (" + depType + lineInfo + ") - " + depDesc);
                        }
                    }
                }

                // 风险项
                if (root.has("risks") && root.get("risks").isJsonArray()) {
                    JsonArray risks = root.getAsJsonArray("risks");
                    if (risks.size() > 0) {
                        System.out.println();
                        System.out.println(ColorUtil.heading("━━━ 风险项 ━━━"));
                        for (JsonElement riskElem : risks) {
                            JsonObject risk = riskElem.getAsJsonObject();
                            String severity = getStringField(risk, "severity", "LOW");
                            String riskDesc = getStringField(risk, "description", "");
                            String riskLine = getStringField(risk, "line", "");

                            String severityTag = formatSeverity(severity);
                            String lineInfo = riskLine.isEmpty() ? "" : "(行" + riskLine + ")";
                            System.out.println("  " + ColorUtil.warning("! ") + severityTag + " " + lineInfo + " " + riskDesc);

                            if (risk.has("suggestion") && !risk.get("suggestion").isJsonNull()) {
                                String suggestion = risk.get("suggestion").getAsString();
                                if (!suggestion.isEmpty()) {
                                    System.out.println("    建议: " + suggestion);
                                }
                            }
                        }
                    }
                }

                // 关键方法
                if (root.has("keyMethods") && root.get("keyMethods").isJsonArray()) {
                    JsonArray keyMethods = root.getAsJsonArray("keyMethods");
                    if (keyMethods.size() > 0) {
                        System.out.println();
                        System.out.println(ColorUtil.heading("━━━ 关键方法 ━━━"));
                        for (JsonElement kmElem : keyMethods) {
                            JsonObject km = kmElem.getAsJsonObject();
                            String kmName = getStringField(km, "name", "?");
                            String kmLine = getStringField(km, "line", "");
                            String visibility = getStringField(km, "visibility", "");
                            String kmDesc = getStringField(km, "description", "");
                            String severity = getStringField(km, "severity", "MEDIUM");

                            String lineInfo = kmLine.isEmpty() ? "" : "L" + kmLine;
                            String visInfo = formatVisibility(visibility);
                            System.out.println("  * " + kmName + " (" + lineInfo + ", " + formatSeverity(severity) + ", " + visInfo + ") - " + kmDesc);
                        }
                    }
                }

                // 框架集成
                if (root.has("framework_integration") && !root.get("framework_integration").isJsonNull()) {
                    String framework = root.get("framework_integration").getAsString();
                    if (!framework.isEmpty()) {
                        System.out.println();
                        System.out.println(ColorUtil.heading("━━━ 框架集成 ━━━"));
                        String[] lines = framework.split("\n");
                        for (String fLine : lines) {
                            if (!fLine.trim().isEmpty()) {
                                System.out.println("  " + fLine.trim());
                            }
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

    private static String getStringField(JsonObject obj, String field, String defaultValue) {
        if (obj.has(field) && !obj.get(field).isJsonNull()) {
            return obj.get(field).getAsString();
        }
        return defaultValue;
    }

    private static String formatSeverity(String severity) {
        switch (severity.toUpperCase()) {
            case "HIGH": return ColorUtil.high("[" + severity + "]");
            case "MEDIUM": return ColorUtil.medium("[" + severity + "]");
            case "LOW": return ColorUtil.low("[" + severity + "]");
            default: return ColorUtil.info("[" + severity + "]");
        }
    }

    /**
     * 格式化可见性（Schema v2 替代 formatComplexity）
     */
    private static String formatVisibility(String visibility) {
        switch (visibility.toLowerCase()) {
            case "public": return ColorUtil.business(visibility);
            case "private": return ColorUtil.framework(visibility);
            case "protected": return ColorUtil.info(visibility);
            default: return visibility;
        }
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
