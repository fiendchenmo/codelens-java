package com.codelens.common.agent;

/**
 * 方法分析 Agent 的 Prompt 模板。
 * <p>
 * 对每个非平凡方法生成 L1 证据 + L2 置信度标注。
 */
public class MethodAnalysisPrompt {

    private static final String SYSTEM_PROMPT =
            "你是一位 Java 代码安全与质量分析专家。请对以下方法进行风险分析。\n" +
            "\n" +
            "输出格式必须为 JSON，包含以下字段：\n" +
            "\n" +
            "1. method: 方法名 (String)\n" +
            "2. l1Evidence: 一级证据 (Object)，包含：\n" +
            "   - calls: 方法内调用的其他方法列表 (Array of String)\n" +
            "   - calledBy: 可能调用此方法的方法列表 (Array of String)\n" +
            "   - fieldsUsed: 方法内使用的字段列表 (Array of String)\n" +
            "3. l2Confidence: 二级置信度 (Object)，包含：\n" +
            "   - overallScore: 置信度分数 (Number, 0.0-1.0)\n" +
            "   - reasoningBasis: 推理依据 (String)，可选值: SOLID_ANALYSIS / HEURISTIC / PARTIAL / UNKNOWN\n" +
            "   - riskIndicators: 风险指示器列表 (Array of String)\n" +
            "\n" +
            "约束：\n" +
            "- 仅输出 JSON，不要包含 ```json 标记\n" +
            "- overallScore 必须在 0.0 到 1.0 之间\n" +
            "- reasoningBasis 必须是枚举值之一\n" +
            "- calls / calledBy / fieldsUsed 至少有一个非空";

    private static final String USER_PROMPT_TEMPLATE =
            "请分析以下方法：\n" +
            "\n" +
            "=== 方法签名 ===\n" +
            "{{methodSignature}}\n" +
            "\n" +
            "=== 方法源码 ===\n" +
            "{{methodSourceCode}}\n" +
            "\n" +
            "=== 文件摘要 ===\n" +
            "{{fileSummary}}\n" +
            "\n" +
            "=== 索引元数据 ===\n" +
            "{{metadata}}";

    /**
     * 生成 system prompt。
     */
    public String generateSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * 生成 user prompt。
     *
     * @param methodSignature 方法签名
     * @param methodSourceCode 方法源码
     * @param fileSummary 包含该方法的文件摘要（Phase 2 产出）
     * @param metadata 索引元数据
     */
    public String generateUserPrompt(String methodSignature, String methodSourceCode,
                                     String fileSummary, String metadata) {
        if (methodSignature == null) methodSignature = "";
        if (methodSourceCode == null) methodSourceCode = "";
        if (fileSummary == null) fileSummary = "";
        if (metadata == null) metadata = "";
        return USER_PROMPT_TEMPLATE
                .replace("{{methodSignature}}", methodSignature)
                .replace("{{methodSourceCode}}", methodSourceCode)
                .replace("{{fileSummary}}", fileSummary)
                .replace("{{metadata}}", metadata);
    }
}
