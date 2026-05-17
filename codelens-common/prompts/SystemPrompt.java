// SYNC_SOURCE: codelens-common/models/CodeMetaData.java (JSON_SCHEMA + CORE_RULES)
// SYNC_VERSION: 2026-05-17-v1
// 维护方：喵呜（CLI端），prompt/校验器相关由喵呜拍板
// 说明：共享 prompt 模板，引用 CodeMetaData.JSON_SCHEMA + CORE_RULES，补充 CLI 特有上下文

package com.codelens.common.prompts;

import com.codelens.common.models.CodeMetaData;

/**
 * 系统提示词模板 — 由 codelens-common 统一维护
 * CLI 端专用构建方法，JSON Schema + 核心规则引用自 CodeMetaData
 * 插件端请使用自己的 buildStructuredSystemPrompt() 引用相同 schema + 规则
 */
public class SystemPrompt {

    private SystemPrompt() {}

    /**
     * 构建 CLI 端 LLM 分析用的系统提示词
     * 包含：JSON Schema + 核心规则 + CLI 特有说明
     */
    public static String build() {
        return "你是Java遗留代码分析专家，专精架构级问题发现。必须严格按JSON格式输出，不要输出任何JSON以外的内容。\n"
            + "JSON Schema如下：\n"
            + CodeMetaData.JSON_SCHEMA + "\n"
            + CodeMetaData.CORE_RULES + "\n"
            + "CLI 端特有要求：\n"
            + "16. 同一安全风险只列一条risk，在 description 中列举所有涉及方法，只标首个入口行号\n"
            + "17. class_analysis 只写数据流路径，不要重复其他字段内容\n"
            + "18. 架构改进建议必须包含 trade-off 分析：每个 suggestion 需说明解决了什么问题/"
            + "引入了什么新问题/适用前提条件\n"
            + "19. keyMethods 必须包含方法上的关键注解（特别是 @Transactional、@Async、@Scheduled 等影响行为的注解）和可见性\n"
            + "20. 只输出 JSON，不要 markdown 代码块包裹";
    }
}
