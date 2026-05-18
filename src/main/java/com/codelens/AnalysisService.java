package com.codelens;

import com.codelens.common.validators.EvidenceValidator;
import com.codelens.common.validators.ConfidenceAnnotator;
import com.codelens.common.prompts.SystemPrompt;
import com.codelens.common.utils.SummaryCache;
import com.codelens.common.utils.LLMCache;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import com.google.gson.Gson;
import java.util.logging.Logger;

/**
 * 分析服务 - 核心 LLM 分析逻辑
 * 
 * 职责：
 * - 编排 LLM 调用
 * - 集成 SummaryCache 缓存
 * - 调用 EvidenceValidator 和 ConfidenceAnnotator 进行校验
 * - 返回分析结果
 */
public class AnalysisService {

    private static final Logger LOGGER = Logger.getLogger(AnalysisService.class.getName());

    private AnalysisService() {
        // 工具类，禁止实例化
    }

    /**
     * 分析结果包装类
     */
    public static class AnalysisResult {
        public String jsonResult;
        public String sourceCode;
        public JavaParserService.ClassInfo classInfo;
        public boolean fromCache;
        
        public AnalysisResult(String jsonResult, String sourceCode, JavaParserService.ClassInfo classInfo, boolean fromCache) {
            this.jsonResult = jsonResult;
            this.sourceCode = sourceCode;
            this.classInfo = classInfo;
            this.fromCache = fromCache;
        }
    }

    /**
     * 分析单个 Java 文件
     * 
     * @param sourceCode 源代码
     * @param filePath 文件路径
     * @param callers 可选的调用者信息列表（用于 full 命令）
     * @param apiKey API Key
     * @param apiUrl API 地址
     * @param model 模型名
     * @param temperature 温度参数
     * @param noValidate 是否跳过校验
     * @param noCache 是否禁用缓存
     * @param enableValidation 是否启用验证
     * @return 分析结果（JSON 字符串）
     */
    public static String analyzeFile(
            String sourceCode,
            Path filePath,
            List<CallerFinder.CallerInfo> callers,
            String apiKey,
            String apiUrl,
            String model,
            double temperature,
            boolean noValidate,
            boolean noCache,
            boolean enableValidation
    ) {
        try {
            File sourceFile = filePath.toFile();
            
            // 解析文件结构
            List<JavaParserService.ClassInfo> classInfos = JavaParserService.parseFile(sourceFile);
            String packageName = JavaParserService.getPackageName(sourceFile);
            
            if (classInfos.isEmpty()) {
                System.out.println("⚠️  未找到类定义，跳过分析: " + filePath);
                return "{}";
            }
            
            // 取第一个类作为主类
            JavaParserService.ClassInfo mainClass = classInfos.get(0);
            
            // 构建结构化上下文
            String structContext = JavaParserService.buildStructContext(packageName, classInfos);
            
            // 检查缓存
            String cacheKey = noCache ? null : SummaryCache.generateKey(sourceCode);
            String cachedSummary = (!noCache) ? SummaryCache.getInstance().get(cacheKey) : null;
            
            if (cachedSummary != null && !cachedSummary.isEmpty()) {
                String mergedResult = JsonFormatter.mergeCallersToJson(cachedSummary, callers);
                System.out.println("📦 使用缓存摘要 (可通过 --no-cache 禁用)");
                
                // 验证和标注
                if (enableValidation && !noValidate) {
                    try {
                        String[] sourceLines = sourceCode.split("\n");
                        
                        // L1 证据校验
                        EvidenceValidator vr = new EvidenceValidator();
                        vr.validate(mergedResult, sourceCode, null);
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
                
                return mergedResult;
            }
            
            // 调用 LLM 分析
            String jsonResult = callLLM(sourceCode, structContext, mainClass, apiKey, apiUrl, model, temperature);
            
            if (jsonResult == null || jsonResult.isEmpty()) {
                System.out.println("⚠️ LLM 调用返回空结果");
                return "{}";
            }
            
            // 格式化输出
            String prettyJson = JsonFormatter.prettyPrintJson(jsonResult);
            
            // 合并 callers
            String mergedResult = JsonFormatter.mergeCallersToJson(prettyJson, callers);
            
            // 保存缓存
            if (!noCache) {
                SummaryCache.getInstance().put(cacheKey, prettyJson);
            }
            
            // 验证和标注
            if (enableValidation && !noValidate) {
                try {
                    String[] sourceLines = sourceCode.split("\n");
                    
                    // L1 证据校验
                    EvidenceValidator vr = new EvidenceValidator();
                    vr.validate(mergedResult, sourceCode, null);
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
            
            return mergedResult;
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "分析失败", e);
            System.out.println("⚠️ 分析失败: " + e.getMessage());
            return "{}";
        }
    }

    /**
     * 调用 LLM 获取分析结果
     */
    private static String callLLM(
            String sourceCode,
            String structContext,
            JavaParserService.ClassInfo mainClass,
            String apiKey,
            String apiUrl,
            String model,
            double temperature
    ) {
        try {
            // 读取系统提示词
            String systemPrompt = SystemPrompt.getAnalyzePrompt();
            
            // 构建用户提示
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("请分析以下 Java 代码，返回 JSON 格式结果：\n\n");
            userPrompt.append("## 源代码文件结构\n");
            userPrompt.append(structContext);
            userPrompt.append("\n\n## 完整源代码\n");
            userPrompt.append("```java\n");
            userPrompt.append(sourceCode);
            userPrompt.append("\n```\n");
            
            // 优先使用 class 级别提示
            String classPrompt = SystemPrompt.getClassLevelPrompt(mainClass.name);
            if (classPrompt != null) {
                userPrompt.append("\n").append(classPrompt);
            }
            
            // 优先使用 method 级别提示
            for (JavaParserService.MethodInfo method : mainClass.methods) {
                String methodPrompt = SystemPrompt.getMethodLevelPrompt(mainClass.name, method.name);
                if (methodPrompt != null) {
                    userPrompt.append("\n").append(methodPrompt);
                }
            }
            
            // 优先使用 custom 提示
            String customPrompt = SystemPrompt.getCustomPrompt();
            if (customPrompt != null && !customPrompt.isEmpty()) {
                userPrompt.append("\n").append(customPrompt);
            }
            
            // 调用 LLM
            return callDeepSeekApi(
                userPrompt.toString(),
                systemPrompt,
                apiKey,
                apiUrl,
                model,
                temperature
            );
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "LLM 调用失败", e);
            return null;
        }
    }

    /**
     * 调用 DeepSeek API
     */
    private static String callDeepSeekApi(
            String userMessage,
            String systemPrompt,
            String apiKey,
            String apiUrl,
            String model,
            double temperature
    ) {
        try {
            // 构建请求
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model != null ? model : "deepseek-v4-flash");
            
            List<Map<String, String>> messages = new ArrayList<>();
            
            // 系统消息
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);
            
            // 用户消息
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);
            
            requestBody.put("messages", messages);
            
            // 温度参数
            if (!Double.isNaN(temperature)) {
                requestBody.put("temperature", temperature);
            } else {
                requestBody.put("temperature", 0.1);
            }
            
            // 构建 HTTP 请求
            String actualApiUrl = apiUrl;
            if (actualApiUrl == null || actualApiUrl.isEmpty()) {
                actualApiUrl = "https://api.deepseek.com/v1/chat/completions";
            }
            
            java.net.URL url = new java.net.URL(actualApiUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(120000);
            
            // 发送请求
            Gson gson = new Gson();
            try (java.io.OutputStream os = conn.getOutputStream()) {
                byte[] input = gson.toJson(requestBody).getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            // 读取响应
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    
                    // 解析响应
                    Map<String, Object> respMap = gson.fromJson(response.toString(), Map.class);
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                        if (message != null) {
                            return (String) message.get("content");
                        }
                    }
                }
            } else if (responseCode == 401) {
                System.out.println("⚠️ API Key 无效或已过期");
            } else if (responseCode == 429) {
                System.out.println("⚠️ 请求过于频繁，请稍后重试");
            } else {
                System.out.println("⚠️ API 请求失败: " + responseCode);
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    System.out.println("错误详情: " + errorResponse);
                }
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "API 调用失败", e);
            System.out.println("⚠️ API 调用失败: " + e.getMessage());
        }
        return null;
    }
}
