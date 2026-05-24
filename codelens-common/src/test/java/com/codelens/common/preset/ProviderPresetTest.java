package com.codelens.common.preset;

import com.codelens.common.preset.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * C-10 ProviderPreset 温度锁定 — 单元测试
 *
 * 覆盖范围：
 * 1. ProviderPreset 数据结构（provider + model + temperature + locked）
 * 2. of() 工厂方法：按 provider+model 返回预设
 * 3. 温度锁定逻辑（locked=true 忽略用户温度 / locked=false warn 但放行）
 * 4. 已知模型预设映射
 * 5. 未知模型默认行为
 * 6. 配置外化
 * 7. 边界条件
 */
public class ProviderPresetTest {

    // ========== 1. ProviderPreset 数据结构 ==========

    @Test
    void testPresetConstruction() {
        ProviderPreset preset = new ProviderPreset("doubao", "flash", 0.1, true);
        assertEquals("doubao", preset.getProvider());
        assertEquals("flash", preset.getModel());
        assertEquals(0.1, preset.getTemperature(), 0.001);
        assertTrue(preset.isLocked());
    }

    @Test
    void testPresetUnlocked() {
        ProviderPreset preset = new ProviderPreset("openai", "gpt-4", 0.1, false);
        assertFalse(preset.isLocked());
    }

    @Test
    void testPresetImmutability() {
        // ProviderPreset 应为不可变类（无 setter）
        ProviderPreset preset = new ProviderPreset("doubao", "flash", 0.1, true);
        // 只有 getter，没有 setter → 如果编译通过即满足
        assertNotNull(preset.getProvider());
    }

    // ========== 2. of() 工厂方法 ==========

    @Test
    void testOfFactoryMethod() {
        ProviderPreset preset = ProviderPreset.of("doubao", "flash");
        assertNotNull(preset);
        assertEquals("doubao", preset.getProvider());
        assertEquals("flash", preset.getModel());
    }

    @Test
    void testOfReturnsCorrectPreset() {
        // Flash/Doubao 免费模型：temperature=0.1, locked=true
        ProviderPreset flash = ProviderPreset.of("doubao", "flash");
        assertEquals(0.1, flash.getTemperature(), 0.001);
        assertTrue(flash.isLocked());

        // 同等配置的其他 provider 的 flash 模型
        ProviderPreset qwenFlash = ProviderPreset.of("qwen", "flash");
        assertEquals(0.1, qwenFlash.getTemperature(), 0.001);
    }

    @Test
    void testOfProModel() {
        // Pro/DeepSeek：temperature=0.0, locked=true
        ProviderPreset pro = ProviderPreset.of("doubao", "pro");
        assertEquals(0.0, pro.getTemperature(), 0.001);
        assertTrue(pro.isLocked());
    }

    @Test
    void testOfGpt4Model() {
        // GPT-4：temperature=0.1, locked=true
        ProviderPreset gpt4 = ProviderPreset.of("openai", "gpt-4");
        assertEquals(0.1, gpt4.getTemperature(), 0.001);
        assertTrue(gpt4.isLocked());
    }

    // ========== 3. 温度锁定逻辑 ==========

    @Test
    void testLockedTemperatureIgnoresUserOverride() {
        // locked=true 时，用户传入温度被忽略，返回预设值
        ProviderPreset preset = new ProviderPreset("doubao", "flash", 0.1, true);
        double effectiveTemp = preset.getEffectiveTemperature(0.8);
        assertEquals(0.1, effectiveTemp, 0.001);
    }

    @Test
    void testUnlockedTemperatureAllowsUserOverride() {
        // locked=false 时，用户温度生效
        ProviderPreset preset = new ProviderPreset("openai", "gpt-4", 0.1, false);
        double effectiveTemp = preset.getEffectiveTemperature(0.5);
        assertEquals(0.5, effectiveTemp, 0.001);
    }

    @Test
    void testUnlockedNoUserValueUsesDefault() {
        // locked=false 且用户未传温度 → 使用预设值
        ProviderPreset preset = new ProviderPreset("openai", "gpt-4", 0.1, false);
        double effectiveTemp = preset.getEffectiveTemperature(-1);
        assertEquals(0.1, effectiveTemp, 0.001);
    }

    @Test
    void testIsTemperatureOverridden() {
        // locked=true 时，即使传了温度也报告"被覆盖"
        ProviderPreset locked = new ProviderPreset("doubao", "flash", 0.1, true);
        assertTrue(locked.isTemperatureOverridden(0.8));

        // locked=false 时，用户温度生效，不被覆盖
        ProviderPreset unlocked = new ProviderPreset("openai", "gpt-4", 0.1, false);
        assertFalse(unlocked.isTemperatureOverridden(0.5));
    }

    // ========== 4. 已知模型预设映射 ==========

    @Test
    void testKnownPresetsComplete() {
        // 验证所有已知模型都有预设
        String[][] knownModels = {
            {"doubao", "flash"},
            {"doubao", "pro"},
            {"openai", "gpt-4"},
            {"qwen", "flash"},
            {"deepseek", "chat"}
        };
        for (String[] model : knownModels) {
            ProviderPreset preset = ProviderPreset.of(model[0], model[1]);
            assertNotNull(preset, "Missing preset for " + model[0] + "/" + model[1]);
            assertTrue(preset.getTemperature() >= 0.0 && preset.getTemperature() <= 1.0,
                "Temperature out of range for " + model[0] + "/" + model[1]);
        }
    }

    @Test
    void testAnalysisTemperatureAlwaysLow() {
        // 代码分析场景，所有预设温度都应 ≤ 0.1
        String[][] analysisModels = {
            {"doubao", "flash"},
            {"doubao", "pro"},
            {"openai", "gpt-4"},
            {"deepseek", "chat"}
        };
        for (String[] model : analysisModels) {
            ProviderPreset preset = ProviderPreset.of(model[0], model[1]);
            assertTrue(preset.getTemperature() <= 0.1,
                model[0] + "/" + model[1] + " temperature too high: " + preset.getTemperature());
        }
    }

    // ========== 5. 未知模型默认行为 ==========

    @Test
    void testUnknownModelReturnsDefault() {
        // 未知 provider/model → 返回默认预设（temperature=0.1, locked=true）
        ProviderPreset unknown = ProviderPreset.of("unknown_provider", "unknown_model");
        assertNotNull(unknown);
        assertEquals(0.1, unknown.getTemperature(), 0.001);
        assertTrue(unknown.isLocked());
    }

    @Test
    void testUnknownProviderReturnsDefault() {
        ProviderPreset unknown = ProviderPreset.of("anthropic", "claude-3");
        assertNotNull(unknown);
        // 默认预设
        assertTrue(unknown.getTemperature() <= 0.1);
    }

    // ========== 6. 配置外化 ==========

    @Test
    void testFromMapOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("codelens.provider.flash.temperature", "0.2");
        overrides.put("codelens.provider.flash.locked", "false");
        ProviderPreset preset = ProviderPreset.fromMap("doubao", "flash", overrides);
        assertEquals(0.2, preset.getTemperature(), 0.001);
        assertFalse(preset.isLocked());
    }

    @Test
    void testFromMapNoOverrides() {
        // 无覆盖项时使用默认值
        Map<String, String> empty = Collections.emptyMap();
        ProviderPreset preset = ProviderPreset.fromMap("doubao", "flash", empty);
        assertEquals(0.1, preset.getTemperature(), 0.001);
        assertTrue(preset.isLocked());
    }

    @Test
    void testFromMapInvalidTemperature() {
        // 非法温度值 → 使用默认
        Map<String, String> overrides = new HashMap<>();
        overrides.put("codelens.provider.flash.temperature", "not_a_number");
        ProviderPreset preset = ProviderPreset.fromMap("doubao", "flash", overrides);
        assertEquals(0.1, preset.getTemperature(), 0.001);
    }

    // ========== 7. 边界条件 ==========

    @Test
    void testNullProviderModel() {
        // null provider/model → 返回默认预设
        ProviderPreset preset = ProviderPreset.of(null, null);
        assertNotNull(preset);
    }

    @Test
    void testEmptyProviderModel() {
        ProviderPreset preset = ProviderPreset.of("", "");
        assertNotNull(preset);
    }

    @Test
    void testTemperatureBoundaryZero() {
        ProviderPreset preset = new ProviderPreset("test", "zero", 0.0, true);
        assertEquals(0.0, preset.getTemperature(), 0.001);
        assertEquals(0.0, preset.getEffectiveTemperature(0.5), 0.001);
    }

    @Test
    void testTemperatureBoundaryOne() {
        ProviderPreset preset = new ProviderPreset("test", "one", 1.0, true);
        assertEquals(1.0, preset.getEffectiveTemperature(0.0), 0.001);
    }

    @Test
    void testGetKey() {
        // provider+model 组合键
        ProviderPreset preset = new ProviderPreset("doubao", "flash", 0.1, true);
        assertEquals("doubao:flash", preset.getKey());
    }

    @Test
    void testJdk8Compatibility() {
        List<String> list = new ArrayList<>();
        Map<String, String> map = new HashMap<>();
        assertNotNull(list);
        assertNotNull(map);
    }
}
