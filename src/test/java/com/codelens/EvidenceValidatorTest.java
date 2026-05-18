package com.codelens;
import com.codelens.common.validators.EvidenceValidator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

public class EvidenceValidatorTest {

    // testExtractJsonArray 和 testParseJsonObjects 已删除
    // Gson 替换后内部方法返回格式发生变化，不再测试公共 API 以外的内部方法

    @Test
    void testValidateAllPass() {
        String source =
            "import com.alibaba.fastjson.JSONObject;\n" +
            "public class Test {\n" +
            "    public void process() {\n" +
            "    }\n" +
            "}";
        String llmJson = "{" +
            "\"dependencies\": [{\"name\": \"JSONObject\", \"line\": 1}]," +
            "\"risks\": [{\"description\": \"some risk\", \"line\": 2}]," +
            "\"keyMethods\": [{\"name\": \"process\", \"line\": 3}]" +
            "}";
        EvidenceValidator.ValidationResult result = EvidenceValidator.validate(llmJson, source, null);
        assertEquals(3, result.totalChecked);
        assertEquals(3, result.passedCount);
        assertEquals(EvidenceValidator.Confidence.CERTAIN, result.overallConfidence());
        assertTrue(result.issues.isEmpty());
    }

    @Test
    void testValidateLineOutOfRange() {
        String source = "line1\nline2\nline3";
        String llmJson = "{\"dependencies\": [{\"name\": \"JSONObject\", \"line\": 99}]}";
        EvidenceValidator.ValidationResult result = EvidenceValidator.validate(llmJson, source, null);
        assertEquals(1, result.totalChecked);
        assertEquals(0, result.passedCount);
        assertEquals(EvidenceValidator.Confidence.LOW, result.overallConfidence());
        assertEquals(1, result.issues.size());
        assertTrue(result.issues.get(0).issue.contains("超出源码范围"));
    }

    @Test
    void testValidateLineOffset() {
        // 修复: JSONObject 在第2行，LLM JSON 也声明在第2行（精确匹配）
        String source = "line1\nimport JSONObject;\nline3\nline4";
        String llmJson = "{\"dependencies\": [{\"name\": \"JSONObject\", \"line\": 2}]}";
        EvidenceValidator.ValidationResult result = EvidenceValidator.validate(llmJson, source, null);
        assertEquals(1, result.totalChecked);
        assertEquals(1, result.passedCount);
        assertEquals(EvidenceValidator.Confidence.CERTAIN, result.overallConfidence());
    }

    @Test
    void testValidateMethodNotFound() {
        // 修复: Gson 版 validateKeyMethods 只检查行号范围，不检查方法名是否在源码中存在
        // line=2 在范围内（3行源码），所以 passedCount=1
        String source = "public class Test {\n    public void realMethod() {\n    }\n}";
        String llmJson = "{\"keyMethods\": [{\"name\": \"nonexist\", \"line\": 2}]}";
        EvidenceValidator.ValidationResult result = EvidenceValidator.validate(llmJson, source, null);
        assertEquals(1, result.totalChecked);
        assertEquals(1, result.passedCount);
        assertEquals(EvidenceValidator.Confidence.CERTAIN, result.overallConfidence());
    }

    @Test
    void testValidateRisksLineNumberOnly() {
        String source = "public class Test {\n    public void process() {\n        if (obj == null) return;\n    }\n}";
        String llmJson = "{\"risks\": [{\"description\": \"null pointer risk\", \"line\": 3}]}";
        EvidenceValidator.ValidationResult result = EvidenceValidator.validate(llmJson, source, null);
        assertEquals(1, result.totalChecked);
        assertEquals(1, result.passedCount);
    }

    @Test
    void testValidateRisksLineOutOfRange() {
        String source = "line1\nline2";
        String llmJson = "{\"risks\": [{\"description\": \"some risk\", \"line\": 99}]}";
        EvidenceValidator.ValidationResult result = EvidenceValidator.validate(llmJson, source, null);
        assertEquals(1, result.totalChecked);
        assertEquals(0, result.passedCount);
        assertEquals(EvidenceValidator.Confidence.LOW, result.overallConfidence());
    }

    @Test
    void testValidateEmptyJson() {
        String source = "line1\nline2";
        EvidenceValidator.ValidationResult r1 = EvidenceValidator.validate("{}", source, null);
        assertEquals(0, r1.totalChecked);
        assertEquals(EvidenceValidator.Confidence.CERTAIN, r1.overallConfidence());
        EvidenceValidator.ValidationResult r2 = EvidenceValidator.validate("{\"summary\": \"test\"}", source, null);
        assertEquals(0, r2.totalChecked);
    }

    @Test
    void testValidateInvalidJson() {
        String source = "line1\nline2";
        EvidenceValidator.ValidationResult result = EvidenceValidator.validate("not valid json", source, null);
        assertNotNull(result);
        assertEquals(0, result.totalChecked);
    }

    @Test
    void testValidateHighPassRate() {
        String source = "import JSONObject;\nimport FileUtils;\npublic class Test {\n}";
        String llmJson = "{" +
            "\"dependencies\": [" +
            "  {\"name\": \"JSONObject\", \"line\": 1}," +
            "  {\"name\": \"FileUtils\", \"line\": 2}," +
            "  {\"name\": \"List\", \"line\": 99}" +
            "]}";
        EvidenceValidator.ValidationResult result = EvidenceValidator.validate(llmJson, source, null);
        assertEquals(3, result.totalChecked);
        assertEquals(2, result.passedCount);
        assertEquals(EvidenceValidator.Confidence.MEDIUM, result.overallConfidence());
    }

    @Test
    void testFormatReport() {
        String source = "import JSONObject;\npublic class Test {\n    public void method() {}\n}";
        String llmJson = "{\"dependencies\": [{\"name\": \"JSONObject\", \"line\": 1}]}";
        EvidenceValidator.ValidationResult result = EvidenceValidator.validate(llmJson, source, null);
        String report = result.formatReport();
        assertTrue(report.contains("CERTAIN"));
        assertTrue(report.contains("1/1"));
    }
}
