// SYNC_VERSION: 2026-05-24-v1
// IMPACT: LOGIC_CHANGE
// 维护方：喵呜（CLI端）
// 同步说明：零 IntelliJ SDK 依赖，纯文本处理，CLI 单测可覆盖

package com.codelens.common.preset;

import java.util.HashMap;
import java.util.Map;

/**
 * Provider 预设配置 — 按模型/任务类型锁定推荐温度。
 *
 * 不同 LLM 模型对分析任务的最优温度不同。ProviderPreset 将温度逻辑收归 common 模块，
 * 防止用户误设高温导致分析结果不稳定。
 *
 * 使用方式：
 *   ProviderPreset preset = ProviderPreset.of("doubao", "flash");
 *   double temp = preset.getEffectiveTemperature(userTemperature);
 */
public class ProviderPreset {

    private final String provider;
    private final String model;
    private final double temperature;
    private final boolean locked;

    private static final double DEFAULT_TEMPERATURE = 0.1;
    private static final boolean DEFAULT_LOCKED = true;

    // 已知预设映射表 key = provider:model
    private static final Map<String, ProviderPreset> KNOWN_PRESETS = new HashMap<String, ProviderPreset>();

    static {
        // Flash/Doubao(免费): 0.1 — 代码分析需要低随机性
        KNOWN_PRESETS.put("doubao:flash", new ProviderPreset("doubao", "flash", 0.1, true));
        KNOWN_PRESETS.put("qwen:flash", new ProviderPreset("qwen", "flash", 0.1, true));

        // Pro/DeepSeek: 0.0 — 精准模式
        KNOWN_PRESETS.put("doubao:pro", new ProviderPreset("doubao", "pro", 0.0, true));
        KNOWN_PRESETS.put("deepseek:chat", new ProviderPreset("deepseek", "chat", 0.0, true));

        // GPT-4: 0.1 — 默认
        KNOWN_PRESETS.put("openai:gpt-4", new ProviderPreset("openai", "gpt-4", 0.1, true));
    }

    public ProviderPreset(String provider, String model, double temperature, boolean locked) {
        this.provider = provider;
        this.model = model;
        this.temperature = temperature;
        this.locked = locked;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public double getTemperature() {
        return temperature;
    }

    public boolean isLocked() {
        return locked;
    }

    /**
     * 获取 provider:model 格式的键
     */
    public String getKey() {
        return provider + ":" + model;
    }

    /**
     * 获取生效温度：
     * - locked=true：返回预设温度（忽略用户传入值）
     * - locked=false 且 userTemp ≥ 0：返回用户温度
     * - locked=false 且 userTemp < 0：返回预设温度
     */
    public double getEffectiveTemperature(double userTemp) {
        if (locked) {
            return temperature;
        }
        if (userTemp >= 0) {
            return userTemp;
        }
        return temperature;
    }

    /**
     * 判断用户温度是否被覆盖：
     * - locked=true 且 userTemp ≠ presetTemperature → true
     * - locked=false → false（用户温度生效）
     */
    public boolean isTemperatureOverridden(double userTemp) {
        if (!locked) {
            return false;
        }
        return Math.abs(userTemp - temperature) > 0.0001;
    }

    /**
     * 按 provider + model 获取预设。
     * 未知 provider/model 返回安全默认预设（temperature=0.1, locked=true）。
     */
    public static ProviderPreset of(String provider, String model) {
        if (provider == null || model == null) {
            return defaultPreset();
        }
        String key = provider.trim().toLowerCase() + ":" + model.trim().toLowerCase();
        ProviderPreset preset = KNOWN_PRESETS.get(key);
        if (preset != null) {
            return preset;
        }
        return defaultPreset();
    }

    /**
     * 从配置 Map 加载预设，支持覆盖温度和锁定状态。
     * Map 键格式：
     *   codelens.provider.{model}.temperature
     *   codelens.provider.{model}.locked
     */
    public static ProviderPreset fromMap(String provider, String model, Map<String, String> overrides) {
        ProviderPreset base = of(provider, model);
        if (overrides == null || overrides.isEmpty()) {
            return base;
        }

        double temp = base.temperature;
        boolean lock = base.locked;

        String tempKey = "codelens.provider." + model + ".temperature";
        String tempVal = overrides.get(tempKey);
        if (tempVal != null) {
            try {
                temp = Double.parseDouble(tempVal.trim());
            } catch (NumberFormatException e) {
                // 非法值，使用默认
                temp = base.temperature;
            }
        }

        String lockedKey = "codelens.provider." + model + ".locked";
        String lockedVal = overrides.get(lockedKey);
        if (lockedVal != null) {
            lock = Boolean.parseBoolean(lockedVal.trim());
        }

        return new ProviderPreset(provider, model, temp, lock);
    }

    private static ProviderPreset defaultPreset() {
        return new ProviderPreset("default", "default", DEFAULT_TEMPERATURE, DEFAULT_LOCKED);
    }
}
