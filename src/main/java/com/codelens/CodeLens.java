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
 * Day 1：JavaParser 解析 + LLM 分析
 * 目标：解析一个 Java 文件，提取包名、类名、方法名、方法调用，然后调 LLM 做智能分析
 */
public class CodeLens {

    public static void main(String[] args) throws Exception {
        // 用法：java -jar codelens.jar <文件路径> [API_KEY]
        if (args.length < 1) {
            System.out.println("用法：java -jar codelens.jar <Java文件路径> [DeepSeek API Key]");
            return;
        }

        String filePath = args[0];
        String apiKey = args.length >= 2 ? args[1] : "";

        // ========== Step 1：JavaParser 解析 ==========
        System.out.println("━━━ Step 1：JavaParser 解析 ━━━");

        File file = new File(filePath);
        CompilationUnit cu = StaticJavaParser.parse(file);

        // 提取包名
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("(默认包)");
        System.out.println("📦 包名: " + packageName);

        // 提取类信息
        List<ClassInfo> classInfos = new ArrayList<>();
        for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            ClassInfo info = new ClassInfo();
            info.name = cls.getNameAsString();
            info.isInterface = cls.isInterface();

            // 字段
            for (FieldDeclaration field : cls.getFields()) {
                info.fields.add(field.getVariable(0).getNameAsString() + ": " + field.getElementType());
            }

            // 方法
            for (MethodDeclaration method : cls.getMethods()) {
                MethodInfo mi = new MethodInfo();
                mi.name = method.getNameAsString();
                mi.returnType = method.getTypeAsString();
                mi.params = method.getParameters().toString();
                info.methods.add(mi);
            }

            // 方法调用（依赖关系雏形）
            for (MethodCallExpr call : cls.findAll(MethodCallExpr.class)) {
                String caller = call.getNameAsString();
                call.getScope().ifPresent(scope -> {
                    info.methodCalls.add(scope + "." + caller + "()");
                });
            }

            classInfos.add(info);
        }

        // 打印解析结果
        for (ClassInfo ci : classInfos) {
            System.out.println("\n🏷️ " + (ci.isInterface ? "接口" : "类") + ": " + ci.name);
            if (!ci.fields.isEmpty()) {
                System.out.println("  字段:");
                for (String f : ci.fields) System.out.println("    - " + f);
            }
            if (!ci.methods.isEmpty()) {
                System.out.println("  方法:");
                for (MethodInfo m : ci.methods) {
                    System.out.println("    - " + m.returnType + " " + m.name + "(" + m.params + ")");
                }
            }
            if (!ci.methodCalls.isEmpty()) {
                System.out.println("  方法调用（依赖）:");
                for (String c : ci.methodCalls) System.out.println("    → " + c);
            }
        }

        // ========== Step 2：LLM 分析（如果提供了 API Key）==========
        if (apiKey.isEmpty()) {
            System.out.println("\n━━━ 未提供 API Key，跳过 LLM 分析 ━━━");
            System.out.println("提示：传入第二个参数即可启用 LLM 分析");
            return;
        }

        System.out.println("\n━━━ Step 2：LLM 智能分析 ━━━");

        // 读取源码原文
        String code = Files.readString(Paths.get(filePath));

        // 构建 Prompt：把 JavaParser 解析结果 + 源码一起给 LLM
        String systemPrompt = "你是一个Java遗留代码分析专家。分析代码的功能、设计意图、潜在风险。输出简洁的中文。";
        String userPrompt = "以下是一个Java文件的解析结果和源码，请分析：\n\n"
                + "【包名】" + packageName + "\n"
                + "【类列表】" + classInfos.toString() + "\n"
                + "【源码】\n" + code;

        String result = LLMClient.analyze(apiKey, systemPrompt, userPrompt);
        System.out.println(result);
    }

    // ========== 内部数据类 ==========
    static class ClassInfo {
        String name;
        boolean isInterface;
        List<String> fields = new ArrayList<>();
        List<MethodInfo> methods = new ArrayList<>();
        List<String> methodCalls = new ArrayList<>();

        public String toString() {
            return name + "{methods=" + methods.size() + ", calls=" + methodCalls.size() + "}";
        }
    }

    static class MethodInfo {
        String name;
        String returnType;
        String params;
    }
}
