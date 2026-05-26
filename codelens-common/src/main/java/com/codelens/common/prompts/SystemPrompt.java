// SYNC_SOURCE: codelens-common/models/CodeMetaData.java (JSON_SCHEMA + CORE_RULES)
// SYNC_VERSION: 2026-05-26-v1
// 维护方：喵呜（CLI端），prompt/校验器相关由喵呜拍板
// 说明：共享 prompt 模板，buildBase() 提供两端共用的基础 prompt
// C-3: buildBase(SchemaVersion) 支持版本化

package com.codelens.common.prompts;

import com.codelens.common.models.CodeMetaData;
import com.codelens.common.models.SchemaVersion;

/**
 * 系统提示词模板 — 由 codelens-common 统一维护
 * 
 * 架构：
 *   buildBase()      — 两端共用（角色定义 + JSON Schema + CORE_RULES，默认V2）
 *   buildBase(version) — 两端共用，支持指定Schema版本
 *   build()         — CLI 端完整 prompt = buildBase() + CLI 特有规则
 *   插件端          — buildBase(version) + 插件端特有规则（PSI 标签说明等）
 */
public class SystemPrompt {

    private SystemPrompt() {}

    /**
     * 构建两端共用的基础系统提示词（默认V2版本）
     * 包含：角色定义 + JSON Schema + 核心分析规则
     *
     * @deprecated 使用 {@link #buildBase(SchemaVersion)} 明确指定版本
     */
    @Deprecated
    public static String buildBase() {
        return buildBase(SchemaVersion.V2);
    }
    
    /**
     * 构建两端共用的基础系统提示词，支持指定Schema版本
     * 包含：角色定义 + JSON Schema + 核心分析规则
     *
     * @param version Schema版本，null时默认V2
     * @return 基础系统提示词
     */
    public static String buildBase(SchemaVersion version) {
        String base = "你是Java遗留代码分析专家，专精架构级问题发现。必须严格按JSON格式输出，不要输出任何JSON以外的内容。\n"
            + "JSON Schema如下：\n"
            + CodeMetaData.getSchema(version) + "\n"
            + CodeMetaData.CORE_RULES + "\n";

        // V3 追加两阶段填充协议
        if (version == SchemaVersion.V3) {
            base += "\n"
                + "## 两阶段填充协议（V3 专用）\n"
                + "\n"
                + "### 第一阶段：事实填充 [FACT]\n"
                + "- struct 中已有的信息（字段名、方法签名、调用关系）标记为 [FACT]\n"
                + "- [FACT] 项必须与 struct 完全一致，不得修改、省略或\"纠正\"\n"
                + "- 违反 [FACT] 约束的输出将被校验器标记为错误\n"
                + "\n"
                + "### 第二阶段：推理填充 [INFER]\n"
                + "- 基于 [FACT] 推导出的结论标记为 [INFER]\n"
                + "- [INFER] 项必须说明推理依据（引用哪些 [FACT] 支撑）\n"
                + "- [INFER] 项的置信度由 LLM 自行判断\n"
                + "- [INFER] 项可能被 L3 验证器二次校验\n";
        }

        return base;
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
            + "28. 只输出 JSON，不要 markdown 代码块包裹\n"
            + "29. 所有@Autowired/@Resource字段注入必须完整出现在dependencies中，禁止将字段注入归类到keyMethods.calls\n\n"
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
            + "--- 规则23: dependencies 字段名精确匹配 + 完整列举 ---\n"
            + "源码有3个@Autowired字段注入：\n"
            + "  @Autowired private ILoginManager loginManager;\n"
            + "  @Autowired private ISysDeptService sysDeptService;\n"
            + "  @Autowired private ISysCorpService sysCorpService;\n"
            + "正确（全部列出，不压缩）：\n"
            + "  {\"name\": \"loginManager\", \"type\": \"field\", \"line\": 26},\n"
            + "  {\"name\": \"sysDeptService\", \"type\": \"field\", \"line\": 29},\n"
            + "  {\"name\": \"sysCorpService\", \"type\": \"field\", \"line\": 32}\n"
            + "错误（少列）：{\"name\": \"loginManager\", \"type\": \"field\", \"line\": 26}\n\n"

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

