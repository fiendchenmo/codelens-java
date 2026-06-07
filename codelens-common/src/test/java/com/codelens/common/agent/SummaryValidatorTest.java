package com.codelens.common.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SummaryValidatorTest {

    private final SummaryValidator validator = new SummaryValidator();

    @Test
    public void testValidate_ValidOutput() {
        String validJson = "{\n" +
                "  \"className\": \"com.example.OrderService\",\n" +
                "  \"stereotype\": \"SERVICE\",\n" +
                "  \"summary\": \"订单处理服务，负责订单的创建和支付\",\n" +
                "  \"frameworkDesc\": \"Spring Service\",\n" +
                "  \"fields\": [\n" +
                "    {\"name\": \"orderRepo\", \"type\": \"OrderRepository\", \"injectType\": \"AUTOWIRED\", \"description\": \"订单仓库\", \"line\": 25}\n" +
                "  ],\n" +
                "  \"keyMethods\": [\n" +
                "    {\"name\": \"processOrder\", \"role\": \"核心业务入口\", \"complexity\": 8}\n" +
                "  ],\n" +
                "  \"dependencies\": [\"OrderRepository\"],\n" +
                "  \"complexity\": \"MEDIUM\"\n" +
                "}";

        ValidationResult result = validator.validate(validJson);

        assertTrue(result.isValid(), "合法 JSON 应通过校验");
        assertNull(result.getErrorMessage());
        assertNull(result.getFieldName());
    }

    @Test
    public void testValidate_MissingClassName() {
        String json = "{\"stereotype\": \"SERVICE\", \"keyMethods\": [], " +
                "\"dependencies\": [], \"complexity\": \"LOW\"}";

        ValidationResult result = validator.validate(json);

        assertFalse(result.isValid());
        assertEquals("className", result.getFieldName());
    }

    @Test
    public void testValidate_EmptyKeyMethods() {
        String json = "{\n" +
                "  \"className\": \"com.example.Test\",\n" +
                "  \"stereotype\": \"DTO\",\n" +
                "  \"keyMethods\": [],\n" +
                "  \"dependencies\": [],\n" +
                "  \"complexity\": \"LOW\"\n" +
                "}";

        ValidationResult result = validator.validate(json);

        assertFalse(result.isValid());
        assertEquals("keyMethods", result.getFieldName());
    }

    @Test
    public void testValidate_ExceedsTokenLimit() {
        // 构造一个超长输出（> 550 空格分词）
        StringBuilder sb = new StringBuilder("{\"className\": \"c\", \"stereotype\": \"S\", ");
        // 用大量空格分词来触发 token 估算超限
        sb.append("\"keyMethods\": [{\"name\": \"m\", \"role\": \"r\", \"complexity\": 1}], ");
        sb.append("\"dependencies\": [], \"complexity\": \"LOW\"");
        // 追加超过 550 个词
        for (int i = 0; i < 600; i++) {
            sb.append(" \"word" + i + "\"");
        }
        sb.append("}");

        String longJson = sb.toString();
        ValidationResult result = validator.validate(longJson);

        assertFalse(result.isValid());
        assertEquals("root", result.getFieldName());
    }

    @Test
    public void testValidate_InvalidJson() {
        String invalidJson = "this is not json";

        ValidationResult result = validator.validate(invalidJson);

        assertFalse(result.isValid());
        assertEquals("root", result.getFieldName());
    }

    @Test
    public void testValidate_MissingDependenciesField() {
        String json = "{\n" +
                "  \"className\": \"com.example.Test\",\n" +
                "  \"stereotype\": \"SERVICE\",\n" +
                "  \"keyMethods\": [{\"name\": \"m\", \"role\": \"r\", \"complexity\": 1}],\n" +
                "  \"complexity\": \"MEDIUM\"\n" +
                "}";

        ValidationResult result = validator.validate(json);

        assertFalse(result.isValid());
        assertEquals("dependencies", result.getFieldName());
    }
}
