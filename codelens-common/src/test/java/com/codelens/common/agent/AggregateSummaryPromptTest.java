package com.codelens.common.agent;

import com.codelens.common.agent.AggregateSummaryInput.FileSummaryEntry;
import com.codelens.common.agent.AggregateSummaryInput.CrossPackageDep;
import com.codelens.common.models.ArchitectureLayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AggregateSummaryPrompt} 模板渲染测试。
 */
class AggregateSummaryPromptTest {

    private AggregateSummaryPrompt prompt;

    @BeforeEach
    void setUp() {
        prompt = new AggregateSummaryPrompt();
    }

    // ========================================================================
    // 包级 Prompt
    // ========================================================================

    @Test
    void buildPackageSystemPrompt_containsRequiredFields() {
        String systemPrompt = prompt.buildPackageSystemPrompt();
        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("packageName"));
        assertTrue(systemPrompt.contains("summary"));
        assertTrue(systemPrompt.contains("coreResponsibilities"));
        assertTrue(systemPrompt.contains("crossPackageDeps"));
        assertTrue(systemPrompt.contains("riskOverview"));
        assertTrue(systemPrompt.contains("riskCategories"));
        assertTrue(systemPrompt.contains("fileLayers"));
        assertTrue(systemPrompt.contains("refactorOverview"));
        assertTrue(systemPrompt.contains("responsibilities"));
        assertTrue(systemPrompt.contains("classEntries"));
        assertTrue(systemPrompt.contains("200 字"));
        assertTrue(systemPrompt.contains("2000"));
    }

    @Test
    void buildPackageUserPrompt_substitutesVariables() {
        AggregateSummaryInput input = createSampleInput();
        String userPrompt = prompt.buildPackageUserPrompt(input);
        assertNotNull(userPrompt);
        assertTrue(userPrompt.contains("com.example.service"));
        assertTrue(userPrompt.contains("OrderService"));
        assertTrue(userPrompt.contains("SERVICE"));
    }

    @Test
    void buildPackageUserPrompt_nullInput() {
        String userPrompt = prompt.buildPackageUserPrompt(null);
        assertNotNull(userPrompt);
        assertFalse(userPrompt.contains("null"));
    }

    @Test
    void buildPackageUserPrompt_missingFields() {
        AggregateSummaryInput empty = new AggregateSummaryInput();
        String userPrompt = prompt.buildPackageUserPrompt(empty);
        assertNotNull(userPrompt);
        // 不会抛 NPE
    }

    @Test
    void buildPackagePrompt_containsBothSystemAndUser() {
        AggregateSummaryInput input = createSampleInput();
        String full = prompt.buildPackagePrompt(input);
        assertNotNull(full);
        assertTrue(full.contains("你是一位 Java 架构分析专家"));
        assertTrue(full.contains("com.example.service"));
        // System 和 User 之间应该有换行分隔
        assertTrue(full.contains("\n\n"));
    }

    // ========================================================================
    // 模块级 Prompt
    // ========================================================================

    @Test
    void buildModuleSystemPrompt_containsRequiredFields() {
        String systemPrompt = prompt.buildModuleSystemPrompt();
        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("moduleName"));
        assertTrue(systemPrompt.contains("summary"));
        assertTrue(systemPrompt.contains("corePackages"));
        assertTrue(systemPrompt.contains("architectureOverview"));
        assertTrue(systemPrompt.contains("crossModuleDeps"));
        assertTrue(systemPrompt.contains("riskOverview"));
        assertTrue(systemPrompt.contains("highRiskPackageCount"));
        assertTrue(systemPrompt.contains("mediumRiskPackageCount"));
        assertTrue(systemPrompt.contains("300 字"));
        assertTrue(systemPrompt.contains("1000"));
    }

    @Test
    void buildModuleUserPrompt_substitutesVariables() {
        AggregateSummaryOutput pkg1 = new AggregateSummaryOutput();
        pkg1.setPackageName("com.example.service");
        pkg1.setSummary("服务层");

        List<AggregateSummaryOutput> summaries = new ArrayList<>();
        summaries.add(pkg1);

        String userPrompt = prompt.buildModuleUserPrompt("order-module", summaries);
        assertNotNull(userPrompt);
        assertTrue(userPrompt.contains("order-module"));
        assertTrue(userPrompt.contains("com.example.service"));
    }

    @Test
    void buildModuleUserPrompt_nullPackageSummaries() {
        String userPrompt = prompt.buildModuleUserPrompt("test-module", null);
        assertNotNull(userPrompt);
        assertTrue(userPrompt.contains("test-module"));
    }

    @Test
    void buildModuleUserPrompt_emptyList() {
        String userPrompt = prompt.buildModuleUserPrompt("empty-module",
                new ArrayList<AggregateSummaryOutput>());
        assertNotNull(userPrompt);
        assertTrue(userPrompt.contains("empty-module"));
    }

    @Test
    void buildModulePrompt_containsBothSystemAndUser() {
        AggregateSummaryOutput pkg = new AggregateSummaryOutput();
        pkg.setPackageName("com.example.service");
        List<AggregateSummaryOutput> summaries = Collections.singletonList(pkg);

        String full = prompt.buildModulePrompt("order-module", summaries);
        assertNotNull(full);
        assertTrue(full.contains("你是一位 Java 代码架构分析专家"));
        assertTrue(full.contains("order-module"));
        assertTrue(full.contains("com.example.service"));
        assertTrue(full.contains("\n\n"));
    }

    private static AggregateSummaryInput createSampleInput() {
        FileSummaryEntry entry = new FileSummaryEntry(
                "OrderService.java", ArchitectureLayer.SERVICE,
                "订单核心服务", "Spring Boot", "基于策略模式", "低风险",
                Arrays.asList("createOrder"), Arrays.asList("OrderController"));

        CrossPackageDep dep = new CrossPackageDep(
                "com.example.repository", Arrays.asList("orderMapper.insert"), "outgoing");

        Map<ArchitectureLayer, Integer> dist = new HashMap<>();
        dist.put(ArchitectureLayer.SERVICE, 3);
        dist.put(ArchitectureLayer.CONTROLLER, 1);

        return new AggregateSummaryInput(
                "com.example.service",
                Collections.singletonList(entry),
                Collections.singletonList(dep),
                dist);
    }
}
