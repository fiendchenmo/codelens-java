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
import java.util.ArrayList;
import java.util.List;

/**
 * Day 2：结构化输出 + 行号引用 + 方法调用过滤 + JSON格式化
 */
public class CodeLens {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("用法：java -jar codelens.jar <Java文件路径> [DeepSeek API Key]");
            return;
        }

        String filePath = args[0];
        String apiKey = args.length >= 2 ? args[1] : "";

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

            // 字段 + 行号
            for (FieldDeclaration field : cls.getFields()) {
                FieldInfo fi = new FieldInfo();
                fi.name = field.getVariable(0).getNameAsString();
                fi.type = field.getElementType().asString();
                fi.line = field.getRange().map(r -> r.begin.line).orElse(-1);
                info.fields.add(fi);
            }

            // 方法 + 行号
            for (MethodDeclaration method : cls.getMethods()) {
                MethodInfo mi = new MethodInfo();
                mi.name = method.getNameAsString();
                mi.returnType = method.getTypeAsString();
                mi.params = method.getParameters().toString();
                mi.line = method.getRange().map(r -> r.begin.line).orElse(-1);
                info.methods.add(mi);
            }

            // 方法调用 + 行号，过滤 getter/setter 和框架通用调用
            for (MethodCallExpr call : cls.findAll(MethodCallExpr.class)) {
                String methodName = call.getNameAsString();

                // 过滤琐碎调用
                if (isTrivialCall(methodName)) continue;

                CallInfo ci = new CallInfo();
                ci.methodName = methodName;
                ci.line = call.getRange().map(r -> r.begin.line).orElse(-1);
                call.getScope().ifPresent(scope -> ci.caller = scope.toString());
                info.calls.add(ci);
            }

            classInfos.add(info);
        }

        // 打印结构化解析结果
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

        // 构建结构化 Prompt
        String structContext = buildStructContext(packageName, classInfos);
        String systemPrompt = "你是Java遗留代码分析专家。必须严格按JSON格式输出，不要输出任何JSON以外的内容。"
            + "JSON Schema如下：\n"
            + "{\n"
            + "  \"summary\": \"一句话功能摘要\",\n"
            + "  \"design_intent\": \"设计意图分析\",\n"
            + "  \"dependencies\": [{\"name\": \"依赖对象\", \"type\": \"依赖类型\", \"line\": 行号, \"reason\": \"依赖原因\"}],\n"
            + "  \"risks\": [{\"description\": \"风险描述\", \"line\": 行号, \"severity\": \"高/中/低\", \"suggestion\": \"建议\"}],\n"
            + "  \"key_methods\": [{\"name\": \"方法名\", \"line\": 行号, \"purpose\": \"作用\", \"calls\": [\"调用的方法\"]}]\n"
            + "}\n"
            + "要求：\n"
            + "1. 每条结论必须指向具体代码行号（line字段）\n"
            + "2. dependencies只列出外部依赖（非JDK类），不要列getter/setter\n"
            + "3. risks必须基于代码事实，不要猜测\n"
            + "4. 只输出JSON，不要markdown代码块包裹";

        String userPrompt = "分析以下Java文件：\n\n"
            + "【结构化解析结果】\n" + structContext + "\n\n"
            + "【源码】\n" + code;

        String result = LLMClient.analyze(apiKey, systemPrompt, userPrompt);
        System.out.println(prettyPrintJson(result));
    }

    /**
     * 判断是否为琐碎调用，需要过滤
     */
    private static boolean isTrivialCall(String methodName) {
        // getter/setter/is
        if (methodName.startsWith("get") && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) return true;
        if (methodName.startsWith("set") && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) return true;
        if (methodName.startsWith("is") && methodName.length() > 2
                && Character.isUpperCase(methodName.charAt(2))) return true;
        // Object 基础方法
        if (methodName.equals("toString")) return true;
        if (methodName.equals("hashCode")) return true;
        if (methodName.equals("equals")) return true;
        if (methodName.equals("getClass")) return true;
        if (methodName.equals("valueOf")) return true;
        // 常见集合操作
        if (methodName.equals("add") || methodName.equals("size")
                || methodName.equals("contains") || methodName.equals("remove")
                || methodName.equals("iterator") || methodName.equals("toArray")) return true;
        // RuoYi/Spring 框架通用方法
        if (methodName.equals("startPage")) return true;
        if (methodName.equals("getDataTable")) return true;
        if (methodName.equals("toAjax")) return true;
        if (methodName.equals("error")) return true;
        if (methodName.equals("success")) return true;
        if (methodName.equals("put")) return true;
        // SLF4J/Log4j 日志方法（只过滤纯日志方法，不影响业务 log() 调用）
        // 注意：auditLogger.log() 这种是业务方法，不过滤
        // 只在 caller 是 logger/log 时才过滤，这里无法判断 caller，暂不过滤
        return false;
    }

    /**
     * JSON 格式化输出（使用 Jackson）
     */
    private static String prettyPrintJson(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Object obj = mapper.readValue(json, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            // 如果解析失败，返回原文
            return json;
        }
    }

    /**
     * 构建结构化上下文，传给 LLM
     */
    private static String buildStructContext(String packageName, List<ClassInfo> classInfos) {
        StringBuilder sb = new StringBuilder();
        sb.append("包名: ").append(packageName).append("\n");
        for (ClassInfo ci : classInfos) {
            sb.append(ci.isInterface ? "接口" : "类").append(": ").append(ci.name).append("\n");
            for (FieldInfo f : ci.fields) {
                sb.append("  字段 L").append(f.line).append(" | ").append(f.name).append(": ").append(f.type).append("\n");
            }
            for (MethodInfo m : ci.methods) {
                sb.append("  方法 L").append(m.line).append(" | ").append(m.returnType)
                  .append(" ").append(m.name).append("(").append(m.params).append(")\n");
            }
            for (CallInfo c : ci.calls) {
                String prefix = c.caller != null ? c.caller + "." : "";
                sb.append("  调用 L").append(c.line).append(" | ").append(prefix).append(c.methodName).append("()\n");
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
