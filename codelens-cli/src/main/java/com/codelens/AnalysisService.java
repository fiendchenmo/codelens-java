package com.codelens;

import com.codelens.common.cache.CacheConfig;
import com.codelens.common.cache.CacheEntry;
import com.codelens.common.cache.FileSystemCache;
import com.codelens.common.models.SchemaVersion;
import com.codelens.common.validators.EvidenceValidator;
import com.codelens.common.validators.ConfidenceAnnotator;
import com.codelens.common.normalizers.OutputNormalizer;
import com.codelens.common.prompts.SystemPrompt;
import com.codelens.ColorUtil;

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
 * - 集成 FileSystemCache 缓存
 * - 调用 EvidenceValidator 和 ConfidenceAnnotator 进行校验
 * - 返回分析结果
 */
public class AnalysisService {

    private static final Logger LOGGER = Logger.getLogger(AnalysisService.class.getName());

    private AnalysisService() {
        // 工具类，禁止实例化
    }

    /** 缓存复用：相同 projectRoot 的缓存实例重用 */
    private static FileSystemCache cachedCache;
    private static String cachedProjectRoot;

    private static FileSystemCache getCache(Path projectRoot, boolean noCache) {
        String root = projectRoot.toString();
        if (cachedCache != null && root.equals(cachedProjectRoot)) {
            return cachedCache;
        }
        CacheConfig cacheConfig = !noCache ? CacheConfig.defaults(root) : CacheConfig.disabled();
        cachedCache = new FileSystemCache(cacheConfig);
        cachedProjectRoot = root;
        return cachedCache;
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
     * @param schemaVersion Schema 版本，null 时默认 V2
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
            boolean enableValidation,
            SchemaVersion schemaVersion
    ) {
        try {
            File sourceFile = filePath.toFile();
            
            // 解析文件结构
            List<JavaParserService.ClassInfo> classInfos = JavaParserService.parseFile(sourceFile);
            String packageName = JavaParserService.getPackageName(sourceFile);
            
            if (classInfos.isEmpty()) {
                System.out.println("[!] 未找到类定义，跳过分析: " + filePath);
                return "{}";
            }
            
            // 取第一个类作为主类
            JavaParserService.ClassInfo mainClass = classInfos.get(0);
            
            // 构建结构化上下文
            String structContext = JavaParserService.buildStructContext(packageName, classInfos);
            
            // 检查缓存
            Path projectRoot = JavaParserService.findProjectRoot(filePath);
            if (projectRoot == null) projectRoot = filePath.getParent();
            FileSystemCache cache = getCache(projectRoot, noCache);
            CacheEntry cachedEntry = cache.lookup(filePath.toString(), sourceCode, model);
            String cachedSummary = (cachedEntry != null) ? cachedEntry.getResult() : null;
            
            if (cachedSummary != null && !cachedSummary.isEmpty()) {
                System.out.println("[=] 使用缓存摘要 (可通过 --no-cache 禁用)");
                
                // 格式化展示缓存的分析内容（与 LLM 新调用保持一致）
                String normalized = OutputNormalizer.normalize(cachedSummary, schemaVersion);
                String prettyCached = JsonFormatter.prettyPrintJson(normalized);
                System.out.println(ColorUtil.heading("LLM 分析（缓存）"));
                System.out.println(prettyCached);
                
                String mergedResult = JsonFormatter.mergeCallersToJson(normalized, callers);
                
                // 验证和标注
                if (enableValidation && !noValidate) {
                    try {
                        String[] sourceLines = sourceCode.split("\n");
                        
                        // L1 证据校验
                        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(mergedResult, sourceCode, null);
                        System.out.println("\n" + ColorUtil.heading("━━━ L1 证据校验 ━━━") + "\n");
                        System.out.println(vr.formatReport());

                        // L2 置信度标注
                        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(mergedResult, vr, sourceLines);
                        System.out.println(ColorUtil.heading("━━━ L2 置信度标注 ━━━") + "\n");
                        System.out.println(ar.formatReport());
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "验证/标注失败: " + e.getMessage(), e);
                        System.out.println("[!] 验证/标注失败: " + e.getMessage());
                    }
                }
                
                return "CACHED_DISPLAYED:" + mergedResult;
            }
            
            // 调用 LLM 分析（现在会抛出 LLMException）
            String jsonResult = callLLM(sourceCode, structContext, mainClass, apiKey, apiUrl, model, temperature, filePath, schemaVersion);
            
            if (jsonResult == null || jsonResult.isEmpty()) {
                System.out.println("[!] LLM 调用返回空结果");
                return "{}";
            }
            
            // 输出归一化（method_call 迁移等）
            String normalized = OutputNormalizer.normalize(jsonResult, schemaVersion);

            // 格式化输出
            String prettyJson = JsonFormatter.prettyPrintJson(normalized);
            
            // 合并 callers
            String mergedResult = JsonFormatter.mergeCallersToJson(prettyJson, callers);
            
            // 保存缓存
            if (!noCache) {
                cache.save(filePath.toString(), sourceCode, model, prettyJson);
            }
            
            // 验证和标注
            if (enableValidation && !noValidate) {
                try {
                    String[] sourceLines = sourceCode.split("\n");
                    
                    // L1 证据校验
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
            
            return mergedResult;
            
        } catch (LLMException e) {
            // LLM 异常：给用户明确提示，不让异常冒泡
            LOGGER.log(Level.SEVERE, "LLM 调用失败: " + e.getErrorType(), e);
            System.out.println("\n[FAIL] LLM 调用失败");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("错误类型: " + e.getErrorType().getDescription());
            System.out.println("提示: " + e.getUserFriendlyMessage());
            if (e.getStatusCode() > 0) {
                System.out.println("状态码: " + e.getStatusCode());
            }
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("\n请根据上述提示解决问题后重试。");
            System.out.println("提示: 使用 --no-cache 可跳过缓存，强制重新分析。");
            return "{}";
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "分析失败", e);
            System.out.println("[!] 分析失败: " + e.getMessage());
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
            double temperature,
            Path filePath,
            SchemaVersion schemaVersion
    ) {
        try {
            // Layer 1 底图提取
            String structPromptContext = null;
            try {
                com.codelens.common.normalizers.StructContext structCtx = JavaParserStructExtractor.extract(filePath);
                structPromptContext = structCtx.toPromptContext();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "底图提取失败，跳过", e);
            }

            // 构建系统提示词（含底图，指定 Schema 版本）
            String systemPrompt = SystemPrompt.build(schemaVersion, structPromptContext);

            // 构建用户提示
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("请分析以下 Java 代码，返回 JSON 格式结果：\n\n");
            userPrompt.append("## 源代码文件结构\n");
            userPrompt.append(structContext);
            userPrompt.append("\n\n## 完整源代码\n");
            userPrompt.append("```java\n");
            userPrompt.append(sourceCode);
            userPrompt.append("\n```\n");

            // 调用 LLM（现在会抛出 LLMException）
            return LLMClient.analyze(
                apiKey,
                systemPrompt,
                userPrompt.toString(),
                apiUrl,
                model,
                temperature
            );

        } catch (LLMException e) {
            // 重新抛出 LLMException，让上层处理
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "LLM 调用失败", e);
            throw new LLMException(LLMException.ErrorType.UNKNOWN, "LLM 调用准备失败: " + e.getMessage(), e);
        }
    }
}
