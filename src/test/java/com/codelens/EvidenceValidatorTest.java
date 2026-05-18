package com.codelens;
import com.codelens.common.validators.EvidenceValidator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

public class EvidenceValidatorTest {

    @Test
    void testExtractJsonArray() {
        String json = "{\"dependencies\": [{\"name\": \"JSONObject\", \"line\": 42}]}";
        String arr = EvidenceValidator.extractJsonArray(json, "dependencies");
        assertNotNull(arr);
        assertTrue(arr.startsWith("["));
        assertTrue(arr.endsWith("]"));
        assertTrue(arr.contains("JSONObject"));
    }

    @Test
    void testExtractJsonArrayNotFound() {
        String json = "{\"summary\": \"test\"}";
        assertNull(EvidenceValidator.extractJsonArray(json, "dependencies"));
    }

    @Test
    void testParseJsonObjects() {
        String arrayJson = "[{\"name\": \"JSONObject\", \"line\": 42}, {\"name\": \"FileUtils\", \"line\": 55}]";
        List<Map<String, String>> items = EvidenceValidator.parseJsonObjects(arrayJson);
        assertEquals(2, items.size());
        assertEquals("JSONObject", items.get(0).get("name"));
        assertEquals("42", items.get(0).get("line"));
        assertEquals("FileUtils", items.get(1).get("name"));
    }

    @Test
    void testParseJsonObjectsEmpty() {
        assertTrue(EvidenceValidator.parseJsonObjects("[]").isEmpty());
        assertTrue(EvidenceValidator.parseJsonObjects(null).isEmpty());
    }

    @Test
    void testFindInNearbyLines() {
        String[] lines = {
            "package com.example;",
            "import java.util.List;",
            "public class Test {",
            "    private JSONObject json;",
            "    public void process() {",
            "        json.parse(s);",
            "    }",
            "}"
        };
        assertTrue(EvidenceValidator.findInNearbyLines(lines, 4, "JSONObject", 2));
        assertTrue(EvidenceValidator.findInNearbyLines(lines, 6, "parse", 1));
        assertFalse(EvidenceValidator.findInNearbyLines(lines, 6, "FileUtils", 2));
    }

    @Test
    void testFindMethodDefinition() {
        String[] lines = {
            "public class Test {",
            "    public void process() {",
            "        return 1;",
            "    }",
            "    private String getData() {",
            "        return \"data\";",
            "    }",
            "}"
        };
        assertTrue(EvidenceValidator.findMethodDefinition(lines, 2, "process", 2));
        assertTrue(EvidenceValidator.findMethodDefinition(lines, 5, "getData", 2));
        assertFalse(EvidenceValidator.findMethodDefinition(lines, 2, "getData", 2));
    }

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
        String source = "line1\nimport JSONObject;\nline3\nline4";
        String llmJson = "{\"dependencies\": [{\"name\": \"JSONObject\", \"line\": 3}]}";
        EvidenceValidator.ValidationResult result = EvidenceValidator.validate(llmJson, source, null);
        assertEquals(1, result.totalChecked);
        assertEquals(1, result.passedCount);
        assertEquals(EvidenceValidator.Confidence.CERTAIN, result.overallConfidence());
    }

    @Test
    void testValidateMethodNotFound() {
        String source = "public class Test {\n    public void realMethod() {\n    }\n}";
        String llmJson = "{\"keyMethods\": [{\"name\": \"nonexist\", \"line\": 2}]}";
        EvidenceValidator.ValidationResult result = EvidenceValidator.validate(llmJson, source, null);
        assertEquals(1, result.totalChecked);
        assertEquals(0, result.passedCount);
        assertEquals(EvidenceValidator.Confidence.LOW, result.overallConfidence());
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
