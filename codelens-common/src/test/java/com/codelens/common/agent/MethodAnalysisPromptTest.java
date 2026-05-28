package com.codelens.common.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MethodAnalysisPromptTest {

    @Test
    public void testGenerateSystemPrompt_ContainsJsonSchema() {
        MethodAnalysisPrompt prompt = new MethodAnalysisPrompt();
        String systemPrompt = prompt.generateSystemPrompt();

        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("method"), "应包含 method 字段说明");
        assertTrue(systemPrompt.contains("l1Evidence"), "应包含 l1Evidence 字段说明");
        assertTrue(systemPrompt.contains("l2Confidence"), "应包含 l2Confidence 字段说明");
        assertTrue(systemPrompt.contains("overallScore"), "应包含 overallScore 字段说明");
        assertTrue(systemPrompt.contains("reasoningBasis"), "应包含 reasoningBasis 字段说明");
        assertTrue(systemPrompt.contains("riskIndicators"), "应包含 riskIndicators 字段说明");
    }

    @Test
    public void testGenerateUserPrompt_SubstitutesVariables() {
        MethodAnalysisPrompt prompt = new MethodAnalysisPrompt();
        String methodSignature = "public void processOrder(Long orderId)";
        String methodSourceCode = "if (orderId == null) throw new IllegalArgumentException();";
        String fileSummary = "Class: OrderService, Stereotype: SERVICE";
        String metadata = "Methods: [processOrder], Fields: [orderId]";

        String userPrompt = prompt.generateUserPrompt(methodSignature, methodSourceCode, fileSummary, metadata);

        assertNotNull(userPrompt);
        assertTrue(userPrompt.contains(methodSignature), "user prompt 应包含方法签名");
        assertTrue(userPrompt.contains(methodSourceCode), "user prompt 应包含方法源码");
        assertTrue(userPrompt.contains(fileSummary), "user prompt 应包含文件摘要");
        assertTrue(userPrompt.contains(metadata), "user prompt 应包含元数据");
    }
}
