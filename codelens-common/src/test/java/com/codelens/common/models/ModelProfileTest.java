package com.codelens.common.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

public class ModelProfileTest {

    // ========== A1 — ModelCapability 枚举 ==========

    @Test
    void testModelCapability_allValues() {
        assertNotNull(ModelCapability.valueOf("LONG_CONTEXT"));
        assertNotNull(ModelCapability.valueOf("STABLE_JSON"));
        assertNotNull(ModelCapability.valueOf("HIGH_ACCURACY"));
        assertNotNull(ModelCapability.valueOf("FAST_RESPONSE"));
        assertNotNull(ModelCapability.valueOf("LIMITED_OUTPUT"));
        assertNotNull(ModelCapability.valueOf("MULTI_TURN"));
    }

    @Test
    void testModelCapability_noDuplicates() {
        assertEquals(6, ModelCapability.values().length);
    }

    // ========== A2 — ModelProfile 构造 ==========

    @Test
    void testHasCapability_withCapability() {
        Set<ModelCapability> caps = new HashSet<ModelCapability>();
        caps.add(ModelCapability.STABLE_JSON);
        ModelProfile profile = new ModelProfile("test", "test", caps, 4096, 1024, 0.1);
        assertTrue(profile.hasCapability(ModelCapability.STABLE_JSON));
    }

    @Test
    void testHasCapability_withoutCapability() {
        Set<ModelCapability> caps = new HashSet<ModelCapability>();
        caps.add(ModelCapability.FAST_RESPONSE);
        ModelProfile profile = new ModelProfile("test", "test", caps, 4096, 1024, 0.1);
        assertFalse(profile.hasCapability(ModelCapability.LONG_CONTEXT));
    }

    @Test
    void testModelProfile_fields() {
        Set<ModelCapability> caps = new HashSet<ModelCapability>();
        caps.add(ModelCapability.HIGH_ACCURACY);
        caps.add(ModelCapability.STABLE_JSON);
        ModelProfile profile = new ModelProfile("testProvider", "testModel", caps, 65536, 8192, 0.0);
        assertEquals("testProvider", profile.getProvider());
        assertEquals("testModel", profile.getModel());
        assertTrue(profile.hasCapability(ModelCapability.HIGH_ACCURACY));
        assertTrue(profile.hasCapability(ModelCapability.STABLE_JSON));
        assertEquals(65536, profile.getMaxContextTokens());
        assertEquals(8192, profile.getMaxOutputTokens());
        assertEquals(0.0, profile.getRecommendedTemperature(), 0.001);
    }

    @Test
    void testModelProfile_immutability() {
        Set<ModelCapability> caps = new HashSet<ModelCapability>();
        caps.add(ModelCapability.FAST_RESPONSE);
        ModelProfile profile = new ModelProfile("test", "test", caps, 4096, 1024, 0.1);
        assertThrows(UnsupportedOperationException.class, () -> {
            profile.getCapabilities().add(ModelCapability.LONG_CONTEXT);
        });
    }

    @Test
    void testModelProfile_multipleCapabilities() {
        Set<ModelCapability> caps = new HashSet<ModelCapability>();
        caps.add(ModelCapability.HIGH_ACCURACY);
        caps.add(ModelCapability.LONG_CONTEXT);
        caps.add(ModelCapability.MULTI_TURN);
        ModelProfile profile = new ModelProfile("test", "test", caps, 4096, 1024, 0.1);
        assertTrue(profile.hasCapability(ModelCapability.HIGH_ACCURACY));
        assertTrue(profile.hasCapability(ModelCapability.LONG_CONTEXT));
        assertTrue(profile.hasCapability(ModelCapability.MULTI_TURN));
        assertFalse(profile.hasCapability(ModelCapability.FAST_RESPONSE));
    }

    @Test
    void testModelProfile_emptyCapabilities() {
        ModelProfile profile = new ModelProfile("test", "test",
            Collections.unmodifiableSet(new HashSet<ModelCapability>()), 4096, 1024, 0.1);
        assertFalse(profile.hasCapability(ModelCapability.FAST_RESPONSE));
        assertFalse(profile.hasCapability(ModelCapability.HIGH_ACCURACY));
    }

    // ========== A3 — 预设 Profile ==========

    @Test
    void testPreset_doubaoFlash() {
        ModelProfile profile = ModelProfile.of("doubao", "flash");
        assertEquals("doubao", profile.getProvider());
        assertEquals("flash", profile.getModel());
        assertTrue(profile.hasCapability(ModelCapability.FAST_RESPONSE));
        assertTrue(profile.hasCapability(ModelCapability.STABLE_JSON));
        assertFalse(profile.hasCapability(ModelCapability.HIGH_ACCURACY));
        assertEquals(32768, profile.getMaxContextTokens());
        assertEquals(4096, profile.getMaxOutputTokens());
    }

    @Test
    void testPreset_doubaoPro() {
        ModelProfile profile = ModelProfile.of("doubao", "pro");
        assertTrue(profile.hasCapability(ModelCapability.HIGH_ACCURACY));
        assertTrue(profile.hasCapability(ModelCapability.LONG_CONTEXT));
        assertTrue(profile.hasCapability(ModelCapability.MULTI_TURN));
        assertFalse(profile.hasCapability(ModelCapability.FAST_RESPONSE));
        assertEquals(131072, profile.getMaxContextTokens());
    }

    @Test
    void testPreset_openaiGpt4() {
        ModelProfile profile = ModelProfile.of("openai", "gpt-4");
        assertTrue(profile.hasCapability(ModelCapability.HIGH_ACCURACY));
        assertTrue(profile.hasCapability(ModelCapability.LONG_CONTEXT));
        assertTrue(profile.hasCapability(ModelCapability.STABLE_JSON));
        assertTrue(profile.hasCapability(ModelCapability.MULTI_TURN));
        assertEquals(131072, profile.getMaxContextTokens());
    }

    @Test
    void testPreset_qwenPlus() {
        ModelProfile profile = ModelProfile.of("qwen", "qwen-plus");
        assertTrue(profile.hasCapability(ModelCapability.FAST_RESPONSE));
        assertFalse(profile.hasCapability(ModelCapability.HIGH_ACCURACY));
        assertEquals(32768, profile.getMaxContextTokens());
    }

    @Test
    void testPreset_deepseekChat() {
        ModelProfile profile = ModelProfile.of("deepseek", "deepseek-chat");
        assertTrue(profile.hasCapability(ModelCapability.HIGH_ACCURACY));
        assertTrue(profile.hasCapability(ModelCapability.MULTI_TURN));
        assertFalse(profile.hasCapability(ModelCapability.FAST_RESPONSE));
        assertEquals(65536, profile.getMaxContextTokens());
    }

    @Test
    void testPreset_caseInsensitive() {
        ModelProfile profile = ModelProfile.of("Doubao", "Flash");
        assertEquals("doubao", profile.getProvider());
        assertEquals("flash", profile.getModel());
        assertTrue(profile.hasCapability(ModelCapability.FAST_RESPONSE));
    }

    // ========== A4 — Fallback（未知模型） ==========

    @Test
    void testFallback_unknownModel() {
        ModelProfile profile = ModelProfile.of("unknown", "model-x");
        assertNotNull(profile);
    }

    @Test
    void testFallback_defaultValues() {
        ModelProfile profile = ModelProfile.of("unknown", "model-x");
        assertNotNull(profile.getCapabilities());
        assertTrue(profile.getMaxContextTokens() > 0);
        assertTrue(profile.getMaxOutputTokens() > 0);
    }

    @Test
    void testFallback_unknownProvider() {
        ModelProfile profile = ModelProfile.of(null, "flash");
        assertNotNull(profile);
    }

    // ========== A5 — 边界条件 ==========

    @Test
    void testModelProfile_zeroTokens() {
        ModelProfile profile = new ModelProfile("test", "test",
            new HashSet<ModelCapability>(), 0, 0, 0.0);
        assertEquals(0, profile.getMaxContextTokens());
        assertEquals(0, profile.getMaxOutputTokens());
        assertFalse(profile.hasCapability(ModelCapability.STABLE_JSON));
    }

    @Test
    void testModelProfile_negativeTemperature() {
        ModelProfile profile = new ModelProfile("test", "test",
            new HashSet<ModelCapability>(), 4096, 1024, -0.1);
        assertEquals(-0.1, profile.getRecommendedTemperature(), 0.001);
    }

    @Test
    void testModelProfile_ofSameModelTwice() {
        ModelProfile p1 = ModelProfile.of("doubao", "flash");
        ModelProfile p2 = ModelProfile.of("doubao", "flash");
        assertEquals(p1.getMaxContextTokens(), p2.getMaxContextTokens());
        assertEquals(p1.getMaxOutputTokens(), p2.getMaxOutputTokens());
        assertEquals(p1.getProvider(), p2.getProvider());
    }
}
