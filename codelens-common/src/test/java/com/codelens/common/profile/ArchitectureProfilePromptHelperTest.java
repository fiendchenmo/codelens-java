package com.codelens.common.profile;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ArchitectureProfilePromptHelper 单元测试。
 * <p>
 * 覆盖：null profile、空 profile、完整 profile。
 */
public class ArchitectureProfilePromptHelperTest {

    @Test
    void nullProfileReturnsEmpty() {
        assertEquals("", ArchitectureProfilePromptHelper.generateContext(null));
    }

    @Test
    void emptyProfileReturnsEmpty() {
        ArchitectureProfile profile = new ArchitectureProfile();
        assertEquals("", ArchitectureProfilePromptHelper.generateContext(profile));
    }

    @Test
    void profileWithOnlyPattern() {
        ArchitectureProfile profile = new ArchitectureProfile();
        profile.setArchitecturePattern(ArchitecturePattern.LAYERED);
        profile.setConfidence(ArchitecturePattern.Confidence.HIGH);

        String result = ArchitectureProfilePromptHelper.generateContext(profile);
        assertTrue(result.contains("架构模式: LAYERED"));
        assertTrue(result.contains("置信度: HIGH"));
        // 无跨切关注点和分层规则，section 6 不应出现
        assertFalse(result.contains("分析时请注意"));
    }

    @Test
    void profileWithLayerDistributionOrderedByCount() {
        ArchitectureProfile profile = new ArchitectureProfile();
        profile.setArchitecturePattern(ArchitecturePattern.LAYERED);

        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("CONTROLLER", 5);
        distribution.put("SERVICE", 20);
        distribution.put("REPOSITORY", 15);
        distribution.put("CONFIG", 3);
        distribution.put("CLIENT", 8);
        distribution.put("UNKNOWN", 2);
        profile.setLayerDistribution(distribution);

        String result = ArchitectureProfilePromptHelper.generateContext(profile);
        // 应只输出 top 5（按类数降序）
        assertTrue(result.contains("架构层分布:"));
        // SERVICE(20) 应在 REPOSITORY(15) 之前
        int serviceIdx = result.indexOf("SERVICE");
        int repoIdx = result.indexOf("REPOSITORY");
        assertTrue(serviceIdx >= 0 && repoIdx >= 0);
        assertTrue(serviceIdx < repoIdx, "SERVICE(20) 应在 REPOSITORY(15) 之前");
        // UNKNOWN(2) 是第 6 名，不应出现
        assertFalse(result.contains("UNKNOWN"));
    }

    @Test
    void section6NotOutputWhenNoConcernsNorRules() {
        ArchitectureProfile profile = new ArchitectureProfile();
        profile.setArchitecturePattern(ArchitecturePattern.LAYERED);

        // 有 distribution，但没有跨切关注点和分层规则
        Map<String, Integer> distribution = new HashMap<>();
        distribution.put("CONTROLLER", 5);
        distribution.put("SERVICE", 10);
        profile.setLayerDistribution(distribution);

        String result = ArchitectureProfilePromptHelper.generateContext(profile);
        assertTrue(result.contains("架构层分布:"));
        assertFalse(result.contains("分析时请注意"));
    }

    @Test
    void section6OutputWithExceptionHandling() {
        ArchitectureProfile profile = new ArchitectureProfile();
        profile.setArchitecturePattern(ArchitecturePattern.LAYERED);

        List<CrossCuttingConcern> concerns = new ArrayList<>();
        concerns.add(new CrossCuttingConcern("EXCEPTION_HANDLING", "@ControllerAdvice → GlobalHandler", "GlobalHandler", 1.0));
        profile.setCrossCuttingConcerns(concerns);

        String result = ArchitectureProfilePromptHelper.generateContext(profile);
        assertTrue(result.contains("分析时请注意"));
        assertTrue(result.contains("全局异常处理机制"));
        assertTrue(result.contains("不要因为方法内缺少 try-catch"));
    }

    @Test
    void section6OutputWithSecurity() {
        ArchitectureProfile profile = new ArchitectureProfile();
        profile.setArchitecturePattern(ArchitecturePattern.LAYERED);

        List<CrossCuttingConcern> concerns = new ArrayList<>();
        concerns.add(new CrossCuttingConcern("SECURITY", "Spring Security", "SecurityConfig", 0.8));
        profile.setCrossCuttingConcerns(concerns);

        String result = ArchitectureProfilePromptHelper.generateContext(profile);
        assertTrue(result.contains("分析时请注意"));
        assertTrue(result.contains("安全框架"));
        assertTrue(result.contains("不要因为方法缺少权限检查"));
    }

    @Test
    void section6OutputWithLayerRules() {
        ArchitectureProfile profile = new ArchitectureProfile();
        profile.setArchitecturePattern(ArchitecturePattern.LAYERED);

        Map<String, List<String>> layerRules = new HashMap<>();
        layerRules.put("CONTROLLER", Arrays.asList("SERVICE"));
        layerRules.put("SERVICE", Arrays.asList("REPOSITORY"));
        profile.setLayerRules(layerRules);

        String result = ArchitectureProfilePromptHelper.generateContext(profile);
        // 分层约定 section
        assertTrue(result.contains("分层约定:"));
        assertTrue(result.contains("CONTROLLER → SERVICE"));
        // section 6
        assertTrue(result.contains("分析时请注意"));
        assertTrue(result.contains("跨层调用如果违反上述分层约定"));
    }

    @Test
    void fullProfileAllSections() {
        ArchitectureProfile profile = new ArchitectureProfile();
        profile.setArchitecturePattern(ArchitecturePattern.LAYERED);
        profile.setConfidence(ArchitecturePattern.Confidence.HIGH);

        // 层分布
        Map<String, Integer> distribution = new HashMap<>();
        distribution.put("CONTROLLER", 8);
        distribution.put("SERVICE", 25);
        distribution.put("REPOSITORY", 12);
        profile.setLayerDistribution(distribution);

        // 分层规则
        Map<String, List<String>> layerRules = new HashMap<>();
        layerRules.put("CONTROLLER", Arrays.asList("SERVICE"));
        layerRules.put("SERVICE", Arrays.asList("REPOSITORY"));
        profile.setLayerRules(layerRules);

        // 约束
        List<Constraint> constraints = new ArrayList<>();
        constraints.add(new Constraint("LAYER_VIOLATION", "Controller不应直接调用Repository", "ERROR", "标准分层架构"));
        constraints.add(new Constraint("MISSING_PRACTICE", "建议使用DTO", "WARN", "分层架构中不应暴露实体"));
        profile.setConstraints(constraints);

        // 跨切关注点
        List<CrossCuttingConcern> concerns = new ArrayList<>();
        concerns.add(new CrossCuttingConcern("EXCEPTION_HANDLING", "@ControllerAdvice → GlobalHandler", "GlobalHandler", 1.0));
        concerns.add(new CrossCuttingConcern("TRANSACTION", "@Transactional", "SERVICE layer", 0.8));
        profile.setCrossCuttingConcerns(concerns);

        String result = ArchitectureProfilePromptHelper.generateContext(profile);

        // 验证各段
        assertTrue(result.contains("架构模式: LAYERED"));
        assertTrue(result.contains("置信度: HIGH"));
        assertTrue(result.contains("架构层分布:"));
        assertTrue(result.contains("SERVICE"));
        assertTrue(result.contains("分层约定:"));
        assertTrue(result.contains("架构约束:"));
        assertTrue(result.contains("Controller不应直接调用Repository"));
        assertTrue(result.contains("已识别的跨切关注点:"));
        assertTrue(result.contains("EXCEPTION_HANDLING"));
        assertTrue(result.contains("TRANSACTION"));

        // section 6
        assertTrue(result.contains("分析时请注意"));
        assertTrue(result.contains("全局异常处理机制"));
        assertTrue(result.contains("跨层调用如果违反上述分层约定"));
        // 没有 SECURITY，所以不应有安全相关指导
        assertFalse(result.contains("安全框架"));
    }
}
