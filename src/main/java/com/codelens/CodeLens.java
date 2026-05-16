package com.codelens;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CodeLens - Java 代码分析工具
 * 
 * 支持的命令:
 * - analyze <Java文件路径> [API_KEY] [--api-url=URL] [--model=MODEL] [--temperature=TEMP]
 * - index <目录路径>                    : 建立代码索引
 * - callers <类名>                       : 查询反向依赖
 * - full <Java文件路径> [API_KEY] [--api-url=URL] [--model=MODEL] [--temperature=TEMP]
 * 
 * 环境变量:
 * - CODELENS_API_KEY: API Key
 * - CODELENS_API_URL: API 地址（默认 https://api.deepseek.com/v1/chat/completions）
 * - CODELENS_MODEL: 模型名（默认 deepseek-v4-flash）
 * - CODELENS_TEMPERATURE: 温度参数（默认 0.1）
 */
public class CodeLens {
    
    private static final Logger LOGGER = Logger.getLogger(CodeLens.class.getName());

    public static void main(String[] args) throws Exception {
        // 检测 --no-color 和 --no-validate 参数
        boolean noValidate = false;
        boolean noCache = false;
        List<String> filteredArgs = new ArrayList<String>();
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
     * @param args 命令行参数
     * @return 解析后的选项映射
     */
    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<String, String>();
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
        System.out.println("  CODELENS_MODEL=gpt-4 java -jar codelens.jar analyze src/main/java/MyService.java");
        System.out.println("  java -jar codelens.jar index src/main/java");
        System.out.println("  java -jar codelens.jar callers UserService");
        System.out.println("  java -jar codelens.jar full src/main/java/MyService.java YOUR_API_KEY");
    }
    
    // ========== analyze 命令 ==========
    
    private static void handleAnalyze(String[] args, boolean noValidate, boolean noCache) throws Exception {
        if (args.length < 2) {
            System.out.println(ColorUtil.error("错误: analyze 命令需要指定 Java 文件路径"));
            System.out.println("用法: java -jar codelens.jar analyze <Java文件路径> [API_KEY] [--api-url=URL] [--model=MODEL] [--temperature=TEMP]");
            return;
        }

        String filePath = args[1];
        
        // 解析选项
        Map<String, String> options = parseOptions(args);
        String apiUrl = getApiUrl(options);
        String model = getModel(options);
        double temperature = getTemperature(options);
        
        // API Key: 命令行参数优先
        String apiKey = null;
        if (args.length >= 3 && !args[2].startsWith("--")) {
            apiKey = args[2];
        }
        if (apiKey == null) {
            apiKey = System.getenv("CODELENS_API_KEY");
        }
        if (apiKey == null) apiKey = "";

        // ========== Step 1：JavaParser 解析（含行号） ==========
        System.out.println(ColorUtil.heading("━━━ Step 1：JavaParser 结构化解析 ━━━") + "\n");

        File file = new File(filePath);
        CompilationUnit cu = StaticJavaParser.parse(file);

        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("(默认包)");

        List<ClassInfo> classInfos = new ArrayList<ClassInfo>();
        for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            ClassInfo info = new ClassInfo();
            info.name = cls.getNameAsString();
            info.isInterface = cls.isInterface();

            for (FieldDeclaration field : cls.getFields()) {
                FieldInfo fi = new FieldInfo();
                fi.name = field.getVariable(0).getNameAsString();
                fi.type = field.getElementType().asString();
                fi.line = field.getRange().map(r -> r.begin.line).orElse(-1);
                info.fields.add(fi);
            }

            for (MethodDeclaration method : cls.getMethods()) {
                MethodInfo mi = new MethodInfo();
                mi.name = method.getNameAsString();
                mi.returnType = method.getTypeAsString();
                mi.params = method.getParameters().toString();
                mi.line = method.getRange().map(r -> r.begin.line).orElse(-1);
                info.methods.add(mi);
            }

            for (MethodCallExpr call : cls.findAll(MethodCallExpr.class)) {
                String methodName = call.getNameAsString();

                if (isTrivialCall(methodName)) continue;

                CallInfo ci = new CallInfo();
                ci.methodName = methodName;
                ci.line = call.getRange().map(r -> r.begin.line).orElse(-1);
                call.getScope().ifPresent(scope -> ci.caller = scope.toString());
                info.calls.add(ci);
            }

            classInfos.add(info);
        }

        System.out.println("包名: " + ColorUtil.info(packageName));
        for (ClassInfo ci : classInfos) {
            System.out.println("\n" + ColorUtil.heading((ci.isInterface ? "接口" : "类") + ": " + ci.name));
            if (!ci.fields.isEmpty()) {
                System.out.println("  字段:");
                for (FieldInfo f : ci.fields) {
                    System.out.println("    L" + f.line + " | " + f.name + ": " + ColorUtil.framework(f.type));
                }
            }
            if (!ci.methods.isEmpty()) {
                System.out.println("  方法:");
                for (MethodInfo m : ci.methods) {
                    System.out.println("    L" + m.line + " | " + ColorUtil.business(m.name) + "(" + ColorUtil.framework(m.params) + ")");
                }
            }
            if (!ci.calls.isEmpty()) {
                System.out.println("  业务调用（已过滤getter/setter/框架调用）:");
                for (CallInfo c : ci.calls) {
                    String prefix = c.caller != null ? c.caller + "." : "";
                    String callDisplay = prefix + c.methodName + "()";
                    if (isInfrastructureCall(c.methodName, c.caller)) {
                        System.out.println("    L" + c.line + " | " + ColorUtil.framework("→ " + callDisplay));
                    } else {
                        System.out.println("    L" + c.line + " | " + ColorUtil.business("→ " + callDisplay));
                    }
                }
            }
        }

        // ========== Step 2：LLM 结构化分析 ==========
        if (apiKey.isEmpty()) {
            System.out.println("\n" + ColorUtil.warning("━━━ 未提供 API Key，跳过 LLM 分析 ━━━"));
            return;
        }

        System.out.println("\n" + ColorUtil.heading("━━━ Step 2：LLM 结构化分析 ━━━") + "\n");
        
        // 打印使用的配置
        printLlmConfig(apiUrl, model, temperature);

        String code = Files.readString(Paths.get(filePath));

        // LLM 摘要缓存
        String effectiveModel = model != null ? model : LLMClient.getDefaultModel();
        SummaryCache cache = new SummaryCache(findProjectRoot(Paths.get(filePath)), !noCache);
        SummaryCache.CacheEntry cached = cache.lookup(filePath, code, effectiveModel);

        String result;
        if (cached != null) {
            result = cached.result;
            System.out.println(ColorUtil.info("[缓存命中] ") + "文件内容未变更，使用上次分析结果（模型: " + cached.model + "）\n");
        } else {
            String structContext = buildStructContext(packageName, classInfos);
            String systemPrompt = buildSystemPrompt();
            String userPrompt = "分析以下Java文件：\n\n"
                + "【结构化解析结果】\n" + structContext + "\n\n"
                + "【源码】\n" + code;

            result = LLMClient.analyze(apiKey, systemPrompt, userPrompt, apiUrl, model, temperature);
            cache.save(filePath, code, effectiveModel, result);
        }
        System.out.println(prettyPrintJson(result));

        // L1+L2 证据校验与置信度标注
        if (!noValidate) {
            try {
                String sourceCode = Files.readString(Paths.get(filePath));
                String[] sourceLines = sourceCode.split("\n");
                EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(result, sourceCode, null);
                System.out.println("\n" + ColorUtil.heading("━━━ L1 证据校验 ━━━") + "\n");
                System.out.println(vr.formatReport());

                // L2 置信度标注
                ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(result, vr, sourceLines);
                System.out.println(ColorUtil.heading("━━━ L2 置信度标注 ━━━") + "\n");
                System.out.println(ar.formatReport());
            } catch (Exception e) {
                // 校验失败不影响主流程
            }
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
    
    // ========== index 命令 ==========
    
    private static void handleIndex(String[] args) {
        if (args.length < 2) {
            System.out.println(ColorUtil.error("错误: index 命令需要指定目录路径"));
            System.out.println("用法: java -jar codelens.jar index <目录路径>");
            return;
        }
        
        String dirPath = args[1];
        java.nio.file.Path dir = Paths.get(dirPath);
        
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.out.println(ColorUtil.error("错误: 目录不存在或不是有效目录: ") + dirPath);
            return;
        }
        
        try {
            java.nio.file.Path projectRoot = findProjectRoot(dir);
            if (projectRoot == null) {
                projectRoot = dir;
            }
            
            System.out.println("项目根目录: " + projectRoot);
            System.out.println("索引目录: " + dir);
            
            CallIndex indexer = new CallIndex(projectRoot);
            int count = indexer.indexDirectory(dir);
            indexer.close();
            
            System.out.println("\n✅ 索引完成，共索引 " + count + " 个文件");
            System.out.println("索引数据库: " + projectRoot.resolve(".codelens").resolve("code_index.db"));
        } catch (SQLException e) {
            System.out.println("⚠️ 索引失败: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "索引失败", e);
        }
    }
    
    // ========== callers 命令 ==========
    
    private static void handleCallers(String[] args) {
        if (args.length < 2) {
            System.out.println(ColorUtil.error("错误: callers 命令需要指定类名"));
            System.out.println("用法: java -jar codelens.jar callers <类名>");
            return;
        }
        
        String className = args[1];
        java.nio.file.Path currentDir = Paths.get("").toAbsolutePath();
        java.nio.file.Path projectRoot = findProjectRoot(currentDir);
        
        if (projectRoot == null) {
            System.out.println(ColorUtil.error("错误: 未找到 .codelens 索引目录，请先运行 index 命令"));
            return;
        }
        
        try {
            CallIndex indexer = new CallIndex(projectRoot);
            CallerFinder callerFinder = new CallerFinder(indexer, projectRoot);
            
            // 先查询类所在的文件
            List<CallIndex.IndexResult> classResults = indexer.findByClass(className);
            
            if (classResults.isEmpty()) {
                System.out.println(ColorUtil.warning("未找到类: ") + className);
                indexer.close();
                return;
            }
            
            System.out.println(ColorUtil.info("找到类 ") + className + ColorUtil.info(" 在以下位置:"));
            for (CallIndex.IndexResult r : classResults) {
                System.out.println("  " + r);
            }
            System.out.println();
            
            // 查询被谁调用
            List<CallerFinder.CallerInfo> callers = callerFinder.findCallersWithInterfacePenetration(className);
            
            if (callers.isEmpty()) {
                System.out.println(ColorUtil.warning("没有找到调用 ") + className + ColorUtil.warning(" 的代码"));
            } else {
                System.out.println(ColorUtil.info("找到 ") + callers.size() + ColorUtil.info(" 处调用 ") + className + ColorUtil.info(" 的代码:"));
                for (CallerFinder.CallerInfo caller : callers) {
                    System.out.println("  " + caller);
                }
            }
            
            indexer.close();
        } catch (Exception e) {
            System.out.println("⚠️ 查询失败: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "查询失败", e);
        }
    }
    
    // ========== full 命令 ==========
    
    private static void handleFull(String[] args, boolean noValidate, boolean noCache) {
        if (args.length < 2) {
            System.out.println(ColorUtil.error("错误: full 命令需要指定 Java 文件路径"));
            System.out.println("用法: java -jar codelens.jar full <Java文件路径> [API_KEY] [--api-url=URL] [--model=MODEL] [--temperature=TEMP]");
            return;
        }
        
        String filePath = args[1];
        
        // 解析选项
        Map<String, String> options = parseOptions(args);
        String apiUrl = getApiUrl(options);
        String model = getModel(options);
        double temperature = getTemperature(options);
        
        // API Key: 命令行参数优先
        String apiKey = null;
        if (args.length >= 3 && !args[2].startsWith("--")) {
            apiKey = args[2];
        }
        if (apiKey == null) {
            apiKey = System.getenv("CODELENS_API_KEY");
        }
        if (apiKey == null) apiKey = "";
        
        System.out.println(ColorUtil.heading("━━━ full 命令：一键分析 ━━━") + "\n");
        
        try {
            File file = new File(filePath);
            java.nio.file.Path fileAbsolutePath = file.toPath().toAbsolutePath().normalize();
            
            // Step 1: 索引
            System.out.println(ColorUtil.heading("━━━ Step 1: 索引项目 ━━━") + "\n");
            
            java.nio.file.Path projectRoot = findProjectRootForFull(fileAbsolutePath.getParent());
            if (projectRoot == null) {
                projectRoot = fileAbsolutePath.getParent();
            }
            
            System.out.println("项目根目录: " + projectRoot);
            
            CallIndex indexer = new CallIndex(projectRoot);
            
            // 找 Java 文件所属的源码目录
            java.nio.file.Path srcRoot = findSrcRoot(fileAbsolutePath);
            System.out.println("索引目录: " + srcRoot);
            
            int indexedCount = indexer.indexDirectory(srcRoot);
            System.out.println("索引了 " + indexedCount + " 个文件\n");
            
            // Step 2: 找类名
            System.out.println(ColorUtil.heading("━━━ Step 2: 定位类 ━━━") + "\n");
            
            String className = extractClassName(file);
            System.out.println("目标类: " + className + "\n");
            
            // Step 3: 找 callers
            System.out.println(ColorUtil.heading("━━━ Step 3: 反向依赖查询 ━━━") + "\n");
            
            CallerFinder callerFinder = new CallerFinder(indexer, projectRoot);
            List<CallerFinder.CallerInfo> callers = callerFinder.findCallersWithInterfacePenetration(className);
            
            if (callers.isEmpty()) {
                System.out.println(ColorUtil.warning("没有找到调用 ") + className + ColorUtil.warning(" 的代码"));
            } else {
                System.out.println(ColorUtil.info("找到 ") + callers.size() + ColorUtil.info(" 处调用:"));
                for (CallerFinder.CallerInfo caller : callers) {
                    System.out.println("  " + caller);
                }
            }
            System.out.println();
            
            // Step 4: JavaParser + LLM
            System.out.println(ColorUtil.heading("━━━ Step 4: 结构化解析 ━━━") + "\n");
            
            CompilationUnit cu = StaticJavaParser.parse(file);
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("(默认包)");
            
            List<ClassInfo> classInfos = new ArrayList<ClassInfo>();
            for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                ClassInfo info = new ClassInfo();
                info.name = cls.getNameAsString();
                info.isInterface = cls.isInterface();
                
                for (FieldDeclaration field : cls.getFields()) {
                    FieldInfo fi = new FieldInfo();
                    fi.name = field.getVariable(0).getNameAsString();
                    fi.type = field.getElementType().asString();
                    fi.line = field.getRange().map(r -> r.begin.line).orElse(-1);
                    info.fields.add(fi);
                }
                
                for (MethodDeclaration method : cls.getMethods()) {
                    MethodInfo mi = new MethodInfo();
                    mi.name = method.getNameAsString();
                    mi.returnType = method.getTypeAsString();
                    mi.params = method.getParameters().toString();
                    mi.line = method.getRange().map(r -> r.begin.line).orElse(-1);
                    // 提取注解
                    List<String> annots = new ArrayList<>();
                    for (com.github.javaparser.ast.expr.AnnotationExpr ae : method.getAnnotations()) {
                        annots.add(ae.getNameAsString());
                    }
                    mi.annotations = String.join(", ", annots);
                    // 提取可见性
                    mi.visibility = "";
                    if (method.isPublic()) mi.visibility = "public";
                    else if (method.isProtected()) mi.visibility = "protected";
                    else if (method.isPrivate()) mi.visibility = "private";
                    info.methods.add(mi);
                }
                
                for (MethodCallExpr call : cls.findAll(MethodCallExpr.class)) {
                    String methodName = call.getNameAsString();
                    if (isTrivialCall(methodName)) continue;
                    
                    CallInfo ci = new CallInfo();
                    ci.methodName = methodName;
                    ci.line = call.getRange().map(r -> r.begin.line).orElse(-1);
                    call.getScope().ifPresent(scope -> ci.caller = scope.toString());
                    info.calls.add(ci);
                }
                
                classInfos.add(info);
            }
            
            System.out.println("包名: " + ColorUtil.info(packageName));
            for (ClassInfo ci : classInfos) {
                System.out.println("\n" + ColorUtil.heading((ci.isInterface ? "接口" : "类") + ": " + ci.name));
                if (!ci.fields.isEmpty()) {
                    System.out.println("  字段:");
                    for (FieldInfo f : ci.fields) {
                        System.out.println("    L" + f.line + " | " + f.name + ": " + ColorUtil.framework(f.type));
                    }
                }
                if (!ci.methods.isEmpty()) {
                    System.out.println("  方法:");
                    for (MethodInfo m : ci.methods) {
                        System.out.println("    L" + m.line + " | " + ColorUtil.business(m.name) + "(" + ColorUtil.framework(m.params) + ")");
                    }
                }
                if (!ci.calls.isEmpty()) {
                    System.out.println("  业务调用（已过滤getter/setter/框架调用）:");
                    for (CallInfo c : ci.calls) {
                        String prefix = c.caller != null ? c.caller + "." : "";
                        String callDisplay = prefix + c.methodName + "()";
                    if (isInfrastructureCall(c.methodName, c.caller)) {
                        System.out.println("    L" + c.line + " | " + ColorUtil.framework("→ " + callDisplay));
                    } else {
                        System.out.println("    L" + c.line + " | " + ColorUtil.business("→ " + callDisplay));
                    }
                    }
                }
            }
            
            // LLM 分析
            if (apiKey.isEmpty()) {
                System.out.println("\n" + ColorUtil.warning("━━━ 未提供 API Key，跳过 LLM 分析 ━━━"));
            } else {
                System.out.println("\n" + ColorUtil.heading("━━━ Step 5: LLM 结构化分析 ━━━") + "\n");
                
                // 打印使用的配置
                printLlmConfig(apiUrl, model, temperature);
                
                String code = Files.readString(Paths.get(filePath));
                String structContext = buildStructContext(packageName, classInfos);
                
                String systemPrompt = buildSystemPrompt();
                String userPrompt = "分析以下Java文件：\n\n"
                    + "【结构化解析结果】\n" + structContext + "\n\n"
                    + "【源码】\n" + code;
                
                // LLM 摘要缓存
                String effectiveModelForCache = model != null ? model : LLMClient.getDefaultModel();
                SummaryCache fullCache = new SummaryCache(findProjectRoot(Paths.get(filePath)), !noCache);
                SummaryCache.CacheEntry fullCached = fullCache.lookup(filePath, code, effectiveModelForCache);

                String result;
                if (fullCached != null) {
                    result = fullCached.result;
                    System.out.println(ColorUtil.info("[缓存命中] ") + "文件内容未变更，使用上次分析结果（模型: " + fullCached.model + "）\n");
                } else {
                    result = LLMClient.analyze(apiKey, systemPrompt, userPrompt, apiUrl, model, temperature);
                    fullCache.save(filePath, code, effectiveModelForCache, result);
                }
                
                String mergedResult = mergeCallersToJson(result, callers);
                System.out.println(prettyPrintJson(mergedResult));

                // L1+L2 证据校验与置信度标注
                if (!noValidate) {
                    try {
                        String sourceCode = Files.readString(Paths.get(filePath));
                        String[] sourceLines = sourceCode.split("\n");
                        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(mergedResult, sourceCode, null);
                        System.out.println("\n" + ColorUtil.heading("━━━ L1 证据校验 ━━━") + "\n");
                        System.out.println(vr.formatReport());

                        // L2 置信度标注
                        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(mergedResult, vr, sourceLines);
                        System.out.println(ColorUtil.heading("━━━ L2 置信度标注 ━━━") + "\n");
                        System.out.println(ar.formatReport());
                    } catch (Exception e) {
                        // 校验失败不影响主流程
                    }
                }
            }
            
            indexer.close();
        } catch (Exception e) {
            System.out.println("⚠️ JavaParser 解析或 LLM 分析失败: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "分析失败", e);
        }
        
        System.out.println("\n" + ColorUtil.heading("━━━ full 命令执行完成 ━━━"));
    }
    
    private static java.nio.file.Path findProjectRootForFull(java.nio.file.Path start) {
        java.nio.file.Path current = start.toAbsolutePath().normalize();
        java.nio.file.Path gitRoot = null;
        java.nio.file.Path temp = current;
        while (temp != null) {
            if (Files.exists(temp.resolve(".git"))) {
                gitRoot = temp;
            }
            temp = temp.getParent();
        }
        if (gitRoot != null) {
            return gitRoot;
        }
        
        java.nio.file.Path topmostPom = null;
        current = start.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))) {
                topmostPom = current;
            }
            current = current.getParent();
        }
        return topmostPom;
    }
    
    private static java.nio.file.Path findSrcRoot(java.nio.file.Path filePath) {
        // 尝试找到 src/main/java 或 src/test/java
        java.nio.file.Path current = filePath.getParent();
        while (current != null) {
            if (current.endsWith("src" + java.nio.file.FileSystems.getDefault().getSeparator() + "main" + java.nio.file.FileSystems.getDefault().getSeparator() + "java") ||
                current.endsWith("src" + java.nio.file.FileSystems.getDefault().getSeparator() + "test" + java.nio.file.FileSystems.getDefault().getSeparator() + "java")) {
                return current;
            }
            // 继续往上找 src
            if (current.endsWith("src")) {
                return current;
            }
            current = current.getParent();
        }
        // 找不到则返回文件父目录
        return filePath.getParent();
    }
    
    private static String extractClassName(File file) {
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
    
    private static String mergeCallersToJson(String jsonResult, List<CallerFinder.CallerInfo> callers) {
        if (callers.isEmpty()) {
            return jsonResult;
        }
        
        try {
            StringBuilder callersJson = new StringBuilder();
            callersJson.append(",\n  \"callers\": [\n");
            for (int i = 0; i < callers.size(); i++) {
                CallerFinder.CallerInfo caller = callers.get(i);
                callersJson.append("    {\n");
                callersJson.append("      \"file\": \"").append(escapeJson(caller.filePath)).append("\",\n");
                callersJson.append("      \"type\": \"").append(caller.type).append("\",\n");
                callersJson.append("      \"line\": ").append(caller.lineNumber).append(",\n");
                callersJson.append("      \"description\": \"").append(escapeJson(caller.description)).append("\"\n");
                callersJson.append("    }");
                if (i < callers.size() - 1) {
                    callersJson.append(",");
                }
                callersJson.append("\n");
            }
            callersJson.append("  ]");
            
            int lastBrace = jsonResult.lastIndexOf("}");
            if (lastBrace > 0) {
                return jsonResult.substring(0, lastBrace) + callersJson + "\n}";
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "合并 callers 到 JSON 失败", e);
        }
        return jsonResult;
    }
    
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
    
    private static String buildSystemPrompt() {
        return "你是Java遗留代码分析专家，专精架构级问题发现。必须严格按JSON格式输出，不要输出任何JSON以外的内容。"
            + "JSON Schema如下：\n"
            + "{\n"
            + "  \"summary\": \"一句话功能摘要\",\n"
            + "  \"design_intent\": \"设计意图分析：这个类在整个系统中的角色，它协调了哪些外部资源\",\n"
            + "  \"class_analysis\": \"数据流描述：从输入到输出的关键数据流转路径，只写数据流，不要重复summary和design_intent\",\n"
            + "  \"dependencies\": [{\"name\": \"依赖对象\", \"type\": \"依赖类型(字段注入/静态方法调用/构造注入)\", \"line\": 行号, \"description\": \"依赖原因\"}],\n"
            + "  \"risks\": [{\"description\": \"风险描述，必须基于代码事实，包含触发场景和影响范围\", \"line\": 行号, \"severity\": \"高/中/低\", \"impact\": \"影响面：哪些场景会触发，对系统的影响范围\", \"suggestion\": \"修复建议\"}],\n"
            + "  \"keyMethods\": [{\"name\": \"方法名(含参数签名)\", \"line\": 行号, \"visibility\": \"public/private/protected\", \"annotations\": \"方法上的注解如@Transactional等\", \"purpose\": \"作用\", \"calls\": [\"调用的方法\"], \"description\": \"该方法的特殊情况或注意事项\"}],\n"
            + "  \"framework_integration\": \"框架集成分析：本类使用了哪些框架（Spring/Quartz/MyBatis等），框架的关键调用链是什么，框架的行为如何影响本类的逻辑正确性\",\n"
            + "  \"architecture_issues\": [{\"issue\": \"架构级问题描述\", \"category\": \"分类(状态一致性/事务边界/并发安全/资源管理/初始化时序)\", \"impact\": \"对系统的影响\", \"suggestion\": \"改进建议\"}]\n"
            + "}\n"
            + "要求：\n"
            + "1. 每条risks和dependencies必须指向具体代码行号（line字段）\n"
            + "2. dependencies必须包含所有依赖注入的字段（标注了[依赖注入]的字段），以及所有第三方和框架类的静态方法调用；同一静态方法在不同业务场景中使用应按用途拆分为多条(如ScheduleUtils.createScheduleJob在init和insertJob中用途不同应分列)，同场景同方法可合并为一条标注首个行号；每条dependency的line指向首次调用行。不要列日志类（Logger/Log），不要列getter/setter，不要列纯值对象类（String/Integer/List等）\n"
            + "3. risks必须基于代码事实，不要猜测，不要写\"需确认\"类模糊描述；每条risk必须包含impact字段说明影响面：什么场景触发、对系统有什么影响、是否可被框架兜住、是否有自动恢复机制(如重启恢复)。severity判断：不可恢复的数据损坏/状态永久不一致=高，未捕获异常导致程序崩溃=高，事务无法补偿的外部系统状态变更=高；被@Transactional兜住会回滚的异常=中(即使抛NPE只要事务回滚就不算高)，逻辑错误导致校验被跳过=中，可恢复的异常=中，代码风格问题=低\n"
            + "4. 必须检查安全风险：路径遍历（文件路径拼接）、SQL注入（表名/列名拼接传入Mapper时若无法确认使用#{}参数化查询应标为风险）、空指针链（链式调用未判空，说明什么输入会触发null）、JSON解析异常，安全类风险不得遗漏\n"
            + "5. 检查异常处理对事务的影响：catch块吞异常会导致Spring事务不回滚，这是事务方法的严重问题；特别关注@Transactional方法中的异常处理\n"
            + "6. 检查跨资源一致性：当一个方法同时操作DB和外部系统（调度器/缓存/消息队列），必须分析两阶段操作的失败场景——DB成功但外部系统失败时状态是否一致，是否有补偿/回滚机制。这类问题必须写入architecture_issues，同时在risks中标注具体代码行\n"
            + "7. architecture_issues不得为空且不得合并为单条！每个维度的问题必须独立列出，至少3条。必须检查以下维度：状态一致性（多资源操作的原子性）、事务边界（@Transactional的粒度和覆盖范围）、并发安全（共享状态的线程安全）、资源管理（连接/流的关闭）、初始化时序（@PostConstruct/静态块的初始化顺序）。每类问题独立一条issue，不要合并不同类别的问题。每个issue必须有category/impact/suggestion三个字段\n"
            + "8. framework_integration不得为空！必须分析本类使用的框架的关键行为和前提条件：框架方法的副作用、框架异常处理机制、框架与DB的事务关系。例如：如果用了Quartz，必须分析JobStore类型（RAMJobStore内存存储vs JobStoreTX/JDBC持久化）对一致性的影响——若是JDBC JobStore则调度器操作和DB操作共享同一数据库，跨资源一致性问题可能不存在；如果是RAMJobStore则是真正的跨资源问题。如果用了Spring事务，要分析@Transactional的传播行为和回滚条件\n"
            + "9. keyMethods必须包含方法上的关键注解（特别是@Transactional、@Async、@Scheduled等影响行为的注解）和可见性\n"
            + "10. 同一类安全风险只列一条risk，在description中列举所有涉及方法，只标首个入口行号\n"
            + "11. class_analysis只写数据流路径，不要重复其他字段内容\n"
            + "12. 只输出JSON，不要markdown代码块包裹\n"
            + "13. 检查Spring AOP自调用问题: 当一个方法内部直接调用同类其他@Transactional方法, 这是通过this调用而非代理, @Transactional不生效, 事务被跳过而非合并. 这类自调用必须标注为风险\n"
            + "14. 架构改进建议必须包含trade-off分析: 每个suggestion需说明解决了什么问题/引入了什么新问题/适用前提条件. 例如先调调度器再改DB的建议需说明: 如果Quartz用JDBC JobStore则调度器操作也在DB事务内, 此建议不适用\n"
+ "15. keyMethods的description字段保持精简，只写关键发现，不要重复purpose已涵盖的内容。优先保证architecture_issues和risks的完整性";
    }
    
    private static java.nio.file.Path findProjectRoot(java.nio.file.Path start) {
        java.nio.file.Path current = start;
        while (current != null) {
            if (Files.exists(current.resolve(".codelens"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean isTableNameParamMethod(String methodName) {
        return methodName.contains("ByName") || methodName.contains("ByNames")
            || methodName.contains("ByNameList") || methodName.contains("ByTableName")
            || methodName.contains("ByColumn") || methodName.contains("ByColumns");
    }

    private static boolean isInfrastructureCall(String methodName, String caller) {
        // getter/setter/is
        if (methodName.startsWith("get") && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) return true;
        if (methodName.startsWith("set") && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) return true;
        if (methodName.startsWith("is") && methodName.length() > 2
                && Character.isUpperCase(methodName.charAt(2))) return true;
        // JDK/工具库包名
        if (caller != null) {
            if (caller.startsWith("System.") || caller.startsWith("Collections.")
                    || caller.startsWith("Arrays.") || caller.startsWith("Objects.")) return true;
            if (caller.startsWith("Logger.") || caller.startsWith("log.")
                    || caller.startsWith("logger.")) return true;
        }
        // 常见集合/工具方法
        if (methodName.equals("toString") || methodName.equals("hashCode")
                || methodName.equals("equals") || methodName.equals("getClass")
                || methodName.equals("valueOf") || methodName.equals("add")
                || methodName.equals("size") || methodName.equals("contains")
                || methodName.equals("remove") || methodName.equals("iterator")
                || methodName.equals("toArray") || methodName.equals("put")
                || methodName.equals("get") || methodName.equals("stream")
                || methodName.equals("collect") || methodName.equals("forEach")) return true;
        return false;
    }

    private static boolean isTrivialCall(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) return true;
        if (methodName.startsWith("set") && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) return true;
        if (methodName.startsWith("is") && methodName.length() > 2
                && Character.isUpperCase(methodName.charAt(2))) return true;
        if (methodName.equals("toString")) return true;
        if (methodName.equals("hashCode")) return true;
        if (methodName.equals("equals")) return true;
        if (methodName.equals("getClass")) return true;
        if (methodName.equals("valueOf")) return true;
        if (methodName.equals("add") || methodName.equals("size")
                || methodName.equals("contains") || methodName.equals("remove")
                || methodName.equals("iterator") || methodName.equals("toArray")) return true;
        if (methodName.equals("startPage")) return true;
        if (methodName.equals("getDataTable")) return true;
        if (methodName.equals("toAjax")) return true;
        if (methodName.equals("error")) return true;
        if (methodName.equals("success")) return true;
        
        return false;
    }

    /**
     * JSON 格式化输出（纯 Java 实现，无需 Jackson）
     */
    private static String prettyPrintJson(String json) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i-1) != '\\')) {
                inString = !inString;
            }
            if (inString) {
                sb.append(c);
                continue;
            }
            if (c == '{' || c == '[') {
                sb.append(c).append('\n');
                indent++;
                sb.append("  ".repeat(indent));
            } else if (c == '}' || c == ']') {
                sb.append('\n');
                indent--;
                sb.append("  ".repeat(indent)).append(c);
            } else if (c == ',') {
                sb.append(c).append('\n');
                sb.append("  ".repeat(indent));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String buildStructContext(String packageName, List<ClassInfo> classInfos) {
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
                if (c.caller != null && c.caller.contains("Mapper") && isTableNameParamMethod(c.methodName)) {
                    sb.append(" [注意:表名/列名参数传入Mapper，需检查SQL是否使用${}拼接]");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    // ========== 数据类 ==========
    static class ClassInfo {
        String name;
        boolean isInterface;
        List<FieldInfo> fields = new ArrayList<FieldInfo>();
        List<MethodInfo> methods = new ArrayList<MethodInfo>();
        List<CallInfo> calls = new ArrayList<CallInfo>();
    }

    static class FieldInfo {
        String name;
        String type;
        int line;
    }

    static class MethodInfo {
        String name;
        String returnType;
        String params;
        String annotations;
        String visibility;
        int line;
    }

    static class CallInfo {
        String methodName;
        String caller;
        int line;
    }
}
