// SYNC_VERSION: 2026-05-25-v1
// IMPACT: LOGIC_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.models;

import java.util.*;

/**
 * 模型特性 Profile — 描述 LLM 模型的能力边界。
 *
 * 与 C-10 ProviderPreset 温度联动但互不依赖。
 * SystemPrompt/Normalizer 可根据 capabilities 自适应调整行为。
 */
public class ModelProfile {

    private final String provider;
    private final String model;
    private final Set<ModelCapability> capabilities;
    private final int maxContextTokens;
    private final int maxOutputTokens;
    private final double recommendedTemperature;

    private static final int DEFAULT_MAX_CONTEXT = 16384;
    private static final int DEFAULT_MAX_OUTPUT = 4096;
    private static final double DEFAULT_TEMPERATURE = 0.0;

    // 已知预设映射表 key = provider:model
    private static final Map<String, ModelProfile> KNOWN_PROFILES = new HashMap<String, ModelProfile>();

    static {
        KNOWN_PROFILES.put("doubao:flash", new ModelProfile("doubao", "flash",
            enumSet(ModelCapability.FAST_RESPONSE, ModelCapability.STABLE_JSON),
            32768, 4096, 0.0));

        KNOWN_PROFILES.put("doubao:pro", new ModelProfile("doubao", "pro",
            enumSet(ModelCapability.HIGH_ACCURACY, ModelCapability.LONG_CONTEXT, ModelCapability.MULTI_TURN),
            131072, 16384, 0.0));

        KNOWN_PROFILES.put("openai:gpt-4", new ModelProfile("openai", "gpt-4",
            enumSet(ModelCapability.HIGH_ACCURACY, ModelCapability.LONG_CONTEXT,
                    ModelCapability.STABLE_JSON, ModelCapability.MULTI_TURN),
            131072, 16384, 0.0));

        KNOWN_PROFILES.put("qwen:qwen-plus", new ModelProfile("qwen", "qwen-plus",
            enumSet(ModelCapability.FAST_RESPONSE),
            32768, 8192, 0.0));

        KNOWN_PROFILES.put("deepseek:deepseek-chat", new ModelProfile("deepseek", "deepseek-chat",
            enumSet(ModelCapability.HIGH_ACCURACY, ModelCapability.MULTI_TURN),
            65536, 8192, 0.0));
    }

    public ModelProfile(String provider, String model, Set<ModelCapability> capabilities,
                        int maxContextTokens, int maxOutputTokens, double recommendedTemperature) {
        this.provider = provider;
        this.model = model;
        this.capabilities = capabilities;
        this.maxContextTokens = maxContextTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.recommendedTemperature = recommendedTemperature;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    /**
     * 返回不可修改的能力集。
     */
    public Set<ModelCapability> getCapabilities() {
        return Collections.unmodifiableSet(capabilities);
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public double getRecommendedTemperature() {
        return recommendedTemperature;
    }

    /**
     * 判断是否具备指定能力。
     */
    public boolean hasCapability(ModelCapability cap) {
        return capabilities.contains(cap);
    }

    /**
     * 按 provider + model 获取预设 Profile。
     * 全部转为小写匹配。未知模型返回 fallback Profile（不抛异常）。
     */
    public static ModelProfile of(String provider, String model) {
        if (provider == null || model == null) {
            return fallbackProfile(provider, model);
        }
        String key = provider.trim().toLowerCase() + ":" + model.trim().toLowerCase();
        ModelProfile profile = KNOWN_PROFILES.get(key);
        if (profile != null) {
            return profile;
        }
        return fallbackProfile(provider, model);
    }

    private static ModelProfile fallbackProfile(String provider, String model) {
        return new ModelProfile(
            provider != null ? provider : "",
            model != null ? model : "",
            Collections.unmodifiableSet(new HashSet<ModelCapability>()),
            DEFAULT_MAX_CONTEXT,
            DEFAULT_MAX_OUTPUT,
            DEFAULT_TEMPERATURE
        );
    }

    // JDK 1.8 兼容：替代 Set.of()
    private static Set<ModelCapability> enumSet(ModelCapability first, ModelCapability... rest) {
        Set<ModelCapability> set = new HashSet<ModelCapability>();
        set.add(first);
        for (ModelCapability cap : rest) {
            set.add(cap);
        }
        return Collections.unmodifiableSet(set);
    }
}
