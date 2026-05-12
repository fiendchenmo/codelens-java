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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CodeLens - Java 代码分析工具
 * 
 * 支持的命令:
 * - analyze <Java文件路径> [API_KEY] : 分析 Java 文件
 * - index <目录路径>                    : 建立代码索引
 * - callers <类名>                       : 查询反向依赖
 * - full <Java文件路径> [API_KEY]       : 一键完成 index + callers + analyze
 * 
 * 环境变量:
 * - CODELENS_API_KEY: API Key（可选，优先级低于命令行参数）
 */
public class CodeLens {
    
    private static final Logger LOGGER = Logger.getLogger(CodeLens.class.getName());

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            printUsage();
            return;
        }

        String command = args[0];
        
        switch (command) {
            case "analyze":
                handleAnalyze(args);
                break;
            case "index":
                handleIndex(args);
                break;
            case "callers":
                handleCallers(args);
                break;
            case "full":
                handleFull(args);
                break;
            case "--help":
            case "-h":
                printUsage();
                break;
            default:
                handleAnalyze(args);
                break;
        }
    }
    
    private static void printUsage() {
        System.out.println("CodeLens - Java 代码分析工具");
        System.out.println("");
        System.out.println("用法:");
        System.out.println("  java -jar codelens.jar analyze <Java文件路径> [API_KEY]");
        System.out.println("                              - 分析 Java 文件（使用 LLM）");
        System.out.println("  java -jar codelens.jar index <目录路径>");
        System.out.println("                              - 建立代码索引");
        System.out.println("  java -jar codelens.jar callers <类名>");
        System.out.println("                              - 查询反向依赖关系");
        System.out.println("  java -jar codelens.jar full <Java文件路径> [API_KEY]");
        System.out.println("                              - 一键完成 index + callers + analyze");
        System.out.println("  java -jar codelens.jar --help");
        System.out.println("                              - 显示帮助信息");
        System.out.println("");
        System.out.println("环境变量:");
        System.out.println("  CODELENS_API_KEY - API Key（优先级低于命令行参数）");
        System.out.println("");
        System.out.println("示例:");
        System.out.println("  java -jar codelens.jar analyze src/main/java/MyService.java");
        System.out.println("  CODELENS_API_KEY=xxx java -jar codelens.jar analyze src/main/java/MyService.java");
        System.out.println("  java -jar codelens.jar analyze src/main/java/MyService.java YOUR_API_KEY");
        System.out.println("  java -jar codelens.jar index src/main/java");
        System.out.println("  java -jar codelens.jar callers UserService");
        System.out.println("  java -jar codelens.jar full src/main/java/MyService.java YOUR_API_KEY");
    }
    
    // ========== analyze 命令 ==========
    
    private static void handleAnalyze(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("错误: analyze 命令需要指定 Java 文件路径");
            System.out.println("用法: java -jar codelens.jar analyze <Java文件路径> [API_KEY]");
            return;
        }

        String filePath = args[1];
        String apiKey = args.length >= 3 ? args[2] : System.getenv("CODELENS_API_KEY");
        if (apiKey == null) apiKey = "";

        // ========== Step 1：JavaParser 解析（含行号） ==========
        System.out.println("━━━ Step 1：JavaParser 结构化解析 ━━━\n");

        File file = new File(filePath);
        CompilationUnit cu = StaticJavaParser.parse(file);

        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("(默认包)");

        List<ClassInfo> classInfos = new ArrayList<>();
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

        System.out.println("📦 包名: " + packageName);
        for (ClassInfo ci : classInfos) {
            System.out.println("\n🏷️ " + (ci.isInterface ? "接口" : "类") + ": " + ci.name);
            if (!ci.fields.isEmpty()) {
                System.out.println("  字段:");
                for (FieldInfo f : ci.fields) {
                    System.out.println("    L" + f.line + " | " + f.name + ": " + f.type);
                }
            }
            if (!ci.methods.isEmpty()) {
                System.out.println("  方法:");
                for (MethodInfo m : ci.methods) {
                    System.out.println("    L" + m.line + " | " + m.returnType + " " + m.name + "(" + m.params + ")");
                }
            }
            if (!ci.calls.isEmpty()) {
                System.out.println("  业务调用（已过滤getter/setter/框架调用）:");
                for (CallInfo c : ci.calls) {
                    String prefix = c.caller != null ? c.caller + "." : "";
                    System.out.println("    L" + c.line + " | → " + prefix + c.methodName + "()");
                }
            }
        }

        // ========== Step 2：LLM 结构化分析 ==========
        if (apiKey.isEmpty()) {
            System.out.println("\n━━━ 未提供 API Key，跳过 LLM 分析 ━━━");
            return;
        }

        System.out.println("\n━━━ Step 2：LLM 结构化分析 ━━━\n");

        String code = Files.readString(Paths.get(filePath));

        String structContext = buildStructContext(packageName, classInfos);
        String systemPrompt = buildSystemPrompt();
        String userPrompt = "分析以下Java文件：\n\n"
            + "【结构化解析结果】\n" + structContext + "\n\n"
            + "【源码】\n" + code;

        String result = LLMClient.analyze(apiKey, systemPrompt, userPrompt);
        System.out.println(prettyPrintJson(result));
    }
    
    // ========== index 命令 ==========
    
    private static void handleIndex(String[] args) {
        if (args.length < 2) {
            System.out.println("错误: index 命令需要指定目录路径");
            System.out.println("用法: java -jar codelens.jar index <目录路径>");
            return;
        }
        
        String dirPath = args[1];
        java.nio.file.Path dir = Paths.get(dirPath);
        
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.out.println("错误: 目录不存在或不是有效目录: " + dirPath);
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
            System.out.println("错误: callers 命令需要指定类名");
            System.out.println("用法: java -jar codelens.jar callers <类名>");
            return;
        }
        
        String className = args[1];
        java.nio.file.Path currentDir = Paths.get("").toAbsolutePath();
        java.nio.file.Path projectRoot = findProjectRoot(currentDir);
        
        if (projectRoot == null) {
            System.out.println("错误: 未找到 .codelens 索引目录，请先运行 index 命令");
            return;
        }
        
        try {
            CallIndex indexer = new CallIndex(projectRoot);
            CallerFinder callerFinder = new CallerFinder(indexer, projectRoot);
            
            // 先查询类所在的文件
            List<CallIndex.IndexResult> classResults = indexer.findByClass(className);
            
            if (classResults.isEmpty()) {
                System.out.println("未找到类: " + className);
                indexer.close();
                return;
            }
            
            System.out.println("找到类 " + className + " 在以下位置:");
            for (CallIndex.IndexResult r : classResults) {
                System.out.println("  " + r);
            }
            System.out.println();
            
            // 查询被谁调用
            List<CallerFinder.CallerInfo> callers = callerFinder.findCallers(className);
            
            if (callers.isEmpty()) {
                System.out.println("没有找到调用 " + className + " 的代码");
            } else {
                System.out.println("找到 " + callers.size() + " 处调用 " + className + " 的代码:");
                for (CallerFinder.CallerInfo caller : callers) {
                    System.out.println("  " + caller);
                }
            }
            
            indexer.close();
        } catch (SQLException e) {
            System.out.println("⚠️ 查询失败: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "查询失败", e);
        }
    }
    
    // ========== full 命令 ==========
    
    private static void handleFull(String[] args) {
        if (args.length < 2) {
            System.out.println("错误: full 命令需要指定 Java 文件路径");
            System.out.println("用法: java -jar codelens.jar full <Java文件路径> [API_KEY]");
            return;
        }
        
        String filePath = args[1];
        String apiKey = args.length >= 3 ? args[2] : System.getenv("CODELENS_API_KEY");
        if (apiKey == null) apiKey = "";
        
        System.out.println("━━━ full 命令：一键分析 ━━━\n");
        
        try {
            File file = new File(filePath);
            java.nio.file.Path fileAbsolutePath = file.toPath().toAbsolutePath().normalize();
            
            // Step 1: 索引
            System.out.println("━━━ Step 1: 索引项目 ━━━\n");
            
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
            System.out.println("━━━ Step 2: 定位类 ━━━\n");
            
            String className = extractClassName(file);
            System.out.println("目标类: " + className + "\n");
            
            // Step 3: 找 callers
            System.out.println("━━━ Step 3: 反向依赖查询 ━━━\n");
            
            CallerFinder callerFinder = new CallerFinder(indexer, projectRoot);
            List<CallerFinder.CallerInfo> callers = callerFinder.findCallers(className);
            
            if (callers.isEmpty()) {
                System.out.println("没有找到调用 " + className + " 的代码");
            } else {
                System.out.println("找到 " + callers.size() + " 处调用:");
                for (CallerFinder.CallerInfo caller : callers) {
                    System.out.println("  " + caller);
                }
            }
            System.out.println();
            
            // Step 4: JavaParser + LLM
            System.out.println("━━━ Step 4: 结构化解析 ━━━\n");
            
            CompilationUnit cu = StaticJavaParser.parse(file);
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("(默认包)");
            
            List<ClassInfo> classInfos = new ArrayList<>();
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
            
            System.out.println("📦 包名: " + packageName);
            for (ClassInfo ci : classInfos) {
                System.out.println("\n🏷️ " + (ci.isInterface ? "接口" : "类") + ": " + ci.name);
                if (!ci.fields.isEmpty()) {
                    System.out.println("  字段:");
                    for (FieldInfo f : ci.fields) {
                        System.out.println("    L" + f.line + " | " + f.name + ": " + f.type);
                    }
                }
                if (!ci.methods.isEmpty()) {
                    System.out.println("  方法:");
                    for (MethodInfo m : ci.methods) {
                        System.out.println("    L" + m.line + " | " + m.returnType + " " + m.name + "(" + m.params + ")");
                    }
                }
                if (!ci.calls.isEmpty()) {
                    System.out.println("  业务调用（已过滤getter/setter/框架调用）:");
                    for (CallInfo c : ci.calls) {
                        String prefix = c.caller != null ? c.caller + "." : "";
                        System.out.println("    L" + c.line + " | → " + prefix + c.methodName + "()");
                    }
                }
            }
            
            // LLM 分析
            if (apiKey.isEmpty()) {
                System.out.println("\n━━━ 未提供 API Key，跳过 LLM 分析 ━━━");
            } else {
                System.out.println("\n━━━ Step 5: LLM 结构化分析 ━━━\n");
                
                String code = Files.readString(Paths.get(filePath));
                String structContext = buildStructContext(packageName, classInfos);
                
                String systemPrompt = buildSystemPrompt();
                String userPrompt = "分析以下Java文件：\n\n"
                    + "【结构化解析结果】\n" + structContext + "\n\n"
                    + "【源码】\n" + code;
                
                String result = LLMClient.analyze(apiKey, systemPrompt, userPrompt);
                
                String mergedResult = mergeCallersToJson(result, callers);
                System.out.println(prettyPrintJson(mergedResult));
            }
            
            indexer.close();
        } catch (Exception e) {
            System.out.println("⚠️ JavaParser 解析或 LLM 分析失败: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "分析失败", e);
        }
        
        System.out.println("\n━━━ full 命令执行完成 ━━━");
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
        return "你是Java遗留代码分析专家。必须严格按JSON格式输出，不要输出任何JSON以外的内容。"
            + "JSON Schema如下：\n"
            + "{\n"
            + "  \"summary\": \"一句话功能摘要\",\n"
            + "  \"design_intent\": \"设计意图分析\",\n"
            + "  \"class_analysis\": \"数据流描述：从输入到输出的关键数据流转路径，只写数据流，不要重复summary和design_intent\",\n"
            + "  \"dependencies\": [{\"name\": \"依赖对象\", \"type\": \"依赖类型(字段注入/静态方法调用)\", \"line\": 行号, \"reason\": \"依赖原因\"}],\n"
            + "  \"risks\": [{\"description\": \"风险描述，必须基于代码事实\", \"line\": 行号, \"severity\": \"高/中/低\", \"suggestion\": \"修复建议\"}],\n"
            + "  \"key_methods\": [{\"name\": \"方法名\", \"line\": 行号, \"purpose\": \"作用\", \"calls\": [\"调用的方法\"], \"notes\": \"该方法的特殊情况或注意事项\"}],\n"
            + "  \"architecture_issues\": [\"架构级问题描述，不标行号\"]\n"
            + "}\n"
            + "要求：\n"
            + "1. 每条risks和dependencies必须指向具体代码行号（line字段）\n"
            + "2. dependencies只列出第三方和框架类（如JSON/JSONObject/Velocity/IOUtils/FileUtils/SecurityUtils/GenUtils/StringUtils等），不要列项目内部类（ServiceException/Constants/GenConstants/Collectors等），不要列日志类（Logger/Log等框架内置），不要列getter/setter\n"
            + "3. risks必须基于代码事实，不要猜测，不要写\"需确认\"类模糊描述——要么是问题标对应severity，要么不是就不写；severity判断：未捕获异常导致程序崩溃=高，数据不一致/事务不回滚=高，逻辑错误导致校验被跳过=中，可恢复的异常=中，代码风格问题=低\n"
            + "4. 必须检查安全风险：路径遍历（文件路径拼接）、SQL注入（表名/列名拼接传入Mapper时若无法确认使用#{}参数化查询应标为风险）、空指针链（链式调用未判空）、JSON解析异常（parseObject/parse传入空或非法字符串），安全类风险不得遗漏\n"
            + "5. 检查异常处理对事务的影响：catch块吞异常会导致Spring事务不回滚，这是事务方法的严重问题；不要对已确认继承RuntimeException的异常重复标风险\n"
            + "6. 检查if-else逻辑时注意分支是否真的能执行到，不要把\"跳过校验\"误判为\"错误地要求校验\"\n"
            + "7. risks应覆盖以下维度且每类至少检查是否有问题：安全（路径遍历/SQL注入/JSON解析异常）、逻辑缺陷（不可达分支/条件错误）、事务边界（catch吞异常）、空指针/越界、重复代码（多个方法重复相同流程）、方法设计（同名重载方法参数类型不同但逻辑不一致，如两个downloadCode或两个generatorCode行为差异）\n"
            + "8. 同一类安全风险只列一条risk，在description中列举所有涉及方法（如\"selectDbTableListByNames等3个ByName方法将表名传入Mapper\"），只标首个入口行号，绝对不要拆成多条\n"
            + "9. architecture_issues只写整体性问题，不带行号\n"
            + "10. class_analysis只写数据流路径，不要重复其他字段内容\n"
            + "11. 只输出JSON，不要markdown代码块包裹";
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
                sb.append("  字段 L").append(f.line).append(" | ").append(f.name).append(": ").append(f.type).append("\n");
            }
            for (MethodInfo m : ci.methods) {
                sb.append("  方法 L").append(m.line).append(" | ").append(m.returnType)
                  .append(" ").append(m.name).append("(").append(m.params).append(")\n");
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
        List<FieldInfo> fields = new ArrayList<>();
        List<MethodInfo> methods = new ArrayList<>();
        List<CallInfo> calls = new ArrayList<>();
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
        int line;
    }

    static class CallInfo {
        String methodName;
        String caller;
        int line;
    }
}
