package com.codelens.common.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SummaryPromptTest {

    @Test
    public void testGenerateSystemPrompt_ContainsJsonSchema() {
        SummaryPrompt prompt = new SummaryPrompt();
        String systemPrompt = prompt.generateSystemPrompt();

        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("className"), "应包含 className 字段说明");
        assertTrue(systemPrompt.contains("stereotype"), "应包含 stereotype 字段说明");
        assertTrue(systemPrompt.contains("summary"), "应包含 summary 字段说明");
        assertTrue(systemPrompt.contains("frameworkDesc"), "应包含 frameworkDesc 字段说明");
        assertTrue(systemPrompt.contains("fields"), "应包含 fields 字段说明");
        assertTrue(systemPrompt.contains("keyMethods"), "应包含 keyMethods 字段说明");
        assertTrue(systemPrompt.contains("dependencies"), "应包含 dependencies 字段说明");
        assertTrue(systemPrompt.contains("complexity"), "应包含 complexity 字段说明");
        assertTrue(systemPrompt.contains("800 token"), "应包含 token 限制说明");
    }

    @Test
    public void testGenerateUserPrompt_SubstitutesVariables() {
        SummaryPrompt prompt = new SummaryPrompt();
        String sourceCode = "public class TestService { }";
        String metadata = "Classes: [TestService], Methods: []";

        String userPrompt = prompt.generateUserPrompt(sourceCode, metadata);

        assertNotNull(userPrompt);
        assertTrue(userPrompt.contains(sourceCode), "user prompt 应包含源码");
        assertTrue(userPrompt.contains(metadata), "user prompt 应包含元数据");
    }
}
