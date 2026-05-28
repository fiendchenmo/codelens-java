package com.codelens.common.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MethodAnalysisValidatorTest {

    private final MethodAnalysisValidator validator = new MethodAnalysisValidator();

    @Test
    public void testValidate_ValidOutput() {
        String validJson = "{\n" +
                "  \"method\": \"processOrder\",\n" +
                "  \"l1Evidence\": {\n" +
                "    \"calls\": [\"validateOrder\", \"saveOrder\"],\n" +
                "    \"calledBy\": [\"handleRequest\"],\n" +
                "    \"fieldsUsed\": [\"orderRepository\"]\n" +
                "  },\n" +
                "  \"l2Confidence\": {\n" +
                "    \"overallScore\": 0.85,\n" +
                "    \"reasoningBasis\": \"SOLID_ANALYSIS\",\n" +
                "    \"riskIndicators\": [\"NULL_CHECK_MISSING\"]\n" +
                "  }\n" +
                "}";

        ValidationResult result = validator.validate(validJson, "processOrder");

        assertTrue(result.isValid(), "合法 JSON 应通过校验");
        assertNull(result.getErrorMessage());
        assertNull(result.getFieldName());
    }

    @Test
    public void testValidate_MethodMismatch() {
        String json = "{\n" +
                "  \"method\": \"otherMethod\",\n" +
                "  \"l1Evidence\": {\"calls\": [\"foo\"], \"calledBy\": [], \"fieldsUsed\": []},\n" +
                "  \"l2Confidence\": {\n" +
                "    \"overallScore\": 0.5,\n" +
                "    \"reasoningBasis\": \"HEURISTIC\",\n" +
                "    \"riskIndicators\": []\n" +
                "  }\n" +
                "}";

        ValidationResult result = validator.validate(json, "processOrder");

        assertFalse(result.isValid());
        assertEquals("method", result.getFieldName());
    }

    @Test
    public void testValidate_EmptyL1Evidence() {
        String json = "{\n" +
                "  \"method\": \"processOrder\",\n" +
                "  \"l1Evidence\": {\"calls\": [], \"calledBy\": [], \"fieldsUsed\": []},\n" +
                "  \"l2Confidence\": {\n" +
                "    \"overallScore\": 0.5,\n" +
                "    \"reasoningBasis\": \"HEURISTIC\",\n" +
                "    \"riskIndicators\": []\n" +
                "  }\n" +
                "}";

        ValidationResult result = validator.validate(json, "processOrder");

        assertFalse(result.isValid());
        assertEquals("l1Evidence", result.getFieldName());
    }

    @Test
    public void testValidate_InvalidOverallScoreRange() {
        String json = "{\n" +
                "  \"method\": \"processOrder\",\n" +
                "  \"l1Evidence\": {\"calls\": [\"foo\"], \"calledBy\": [], \"fieldsUsed\": []},\n" +
                "  \"l2Confidence\": {\n" +
                "    \"overallScore\": 1.5,\n" +
                "    \"reasoningBasis\": \"SOLID_ANALYSIS\",\n" +
                "    \"riskIndicators\": []\n" +
                "  }\n" +
                "}";

        ValidationResult result = validator.validate(json, "processOrder");

        assertFalse(result.isValid());
        assertEquals("overallScore", result.getFieldName());
    }

    @Test
    public void testValidate_InvalidReasoningBasis() {
        String json = "{\n" +
                "  \"method\": \"processOrder\",\n" +
                "  \"l1Evidence\": {\"calls\": [\"foo\"], \"calledBy\": [], \"fieldsUsed\": []},\n" +
                "  \"l2Confidence\": {\n" +
                "    \"overallScore\": 0.5,\n" +
                "    \"reasoningBasis\": \"INVALID_VALUE\",\n" +
                "    \"riskIndicators\": []\n" +
                "  }\n" +
                "}";

        ValidationResult result = validator.validate(json, "processOrder");

        assertFalse(result.isValid());
        assertEquals("reasoningBasis", result.getFieldName());
    }

    @Test
    public void testValidate_RiskIndicatorsNotArray() {
        String json = "{\n" +
                "  \"method\": \"processOrder\",\n" +
                "  \"l1Evidence\": {\"calls\": [\"foo\"], \"calledBy\": [], \"fieldsUsed\": []},\n" +
                "  \"l2Confidence\": {\n" +
                "    \"overallScore\": 0.5,\n" +
                "    \"reasoningBasis\": \"HEURISTIC\",\n" +
                "    \"riskIndicators\": \"NONE\"\n" +
                "  }\n" +
                "}";

        ValidationResult result = validator.validate(json, "processOrder");

        assertFalse(result.isValid());
        assertEquals("riskIndicators", result.getFieldName());
    }
}
