package com.codelens.common.agent;

import com.codelens.common.agent.AggregateSummaryInput.FileSummaryEntry;
import com.codelens.common.models.ArchitectureLayer;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AggregateSummaryValidator} 9 条校验规则测试。
 */
class AggregateSummaryValidatorTest {

    // ========================================================================
    // V1: packageName 非空且匹配 input
    // ========================================================================

    @Test
    void v1_packageName_empty() {
        AggregateSummaryInput input = inputWithPackage("com.example.service");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        String json = jsonWithOverrides("packageName", "");
        ValidationResult result = validator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("packageName"));
    }

    @Test
    void v1_packageName_mismatch() {
        AggregateSummaryInput input = inputWithPackage("com.example.service");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        String json = jsonWithOverrides("packageName", "com.example.other");
        ValidationResult result = validator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("不匹配"));
    }

    @Test
    void v1_packageName_valid() {
        AggregateSummaryInput input = inputWithPackage("com.example.service");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        String json = jsonWithOverrides("packageName", "com.example.service");
        ValidationResult result = validator.validate(json);
        assertTrue(result.isValid());
    }

    // ========================================================================
    // V2: architectureLayer 在枚举范围
    // ========================================================================

    @Test
    void v2_architectureLayer_invalid() {
        AggregateSummaryInput input = inputWithPackage("com.example.test");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        String json = jsonWithOverrides("architectureLayer", "\"INVALID_LAYER\"");
        ValidationResult result = validator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("architectureLayer"));
    }

    @Test
    void v2_architectureLayer_null() {
        AggregateSummaryInput input = inputWithPackage("com.example.test");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        String json = jsonWithOverrides("architectureLayer", "null");
        ValidationResult result = validator.validate(json);
        assertFalse(result.isValid());
    }

    @Test
    void v2_architectureLayer_valid() {
        AggregateSummaryInput input = inputWithPackage("com.example.test");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        String json = jsonWithOverrides("architectureLayer", "\"SERVICE\"");
        ValidationResult result = validator.validate(json);
        assertTrue(result.isValid());
    }

    // ========================================================================
    // V3: summary 非空且 ≤200 字（WARN 截断）
    // ========================================================================

    @Test
    void v3_summary_empty() {
        AggregateSummaryInput input = inputWithPackage("com.example.test");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        String json = jsonWithOverrides("summary", "\"\"");
        // validate 内部调用 validateSummary，应自动补默认值
        ValidationResult result = validator.validate(json);
        assertTrue(result.isValid());
    }

    @Test
    void v3_summary_truncated() {
        AggregateSummaryInput input = inputWithPackage("com.example.test");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("超长摘要测试内容");
        }
        String longSummary = sb.toString();
        assertTrue(longSummary.length() > 200);

        String json = jsonWithOverrides("summary", "\"" + escapeJson(longSummary) + "\"");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        ValidationResult result = validator.validate(json);
        assertTrue(result.isValid());
    }

    @Test
    void v3_summary_valid() {
        AggregateSummaryInput input = inputWithPackage("com.example.test");
        String json = jsonWithOverrides("summary", "\"正常摘要内容\"");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        ValidationResult result = validator.validate(json);
        assertTrue(result.isValid());
    }

    // ========================================================================
    // V4: coreEntries ≤5（WARN 截断）
    // ========================================================================

    @Test
    void v4_coreEntries_truncated() {
        AggregateSummaryInput input = inputWithPackage("com.example.test");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        String entries = "[\"A\",\"B\",\"C\",\"D\",\"E\",\"F\"]";
        String json = jsonWithOverrides("coreEntries", entries);
        ValidationResult result = validator.validate(json);
        assertTrue(result.isValid());
    }

    // ========================================================================
    // V5: coreResponsibilities ≤5（WARN 截断）
    // ========================================================================

    @Test
    void v5_coreResponsibilities_truncated() {
        AggregateSummaryInput input = inputWithPackage("com.example.test");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        String resp = "[\"A\",\"B\",\"C\",\"D\",\"E\",\"F\",\"G\"]";
        String json = jsonWithOverrides("coreResponsibilities", resp);
        ValidationResult result = validator.validate(json);
        assertTrue(result.isValid());
    }

    // ========================================================================
    // V6: riskOverview 非空（有风险时）（WARN 补模板）
    // ========================================================================

    @Test
    void v6_riskOverview_emptyWithRisks() {
        AggregateSummaryInput input = inputWithPackage("com.example.test");
        // highRiskCount > 0, riskOverview 为空
        String json = jsonWithOverrides("highRiskCount", "2")
                .replace("\"riskOverview\":\"\"", "\"riskOverview\":\"\"");
        // 注：原始基础 json 里 riskOverview 是空字符串
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        ValidationResult result = validator.validate(json);
        assertTrue(result.isValid());
    }

    // ========================================================================
    // V7: totalFiles == input.fileSummaries.size()
    // ========================================================================

    @Test
    void v7_totalFiles_mismatch() {
        // input 有 2 个文件
        List<FileSummaryEntry> files = new ArrayList<>();
        files.add(new FileSummaryEntry("A.java", ArchitectureLayer.SERVICE, "a", "", "", "",
                new ArrayList<String>(), new ArrayList<String>()));
        files.add(new FileSummaryEntry("B.java", ArchitectureLayer.SERVICE, "b", "", "", "",
                new ArrayList<String>(), new ArrayList<String>()));
        AggregateSummaryInput input = new AggregateSummaryInput("com.example.test", files,
                new ArrayList<AggregateSummaryInput.CrossPackageDep>(),
                new HashMap<ArchitectureLayer, Integer>());

        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        // totalFiles=3, 但实际文件只有 2 个
        String json = jsonWithOverrides("totalFiles", "3");
        ValidationResult result = validator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("totalFiles"));
    }

    @Test
    void v7_totalFiles_valid() {
        List<FileSummaryEntry> files = new ArrayList<>();
        files.add(new FileSummaryEntry("A.java", ArchitectureLayer.SERVICE, "a", "", "", "",
                new ArrayList<String>(), new ArrayList<String>()));
        AggregateSummaryInput input = new AggregateSummaryInput("com.example.test", files,
                new ArrayList<AggregateSummaryInput.CrossPackageDep>(),
                new HashMap<ArchitectureLayer, Integer>());

        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        String json = jsonWithOverrides("totalFiles", "1");
        ValidationResult result = validator.validate(json);
        assertTrue(result.isValid());
    }

    // ========================================================================
    // V8: 风险计数与概述一致（WARN 以计数为准）
    // ========================================================================

    @Test
    void v8_riskCounts_aligned() {
        AggregateSummaryInput input = inputWithPackage("com.example.test");
        String json = jsonWithOverrides("riskOverview", "\"有风险\"");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        // V8 是 WARN，不阻断
        ValidationResult result = validator.validate(json);
        assertTrue(result.isValid());
    }

    // ========================================================================
    // V9: Token 估算 ≤800（WARN 截断）
    // ========================================================================

    @Test
    void v9_tokenEstimate_ok() {
        AggregateSummaryInput input = inputWithPackage("com.example.test");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        String json = jsonWithOverrides("summary", "\"简短摘要\"");
        ValidationResult result = validator.validate(json);
        assertTrue(result.isValid());
    }

    // ========================================================================
    // 无效 JSON
    // ========================================================================

    @Test
    void validate_invalidJson() {
        AggregateSummaryInput input = inputWithPackage("com.example.test");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        ValidationResult result = validator.validate("{invalid json");
        assertFalse(result.isValid());
    }

    @Test
    void validate_nullJson() {
        AggregateSummaryInput input = inputWithPackage("com.example.test");
        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        ValidationResult result = validator.validate(null);
        assertFalse(result.isValid());
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    private static AggregateSummaryInput inputWithPackage(String pkg) {
        return new AggregateSummaryInput(pkg,
                new ArrayList<FileSummaryEntry>(),
                new ArrayList<AggregateSummaryInput.CrossPackageDep>(),
                new HashMap<ArchitectureLayer, Integer>());
    }

    /**
     * 基于完整 JSON 模板，覆盖指定字段。
     */
    private static String jsonWithOverrides(String field, String value) {
        // 基础完整 JSON
        String base = "{"
                + "\"packageName\":\"com.example.test\","
                + "\"architectureLayer\":\"SERVICE\","
                + "\"layerComposition\":\"100% SERVICE\","
                + "\"summary\":\"测试摘要\","
                + "\"coreEntries\":[\"Entry1\"],"
                + "\"coreResponsibilities\":[\"Resp1\"],"
                + "\"crossPackageDeps\":[],"
                + "\"riskOverview\":\"\","
                + "\"totalFiles\":0,"
                + "\"totalMethods\":5,"
                + "\"highRiskCount\":0,"
                + "\"mediumRiskCount\":0"
                + "}";
        // 简单的字段替换
        String search = "\"" + field + "\":";
        int idx = base.indexOf(search);
        if (idx < 0) {
            return base;
        }
        int valStart = idx + search.length();
        // 找到值结束位置（逗号或 }）
        int end = base.indexOf(",", valStart);
        if (end < 0) {
            end = base.indexOf("}", valStart);
        }
        return base.substring(0, valStart) + value + base.substring(end);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
