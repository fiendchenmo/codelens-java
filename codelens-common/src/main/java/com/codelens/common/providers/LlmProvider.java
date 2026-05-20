package com.codelens.common.providers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * LLM Provider 预设数据。
 * 每个 provider 包含：标识名、默认 base_url、默认模型、可选模型列表。
 * <p>
 * 使用方式：
 *   LlmProvider.byId("qwen") → Qwen 的预设
 *   LlmProvider.PRESETS     → 所有预设列表
 */
public class LlmProvider {

    public final String id;
    public final String displayName;
    public final String baseUrl;
    public final String defaultModel;
    public final List<String> models;

    public LlmProvider(String id, String displayName, String baseUrl,
                       String defaultModel, List<String> models) {
        this.id = id;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.models = models;
    }

    // ==================== 预设定义 ====================

    public static final LlmProvider DEEPSEEK = new LlmProvider(
            "deepseek", "DeepSeek",
            "https://api.deepseek.com/v1/chat/completions",
            "deepseek-v4-flash",
            Arrays.asList(
                    "deepseek-v4-flash",
                    "deepseek-v4-pro",
                    "deepseek-chat",
                    "deepseek-reasoner"
            )
    );

    public static final LlmProvider QWEN = new LlmProvider(
            "qwen", "通义千问 (Qwen)",
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            "qwen-max",
            Arrays.asList(
                    "qwen-max",
                    "qwen-plus",
                    "qwen-turbo",
                    "qwen2.5-72b-instruct"
            )
    );

    public static final LlmProvider GLM = new LlmProvider(
            "glm", "智谱清言 (GLM)",
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            "glm-4-plus",
            Arrays.asList(
                    "glm-4-plus",
                    "glm-4-flash",
                    "glm-4-long",
                    "glm-4-air"
            )
    );

    public static final LlmProvider DOUBAO = new LlmProvider(
            "doubao", "字节豆包 (Doubao)",
            "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
            "doubao-pro-32k",
            Arrays.asList(
                    "doubao-pro-32k",
                    "doubao-pro-128k",
                    "doubao-lite-32k",
                    "doubao-lite-128k"
            )
    );

    public static final LlmProvider HUNYUAN = new LlmProvider(
            "hunyuan", "腾讯混元 (Hunyuan)",
            "https://api.hunyuan.tencent.com/v1/chat/completions",
            "hunyuan-lite",
            Arrays.asList(
                    "hunyuan-lite",
                    "hunyuan-standard",
                    "hunyuan-pro",
                    "hunyuan-turbo"
            )
    );

    public static final LlmProvider CUSTOM = new LlmProvider(
            "custom", "自定义 (Custom)",
            "",
            "",
            Collections.singletonList("(自行填写)")
    );

    /**
     * 所有预设列表（按常用排序：国内主流先排，custom 最后）
     */
    public static final List<LlmProvider> PRESETS = Collections.unmodifiableList(
            Arrays.asList(DEEPSEEK, QWEN, GLM, DOUBAO, HUNYUAN, CUSTOM)
    );

    /**
     * 按 id 查找 provider。
     * @param id provider 标识名（如 "qwen"）
     * @return 匹配的 provider，未匹配返回 CUSTOM
     */
    public static LlmProvider byId(String id) {
        if (id == null || id.isEmpty()) return DEEPSEEK;
        for (LlmProvider p : PRESETS) {
            if (p.id.equals(id)) return p;
        }
        return CUSTOM;
    }

    /**
     * 判断是否为 custom 模式（用户自填 base_url/model）。
     */
    public boolean isCustom() {
        return "custom".equals(id);
    }
}
