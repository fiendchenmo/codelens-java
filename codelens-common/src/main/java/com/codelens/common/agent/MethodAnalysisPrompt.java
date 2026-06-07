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
            "2. description: 方法语义描述 (String)，说明该方法的业务功能，不可为 N/A\n" +
            "3. logicSummary: 方法逻辑概要 (String)，简述方法的实现逻辑和关键流程，不可为 N/A\n" +
            "4. params: 参数列表 (Array of Object)，每个对象包含：\n" +
            "   - name: 参数名 (String)\n" +
            "   - type: 参数类型 (String)\n" +
            "   - usage: 使用场景 (String，可选)，说明参数在方法中的用途\n" +
            "   - sample: 示例值 (String，可选)\n" +
            "5. return: 返回值信息 (Object)，包含：\n" +
            "   - type: 返回类型 (String)\n" +
            "   - businessMeaning: 业务含义 (String，可选)，void 类型填 \"-\"\n" +
            "6. exceptions: 异常列表 (Array of Object)，每个对象包含：\n" +
            "   - type: 异常类型 (String)\n" +
            "   - handling: 处理方式 (String)，可选值: THROWS / TRY_CATCH / IGNORE / NONE\n" +
            "   - line: 行号 (Number，可选)\n" +
            "7. complexity: 圈复杂度文本 (String)，可选值: LOW / MEDIUM / HIGH / VERY_HIGH\n" +
            "8. complexity_value: 圈复杂度数值 (Number)\n" +
            "9. visibility: 可见性 (String)，如 public / private / protected / default\n" +
            "10. annotations: 注解列表 (Array of String)，如 [\"@Override\", \"@Transactional\"]\n" +
            "11. l1Evidence: 一级证据 (Object)，包含：\n" +
            "   - calls: 方法内调用的其他方法列表 (Array of String)\n" +
            "   - calledBy: 可能调用此方法的方法列表 (Array of String)\n" +
            "   - fieldsUsed: 方法内使用的字段列表 (Array of String)\n" +
            "12. l2Confidence: 二级置信度 (Object)，包含：\n" +
            "   - overallScore: 置信度分数 (Number, 0.0-1.0)\n" +
            "   - reasoningBasis: 推理依据 (String)，可选值: SOLID_ANALYSIS / HEURISTIC / PARTIAL / UNKNOWN\n" +
            "   - riskIndicators: 风险指示器列表 (Array of String)\n" +
            "13. risks: 风险列表 (Array of Object)，每个对象包含：\n" +
            "   - type: 风险类型 (String)，如 MAINTAINABILITY / SECURITY / PERFORMANCE\n" +
            "   - description: 风险描述 (String)\n" +
            "   - line: 风险所在行号 (Number)，必须对应方法源码中的实际行号\n" +
            "   - severity: 严重度 (String)，可选值: HIGH / MEDIUM / LOW\n" +
            "   - impact: 影响说明 (String，可选)\n" +
            "   - suggestion: 修复建议 (String，可选)\n" +
            "   - confidence: 置信度 (Number, 0.0-1.0，可选)\n" +
            "\n" +
            "约束：\n" +
            "- 仅输出 JSON，不要包含 ```json 标记\n" +
            "- description 和 logicSummary 绝不能为空或 N/A\n" +
            "- overallScore 必须在 0.0 到 1.0 之间\n" +
            "- reasoningBasis 必须是枚举值之一\n" +
            "- calls / calledBy / fieldsUsed 至少有一个非空\n" +
            "- risks 中每条风险的 line 为 1-indexed 行号（从文件第 1 行开始计数），必须对应方法源码中的实际行号\n" +
            "- 示例：if (paraMap == null) return; 的 null check 风险应标注为该 if 语句所在行号";

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
            "{{metadata}}\n" +
            "\n" +
            "输出示例（仅作参考，请根据实际代码分析）：\n" +
            "{\n" +
            "  \"method\": \"processOrder\",\n" +
            "  \"l1Evidence\": { \"calls\": [\"validateOrder\"], \"calledBy\": [], \"fieldsUsed\": [] },\n" +
            "  \"l2Confidence\": { \"overallScore\": 0.8, \"reasoningBasis\": \"SOLID_ANALYSIS\", \"riskIndicators\": [\"缺少参数校验\"] },\n" +
            "  \"risks\": [\n" +
            "    { \"type\": \"SECURITY\", \"description\": \"缺少参数校验\", \"line\": 28, \"severity\": \"HIGH\", \"suggestion\": \"添加参数非空校验\" }\n" +
            "  ]\n" +
            "}";

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
