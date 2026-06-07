package com.codelens.common.agent;

/**
 * 摘要 Agent 的 Prompt 模板。
 * <p>
 * 封装 system prompt 和 user prompt 模板，用于对 Java 文件生成结构化摘要。
 */
public class SummaryPrompt {

    private static final String SYSTEM_PROMPT =
            "你是一位 Java 代码分析专家。请对以下 Java 文件生成结构化摘要。\n" +
            "\n" +
            "输出格式必须为 JSON，包含以下 8 个字段：\n" +
            "1. className: 类的全限定名 (String)\n" +
            "2. stereotype: 类角色 (String), 可选值: SERVICE / CONTROLLER / REPOSITORY / CONFIGURATION / DTO / ENTITY / UTILITY / UNKNOWN\n" +
            "3. summary: 类的语义摘要 (String, ≤100 字)，描述类的核心职责和功能\n" +
            "4. frameworkDesc: 框架描述 (String)，该类使用的框架或技术栈说明（如 Spring MVC Controller、Spring Data Repository 等），无框架时填 \"-\"\n" +
            "5. fields: 字段列表 (Array of Object), 每个对象包含:\n" +
            "   - name: 字段名 (String)\n" +
            "   - type: 字段类型 (String)\n" +
            "   - injectType: 注入方式 (String), 可选值: AUTOWIRED / RESOURCE / CONSTRUCTOR / VALUE / PLAIN / UNKNOWN\n" +
            "   - description: 字段描述 (String, ≤30 字)\n" +
            "   - line: 行号 (Number)\n" +
            "6. keyMethods: 关键方法列表 (Array of Object), 每个对象包含:\n" +
            "   - name: 方法名 (String)\n" +
            "   - role: 方法职责描述 (String, ≤30 字)\n" +
            "   - complexity: 圈复杂度估计 (Integer, 1-10)\n" +
            "7. dependencies: 外部依赖列表 (Array of String), 如 [\"OrderRepository\", \"PaymentGateway\"]\n" +
            "8. complexity: 整体复杂度 (String), 可选值: LOW / MEDIUM / HIGH / VERY_HIGH\n" +
            "\n" +
            "约束：\n" +
            "- 摘要不超过 800 token（约 560 汉字）\n" +
            "- 仅输出 JSON，不要包含 ```json 标记\n" +
            "- keyMethods 至少包含 1 个方法\n" +
            "- fields 可以为空数组\n" +
            "- dependencies 可以为空数组";

    private static final String USER_PROMPT_TEMPLATE =
            "请分析以下 Java 文件：\n" +
            "\n" +
            "=== 源码 ===\n" +
            "{{sourceCode}}\n" +
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
     * @param sourceCode Java 源码
     * @param metadata   索引元数据（class 列表、方法签名列表）
     * @return 完整的 user prompt 字符串
     */
    public String generateUserPrompt(String sourceCode, String metadata) {
        if (sourceCode == null) sourceCode = "";
        if (metadata == null) metadata = "";
        return USER_PROMPT_TEMPLATE
                .replace("{{sourceCode}}", sourceCode)
                .replace("{{metadata}}", metadata);
    }
}
