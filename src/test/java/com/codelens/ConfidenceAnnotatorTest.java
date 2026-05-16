package com.codelens;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

public class ConfidenceAnnotatorTest {

    @Test
    void testAnnotateAllCertain() {
        String source = "import com.alibaba.fastjson.JSONObject;\npublic class Test {\n    public void process() {\n    }\n}";
        String llmJson = "{" +
            "\"dependencies\": [{\"name\": \"JSONObject\", \"line\": 1}]," +
            "\"risks\": [{\"description\": \"some risk\", \"line\": 2, \"severity\": \"低\"}]," +
            "\"keyMethods\": [{\"name\": \"process\", \"line\": 3}]" +
            "}";
        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(llmJson, source, null);
        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(llmJson, vr, source.split("\n"));

        // 检查标注条目
        assertTrue(ar.items.size() >= 3);
        assertEquals(EvidenceValidator.Confidence.CERTAIN, vr.overallConfidence());
    }

    @Test
    void testAnnotateDependencyLineOutOfRange() {
        String source = "line1\nline2\nline3";
        String llmJson = "{\"dependencies\": [{\"name\": \"JSONObject\", \"line\": 99}]}";
        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(llmJson, source, null);
        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(llmJson, vr, source.split("\n"));

        ConfidenceAnnotator.AnnotatedItem depItem = null;
        for (ConfidenceAnnotator.AnnotatedItem item : ar.items) {
            if (item.category.equals("dependencies")) { depItem = item; break; }
        }
        assertNotNull(depItem);
        assertEquals(EvidenceValidator.Confidence.LOW, depItem.confidence);
        assertTrue(depItem.reason.contains("超出范围") || depItem.reason.contains("不匹配"));
    }

    @Test
    void testAnnotateRiskHighSeverity() {
        String source = "public class Test {\n    public void process() {\n        if (obj == null) return;\n    }\n}";
        String llmJson = "{\"risks\": [{\"description\": \"null pointer risk\", \"line\": 3, \"severity\": \"高\"}]}";
        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(llmJson, source, null);
        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(llmJson, vr, source.split("\n"));

        ConfidenceAnnotator.AnnotatedItem riskItem = null;
        for (ConfidenceAnnotator.AnnotatedItem item : ar.items) {
            if (item.category.equals("risks")) { riskItem = item; break; }
        }
        assertNotNull(riskItem);
        assertEquals(EvidenceValidator.Confidence.HIGH, riskItem.confidence);
    }

    @Test
    void testAnnotateRiskLowSeverity() {
        String source = "public class Test {\n    public void process() {\n    }\n}";
        String llmJson = "{\"risks\": [{\"description\": \"code style issue\", \"line\": 2, \"severity\": \"低\"}]}";
        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(llmJson, source, null);
        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(llmJson, vr, source.split("\n"));

        ConfidenceAnnotator.AnnotatedItem riskItem = null;
        for (ConfidenceAnnotator.AnnotatedItem item : ar.items) {
            if (item.category.equals("risks")) { riskItem = item; break; }
        }
        assertNotNull(riskItem);
        assertEquals(EvidenceValidator.Confidence.CERTAIN, riskItem.confidence);
    }

    @Test
    void testAnnotateMethodNotFound() {
        String source = "public class Test {\n    public void realMethod() {\n    }\n}";
        String llmJson = "{\"keyMethods\": [{\"name\": \"nonexist\", \"line\": 2}]}";
        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(llmJson, source, null);
        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(llmJson, vr, source.split("\n"));

        ConfidenceAnnotator.AnnotatedItem methodItem = null;
        for (ConfidenceAnnotator.AnnotatedItem item : ar.items) {
            if (item.category.equals("keyMethods")) { methodItem = item; break; }
        }
        assertNotNull(methodItem);
        assertEquals(EvidenceValidator.Confidence.MEDIUM, methodItem.confidence);
    }

    @Test
    void testAnnotateMethodExactMatch() {
        String source = "public class Test {\n    public void process() {\n    }\n}";
        String llmJson = "{\"keyMethods\": [{\"name\": \"process\", \"line\": 2}]}";
        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(llmJson, source, null);
        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(llmJson, vr, source.split("\n"));

        ConfidenceAnnotator.AnnotatedItem methodItem = null;
        for (ConfidenceAnnotator.AnnotatedItem item : ar.items) {
            if (item.category.equals("keyMethods")) { methodItem = item; break; }
        }
        assertNotNull(methodItem);
        assertEquals(EvidenceValidator.Confidence.CERTAIN, methodItem.confidence);
        assertTrue(methodItem.reason.contains("精确匹配"));
    }

    @Test
    void testAnnotateEmptyJson() {
        String source = "line1\nline2";
        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate("{}", source, null);
        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate("{}", vr, source.split("\n"));

        // 只有 summary/design_intent/class_analysis 可能被标注为 MEDIUM
        // 空 JSON 没有这些字段
        assertTrue(ar.items.size() <= 3);
    }

    @Test
    void testExtractSimpleName() {
        assertEquals("JSONObject", ConfidenceAnnotator.extractSimpleName("com.alibaba.fastjson.JSONObject"));
        assertEquals("process", ConfidenceAnnotator.extractSimpleName("process(String, int)"));
        assertEquals("getData", ConfidenceAnnotator.extractSimpleName("getData()"));
        assertEquals("Test", ConfidenceAnnotator.extractSimpleName("Test"));
    }

    @Test
    void testExtractStringValue() {
        String json = "{\"summary\": \"This is a test\", \"count\": 5}";
        assertEquals("This is a test", ConfidenceAnnotator.extractStringValue(json, "summary"));
        assertNull(ConfidenceAnnotator.extractStringValue(json, "nonexist"));
    }

    @Test
    void testInjectConfidenceIntoJson() {
        String source = "import JSONObject;\npublic class Test {\n    public void process() {\n    }\n}";
        String llmJson = "{\"dependencies\": [{\"name\": \"JSONObject\", \"line\": 1}], \"keyMethods\": [{\"name\": \"process\", \"line\": 3}]}";
        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(llmJson, source, null);
        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(llmJson, vr, source.split("\n"));

        String injected = ConfidenceAnnotator.injectConfidenceIntoJson(llmJson, ar, vr);
        assertTrue(injected.contains("\"confidence\":"));
        assertTrue(injected.contains("\"validation\":"));
        assertTrue(injected.contains("overall_confidence"));
    }

    @Test
    void testAnnotateUnvalidatedFields() {
        String source = "public class Test {\n}";
        String llmJson = "{\"summary\": \"A test class\", \"design_intent\": \"Testing\", \"class_analysis\": \"Data flow here\"}";
        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(llmJson, source, null);
        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(llmJson, vr, source.split("\n"));

        // 3 text fields should be annotated as MEDIUM
        int mediumCount = 0;
        for (ConfidenceAnnotator.AnnotatedItem item : ar.items) {
            if (item.confidence == EvidenceValidator.Confidence.MEDIUM) mediumCount++;
        }
        assertEquals(3, mediumCount);
    }

    @Test
    void testFormatReport() {
        String source = "import JSONObject;\npublic class Test {\n    public void process() {\n    }\n}";
        String llmJson = "{\"dependencies\": [{\"name\": \"JSONObject\", \"line\": 1}]}";
        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(llmJson, source, null);
        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(llmJson, vr, source.split("\n"));

        String report = ar.formatReport();
        assertNotNull(report);
        assertTrue(report.contains("L2"));
    }

    @Test
    void testAnnotateMixedConfidence() {
        String source = "import JSONObject;\npublic class Test {\n    public void process() {\n    }\n}";
        String llmJson = "{" +
            "\"dependencies\": [{\"name\": \"JSONObject\", \"line\": 1}, {\"name\": \"NonExist\", \"line\": 99}]," +
            "\"risks\": [{\"description\": \"risk1\", \"line\": 2, \"severity\": \"高\"}]" +
            "}";
        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(llmJson, source, null);
        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(llmJson, vr, source.split("\n"));

        // Should have items with different confidence levels
        boolean hasLow = false, hasHighOrCertain = false;
        for (ConfidenceAnnotator.AnnotatedItem item : ar.items) {
            if (item.confidence == EvidenceValidator.Confidence.LOW) hasLow = true;
            if (item.confidence == EvidenceValidator.Confidence.HIGH || item.confidence == EvidenceValidator.Confidence.CERTAIN) hasHighOrCertain = true;
        }
        assertTrue(hasLow, "Should have LOW confidence items");
        assertTrue(hasHighOrCertain, "Should have HIGH/CERTAIN confidence items");
    }


@Test
    void testAnnotateMethodLineOutOfRange() {
        String source = "public class Test {\n    public void realMethod() {\n    }\n}";
        String llmJson = "{\"keyMethods\": [{\"name\": \"nonexist\", \"line\": 99}]}";
        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(llmJson, source, null);
        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(llmJson, vr, source.split("\n"));

        ConfidenceAnnotator.AnnotatedItem methodItem = null;
        for (ConfidenceAnnotator.AnnotatedItem item : ar.items) {
            if (item.category.equals("keyMethods")) { methodItem = item; break; }
        }
        assertNotNull(methodItem);
        assertEquals(EvidenceValidator.Confidence.LOW, methodItem.confidence);
    }
}
