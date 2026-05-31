package com.codelens.common.agent;

import com.codelens.common.agent.AggregateSummaryInput.FileSummaryEntry;
import com.codelens.common.models.ArchitectureLayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GroupingPrompt} 分组 Prompt 渲染测试。
 */
class GroupingPromptTest {

    private GroupingPrompt prompt;

    @BeforeEach
    void setUp() {
        prompt = new GroupingPrompt();
    }

    @Test
    void buildSystemPrompt_containsRequiredFields() {
        String systemPrompt = prompt.buildSystemPrompt();
        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("group"));
        assertTrue(systemPrompt.contains("files"));
        assertTrue(systemPrompt.contains("JSON"));
    }

    @Test
    void buildUserPrompt_substitutesEntries() {
        List<FileSummaryEntry> entries = createSampleEntries();
        String userPrompt = prompt.buildUserPrompt(entries);
        assertNotNull(userPrompt);
        assertTrue(userPrompt.contains("OrderService.java"));
        assertTrue(userPrompt.contains("PaymentService.java"));
        assertTrue(userPrompt.contains("SERVICE"));
    }

    @Test
    void buildUserPrompt_nullEntries() {
        String userPrompt = prompt.buildUserPrompt(null);
        assertNotNull(userPrompt);
        assertFalse(userPrompt.contains("null"));
    }

    @Test
    void buildUserPrompt_emptyList() {
        String userPrompt = prompt.buildUserPrompt(new ArrayList<FileSummaryEntry>());
        assertNotNull(userPrompt);
    }

    @Test
    void buildGroupingPrompt_containsBothSystemAndUser() {
        List<FileSummaryEntry> entries = createSampleEntries();
        String full = prompt.buildGroupingPrompt(entries);
        assertNotNull(full);
        assertTrue(full.contains("你是一位 Java 代码分析专家"));
        assertTrue(full.contains("OrderService.java"));
        assertTrue(full.contains("\n\n"));
    }

    private static List<FileSummaryEntry> createSampleEntries() {
        FileSummaryEntry e1 = new FileSummaryEntry(
                "OrderService.java", ArchitectureLayer.SERVICE,
                "订单服务", "Spring", "处理订单", "低风险",
                Arrays.asList("createOrder"), new ArrayList<String>());

        FileSummaryEntry e2 = new FileSummaryEntry(
                "PaymentService.java", ArchitectureLayer.SERVICE,
                "支付服务", "Spring", "处理支付", "中风险",
                Arrays.asList("pay"), new ArrayList<String>());

        return Arrays.asList(e1, e2);
    }
}
