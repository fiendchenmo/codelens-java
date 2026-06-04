package com.codelens.common.agent;

import com.codelens.common.validators.EvidenceValidator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ValidationPostProcessor 单元测试。
 * <p>
 * 覆盖：正常校验 / 校验失败 / sourceCode=null / 非法 JSON / 空声明 / 辅助方法。
 * </p>
 */
public class ValidationPostProcessorTest {

    private static final String VALID_METHOD_JSON =
            "{\n" +
            "  \"method\": \"processOrder\",\n" +
            "  \"l1Evidence\": {\n" +
            "    \"calls\": [\n" +
            "      {\"target\": \"validateOrder\", \"line\": 4},\n" +
            "      {\"target\": \"saveOrder\", \"line\": 5}\n" +
            "    ],\n" +
            "    \"calledBy\": [\"handleRequest\"],\n" +
            "    \"fieldsUsed\": [{\"target\": \"orderRepository\", \"line\": 2}]\n" +
            "  },\n" +
            "  \"l2Confidence\": {\n" +
            "    \"overallScore\": 0.95,\n" +
            "    \"reasoningBasis\": \"SOLID_ANALYSIS\",\n" +
            "    \"riskIndicators\": [\"complex logic flow\"]\n" +
            "  }\n" +
            "}";

    private static final String VALID_SOURCE =
            "public class OrderService {\n" +
            "  private OrderRepository orderRepository;\n" +
            "  public void processOrder() {\n" +
            "    validateOrder();\n" +
            "    saveOrder();\n" +
            "  }\n" +
            "  private void validateOrder() {}\n" +
            "  private void saveOrder() {}\n" +
            "}";

    // ==================== TC-01: 正常校验，所有声明在源码中找到 ====================

    @Test
    void testNormalValidation_AllClaimsFound() {
        String enriched = ValidationPostProcessor.process(VALID_METHOD_JSON, VALID_SOURCE);
        JsonObject result = JsonParser.parseString(enriched).getAsJsonObject();
        JsonObject l2 = result.getAsJsonObject("l2Confidence");

        // validateOrder (line 4), saveOrder (line 5), orderRepository (line 2) — all pass
        double score = l2.get("overallScore").getAsDouble();
        String basis = l2.get("reasoningBasis").getAsString();

        assertEquals(1.0, score, 0.001, "All claims found → CERTAIN → 1.0");
        assertEquals("SOLID_ANALYSIS", basis);
    }

    // ==================== TC-02: 校验失败，声明在源码中未找到 ====================

    @Test
    void testValidation_ClaimsNotFound_LowScore() {
        // 字符串格式的 nonExistentMethod，无 LLM line → 不加入 deps
        String methodJson =
                "{\n" +
                "  \"method\": \"processOrder\",\n" +
                "  \"l1Evidence\": {\n" +
                "    \"calls\": [\"nonExistentMethod\"],\n" +
                "    \"calledBy\": [\"handleRequest\"],\n" +
                "    \"fieldsUsed\": []\n" +
                "  },\n" +
                "  \"l2Confidence\": {\n" +
                "    \"overallScore\": 0.95,\n" +
                "    \"reasoningBasis\": \"SOLID_ANALYSIS\",\n" +
                "    \"riskIndicators\": []\n" +
                "  }\n" +
                "}";

        String enriched = ValidationPostProcessor.process(methodJson, VALID_SOURCE);
        JsonObject result = JsonParser.parseString(enriched).getAsJsonObject();
        JsonObject l2 = result.getAsJsonObject("l2Confidence");

        // nonExistentMethod 字符串格式 → line=0 → 越界失败 → 0/1 → LOW
        double score = l2.get("overallScore").getAsDouble();
        String basis = l2.get("reasoningBasis").getAsString();
        assertEquals(0.2, score, 0.001, "String claims without line → line=0 → LOW → 0.2");
        assertEquals("PARTIAL", basis);
    }

    // ==================== TC-03: sourceCode = null → 返回原始 JSON ====================

    @Test
    void testSourceCodeNull_ReturnsOriginal() {
        String enriched = ValidationPostProcessor.process(VALID_METHOD_JSON, null);
        assertEquals(VALID_METHOD_JSON, enriched, "sourceCode=null 应返回原始 JSON");
    }

    // ==================== TC-04: 非法 JSON → 返回原始字符串 ====================

    @Test
    void testInvalidMethodJson_ReturnsOriginal() {
        String enriched = ValidationPostProcessor.process("{invalid json", VALID_SOURCE);
        assertEquals("{invalid json", enriched, "非法 JSON 应返回原始字符串");
    }

    // ==================== TC-05: 空声明 → UNKNOWN ====================

    @Test
    void testEmptyClaims_ScoreUnknown() {
        String emptyClaims =
                "{\n" +
                "  \"method\": \"emptyMethod\",\n" +
                "  \"l1Evidence\": {\"calls\": [], \"calledBy\": [], \"fieldsUsed\": []},\n" +
                "  \"l2Confidence\": {\n" +
                "    \"overallScore\": 0.5,\n" +
                "    \"reasoningBasis\": \"HEURISTIC\",\n" +
                "    \"riskIndicators\": []\n" +
                "  }\n" +
                "}";

        String enriched = ValidationPostProcessor.process(emptyClaims, VALID_SOURCE);
        JsonObject result = JsonParser.parseString(enriched).getAsJsonObject();
        JsonObject l2 = result.getAsJsonObject("l2Confidence");

        // empty calls+fieldsUsed+riskIndicators → totalChecked=0 → UNKNOWN
        assertEquals(0.0, l2.get("overallScore").getAsDouble(), 0.001, "空声明应映射为 UNKNOWN → 0.0");
        assertEquals("UNKNOWN", l2.get("reasoningBasis").getAsString());
    }

    // ==================== TC-06: findLineInSource 找到 ====================

    @Test
    void testFindLineInSource_Found() {
        String[] lines = {"line0", "public void validateOrder()", "line2"};
        assertEquals(2, ValidationPostProcessor.findLineInSource("validateOrder", lines));
    }

    // ==================== TC-07: findLineInSource 未找到 ====================

    @Test
    void testFindLineInSource_NotFound() {
        String[] lines = {"line0", "line1", "line2"};
        assertEquals(0, ValidationPostProcessor.findLineInSource("nonexistent", lines));
    }

    // ==================== TC-08: toScore 映射 ====================

    @Test
    void testConfidenceMapping() {
        assertEquals(1.0, ValidationPostProcessor.toScore(EvidenceValidator.Confidence.CERTAIN), 0.001);
        assertEquals(0.8, ValidationPostProcessor.toScore(EvidenceValidator.Confidence.HIGH), 0.001);
        assertEquals(0.5, ValidationPostProcessor.toScore(EvidenceValidator.Confidence.MEDIUM), 0.001);
        assertEquals(0.2, ValidationPostProcessor.toScore(EvidenceValidator.Confidence.LOW), 0.001);
        assertEquals(0.0, ValidationPostProcessor.toScore(EvidenceValidator.Confidence.UNKNOWN), 0.001);
    }

    // ==================== TC-09: toBasis 映射 ====================

    @Test
    void testBasisMapping() {
        assertEquals("SOLID_ANALYSIS", ValidationPostProcessor.toBasis(EvidenceValidator.Confidence.CERTAIN));
        assertEquals("SOLID_ANALYSIS", ValidationPostProcessor.toBasis(EvidenceValidator.Confidence.HIGH));
        assertEquals("HEURISTIC", ValidationPostProcessor.toBasis(EvidenceValidator.Confidence.MEDIUM));
        assertEquals("PARTIAL", ValidationPostProcessor.toBasis(EvidenceValidator.Confidence.LOW));
        assertEquals("UNKNOWN", ValidationPostProcessor.toBasis(EvidenceValidator.Confidence.UNKNOWN));
    }
}
