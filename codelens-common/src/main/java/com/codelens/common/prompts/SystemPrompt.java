// SYNC_SOURCE: codelens-common/models/CodeMetaData.java (JSON_SCHEMA + CORE_RULES)
// SYNC_VERSION: 2026-05-21-v3
// 维护方：喵呜（CLI端），prompt/校验器相关由喵呜拍板
// 说明：共享 prompt 模板，buildBase() 提供两端共用的基础 prompt
// CLI 端用 build()，插件端用 buildBase() + 自行追加 PSI 标签上下文

package com.codelens.common.prompts;

import com.codelens.common.models.CodeMetaData;

/**
 * 系统提示词模板 — 由 codelens-common 统一维护
 * 
 * 架构：
 *   buildBase() — 两端共用（角色定义 + JSON Schema + CORE_RULES）
 *   build()    — CLI 端完整 prompt = buildBase() + CLI 特有规则
 *   插件端     — buildBase() + 插件端特有规则（PSI 标签说明等）
 */
public class SystemPrompt {

    private SystemPrompt() {}

    /**
     * 构建两端共用的基础系统提示词
     * 包含：角色定义 + JSON Schema + 核心分析规则(1-17)
     *
     * 两端必须使用此方法作为 prompt 基础，在此基础上追加各自的特有规则
     */
    public static String buildBase() {
        return "你是Java遗留代码分析专家，专精架构级问题发现。必须严格按JSON格式输出，不要输出任何JSON以外的内容。\n"
            + "JSON Schema如下：\n"
            + CodeMetaData.JSON_SCHEMA + "\n"
            + CodeMetaData.CORE_RULES + "\n";
    }

    /**
     * 构建 CLI 端 LLM 分析用的完整系统提示词
     * = buildBase() + CLI 特有规则(23-27)
     */
    public static String build() {
        return build(null);
    }

    /**
     * 构建 CLI 端 LLM 分析用的完整系统提示词，含 Layer 1 代码结构底图
     *
     * @param structContext 代码结构底图文本（由 JavaParserStructExtractor 提取），
     *                      为 null 或空时不注入
     * @return 完整系统提示词
     */
    public static String build(String structContext) {
        String base = buildBase()
            + "CLI 端特有要求：\n"
            + "24. 同一安全风险只列一条risk，在 description 中列举所有涉及方法，只标首个入口行号\n"
            + "25. class_analysis 只写数据流路径，不要重复其他字段内容\n"
            + "26. 架构改进建议必须包含 trade-off 分析：每个 suggestion 需说明解决了什么问题/"
            + "引入了什么新问题/适用前提条件\n"
            + "27. keyMethods 必须包含方法上的关键注解（特别是 @Transactional、@Async、@Scheduled 等影响行为的注解）和可见性\n"
            + "28. 只输出 JSON，不要 markdown 代码块包裹\n\n"
            + buildFewShotSection();

        if (structContext != null && !structContext.isEmpty()) {
            base += "\n\n" + structContext;
        }

        return base;
    }

    /**
     * Few-shot 示例（规则23-25）— 引导 LLM 输出格式与字段精确匹配
     */
    private static String buildFewShotSection() {
        return "=== Few-shot 示例 ===\n"
            + "--- 规则23: dependencies 字段名精确匹配 ---\n"
            + "源码字段：@Autowired private ILoginManager loginManager;\n"
            + "正确：{\"name\": \"loginManager\", \"type\": \"field\", \"line\": 26}\n"
            + "错误：{\"name\": \"LoginManager\", \"type\": \"field\", \"line\": 26}\n\n"

            + "--- 规则24: keyMethods.calls 精简 ---\n"
            + "{\n"
            + "  \"name\": \"login\",\n"
            + "  \"line\": 40,\n"
            + "  \"calls\": [\n"
            + "    {\"method\": \"loginManager.login\", \"line\": 50, \"type\": \"method_call\"},\n"
            + "    {\"method\": \"sysCorpService.selectById\", \"line\": 55, \"type\": \"method_call\"}\n"
            + "  ]\n"
            + "}\n"
            + "要求：只列核心业务调用，过滤 getter/setter/日志/工具类，每方法 ≤15 项\n\n"

            + "--- 规则25: 字段名逐字符匹配 ---\n"
            + "- sleep() 不写成 Thread.sleep()\n"
            + "- removeAttribute() 不写成 session.removeAttribute()\n"
            + "- 字段名大小写、缩写必须与源码完全一致，不脑补、不简化\n"
            + "- 不确认时检查源码中确切名称\n";
    }
}

