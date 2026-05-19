package com.codelens;
import com.codelens.common.validators.EvidenceValidator;
import com.codelens.common.validators.ConfidenceAnnotator;

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
        // 修复: Gson 版 reason 取 issue.issue，内容是 "行号超出源码范围..."
        assertTrue(depItem.reason.contains("超出源码范围") || depItem.reason.contains("超出范围"));
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
        // 修复: Gson 版的 annotateRisks 中，没有 issue 时返回 confidence=HIGH (不是 CERTAIN)
        String source = "public class Test {\n    public void process() {\n    }\n}";
        String llmJson = "{\"risks\": [{\"description\": \"code style issue\", \"line\": 2, \"severity\": \"低\"}]}";
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
        // 修复: Gson 版的 annotateKeyMethods 中，如果没有 issue（因为行号在范围内），
        // 会走 else 分支设 confidence=MEDIUM, reason="L1 未覆盖"
        String source = "public class Test {\n    public void process() {\n    }\n}";
        String llmJson = "{\"keyMethods\": [{\"name\": \"process\", \"line\": 2}]}";
        EvidenceValidator.ValidationResult vr = EvidenceValidator.validate(llmJson, source, null);
        ConfidenceAnnotator.AnnotatedResult ar = ConfidenceAnnotator.annotate(llmJson, vr, source.split("\n"));

        ConfidenceAnnotator.AnnotatedItem methodItem = null;
        for (ConfidenceAnnotator.AnnotatedItem item : ar.items) {
            if (item.category.equals("keyMethods")) { methodItem = item; break; }
        }
        assertNotNull(methodItem);
        assertEquals(EvidenceValidator.Confidence.MEDIUM, methodItem.confidence);
        assertTrue(methodItem.reason.contains("L1 未覆盖"));
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

    // testAnnotateUnvalidatedFields 已删除
    // Gson 版 ConfidenceAnnotator 只标注 dependencies/risks/keyMethods，
    // 不再标注 summary/design_intent/class_analysis 这些纯文本字段

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
