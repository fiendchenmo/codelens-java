package com.codelens.common.agent.contradiction;

import com.codelens.common.agent.AnalysisReport;
import com.codelens.common.agent.L1Call;
import com.codelens.common.agent.L1Evidence;
import com.codelens.common.agent.L2Confidence;
import com.codelens.common.agent.MethodReport;
import com.codelens.common.agent.RiskItem;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 矛盾检测引擎单元测试。
 * <p>
 * 覆盖 7 个 case（Phase 1 MVP + Phase 2/3 规则）：
 * <ol>
 *   <li>无矛盾 — 正常数据，应返回空 findings</li>
 *   <li>C1 矛盾 — A→B 但 B.calledBy 不含 A</li>
 *   <li>C1 INCOMPLETE — A→B 但 B.calledBy 为空，不降置信度</li>
 *   <li>C4 矛盾 — complexity=HIGH 但 complexityValue=2</li>
 *   <li>混合矛盾 — C1 + C4 同时存在</li>
 *   <li>C2 矛盾 — SUMMARY.complexity=LOW 但 ≥50% 方法 HIGH</li>
 *   <li>C3 矛盾 — risk 行号指向注释行</li>
 * </ol>
 * </p>
 */
public class ContradictionDetectorTest {

    private final ContradictionDetector detector = new ContradictionDetector();

    // ─── Case 1: 无矛盾 ─────────────────────────────

    @Test
    void testNoContradiction() {
        AnalysisReport report = buildReport("LOW", Arrays.asList(
                buildMethod("process", "LOW", 2,
                        new ArrayList<L1Call>(),
                        Arrays.asList("com.example.Main.run"),
                        new ArrayList<RiskItem>()),
                buildMethod("validate", "LOW", 1,
                        new ArrayList<L1Call>(),
                        Arrays.asList("com.example.Service.process"),
                        new ArrayList<RiskItem>())
        ));

        ContradictionReport result = detector.detect(report, null);
        assertNotNull(result);
        assertEquals(0, countContradictory(result.getFindings()));
        assertEquals(0.0, result.getContradictionScore(), 0.001);
    }

    // ─── Case 2: C1 矛盾（A→B 但 B.calledBy 不含 A） ─

    @Test
    void testC1_CallGraphMismatch() {
        // process() calls validate(), but validate() calledBy does NOT include process
        L1Call processCall = new L1Call("this.validate", 10, 0, 1);

        MethodReport process = buildMethod("process", "LOW", 2,
                Arrays.asList(processCall),
                Arrays.asList("com.example.Main.run"), // process's own calledBy
                new ArrayList<RiskItem>());

        MethodReport validate = buildMethod("validate", "LOW", 1,
                new ArrayList<L1Call>(),
                Arrays.asList("com.example.Other.method"), // validate's calledBy: doesn't include process
                new ArrayList<RiskItem>());

        AnalysisReport report = buildReport("MEDIUM", Arrays.asList(process, validate));
        ContradictionReport result = detector.detect(report, null);

        List<ContradictionFinding> contradicted = filterContradictory(result.getFindings());
        assertEquals(1, contradicted.size());
        ContradictionFinding f = contradicted.get(0);
        assertEquals(ContradictionFinding.ContradictionType.CALL_GRAPH_MISMATCH, f.getType());
        assertEquals(ContradictionFinding.Status.CONTRADICTORY, f.getStatus());
        assertEquals("process", f.getSourceMethod());
        assertEquals("validate", f.getTargetMethod());
        assertEquals(-0.2, f.getConfidencePenalty(), 0.001);
    }

    // ─── Case 3: C1 INCOMPLETE（A→B 但 B.calledBy 为空） ─

    @Test
    void testC1_CallGraphIncomplete() {
        L1Call processCall = new L1Call("this.validate", 10, 0, 1);

        MethodReport process = buildMethod("process", "LOW", 2,
                Arrays.asList(processCall),
                Arrays.asList("com.example.Main.run"),
                new ArrayList<RiskItem>());

        // validate's calledBy is EMPTY → INCOMPLETE, no penalty
        MethodReport validate = buildMethod("validate", "LOW", 1,
                new ArrayList<L1Call>(),
                new ArrayList<String>(),  // empty calledBy
                new ArrayList<RiskItem>());

        AnalysisReport report = buildReport("MEDIUM", Arrays.asList(process, validate));
        ContradictionReport result = detector.detect(report, null);

        // Should have 1 finding with INCOMPLETE status
        assertEquals(1, result.getFindings().size());
        ContradictionFinding f = result.getFindings().get(0);
        assertEquals(ContradictionFinding.ContradictionType.CALL_GRAPH_MISMATCH, f.getType());
        assertEquals(ContradictionFinding.Status.INCOMPLETE, f.getStatus());
        assertEquals(0.0, f.getConfidencePenalty(), 0.001);

        // INCOMPLETE findings should NOT be counted in score
        assertEquals(0.0, result.getContradictionScore(), 0.001);
    }

    // ─── Case 3b: C1 跨对象调用过滤 — mapper-like call should be skipped ─

    @Test
    void testC1_CrossObjectCallFiltered() {
        // deptMapper.selectDeptById → should be recognized as cross-object call, skipped
        L1Call mapperCall = new L1Call("deptMapper.selectDeptById", 214, 0, 1);

        MethodReport insertDept = buildMethod("insertDept", "LOW", 2,
                Arrays.asList(mapperCall),
                Arrays.asList("com.example.Other.process"),
                new ArrayList<RiskItem>());

        // There IS a same-name method selectDeptById in the file
        MethodReport selectDeptById = buildMethod("selectDeptById", "LOW", 1,
                new ArrayList<L1Call>(),
                Arrays.asList("com.example.Controller.getInfo"), // doesn't include insertDept
                new ArrayList<RiskItem>());

        AnalysisReport report = buildReport("MEDIUM", Arrays.asList(insertDept, selectDeptById));
        ContradictionReport result = detector.detect(report, null);

        // No C1 findings — mapper-like call is filtered out
        List<ContradictionFinding> contradicted = filterContradictory(result.getFindings());
        assertEquals(0, contradicted.size());
    }

    // ─── Case 3c: C1 同对象 this 调用 — should still detect ─

    @Test
    void testC1_ThisCallStillDetected() {
        // this.validate → should NOT be filtered (same object)
        L1Call thisCall = new L1Call("this.validate", 10, 0, 1);

        MethodReport process = buildMethod("process", "LOW", 2,
                Arrays.asList(thisCall),
                Arrays.asList("com.example.Main.run"),
                new ArrayList<RiskItem>());

        MethodReport validate = buildMethod("validate", "LOW", 1,
                new ArrayList<L1Call>(),
                Arrays.asList("com.example.Other.method"), // NOT include process
                new ArrayList<RiskItem>());

        AnalysisReport report = buildReport("MEDIUM", Arrays.asList(process, validate));
        ContradictionReport result = detector.detect(report, null);

        List<ContradictionFinding> contradicted = filterContradictory(result.getFindings());
        assertEquals(1, contradicted.size());
        ContradictionFinding f = contradicted.get(0);
        assertEquals(ContradictionFinding.ContradictionType.CALL_GRAPH_MISMATCH, f.getType());
        assertEquals(ContradictionFinding.Status.CONTRADICTORY, f.getStatus());
    }

    // ─── Case 3d: C1 无前缀直接调用 — should still detect ─

    @Test
    void testC1_DirectCallStillDetected() {
        // selectDeptById (no prefix) → should NOT be filtered
        L1Call directCall = new L1Call("selectDeptById", 10, 0, 1);

        MethodReport caller = buildMethod("checkData", "LOW", 2,
                Arrays.asList(directCall),
                Arrays.asList("com.example.Main.run"),
                new ArrayList<RiskItem>());

        MethodReport target = buildMethod("selectDeptById", "LOW", 1,
                new ArrayList<L1Call>(),
                Arrays.asList("com.example.Other.method"),
                new ArrayList<RiskItem>());

        AnalysisReport report = buildReport("MEDIUM", Arrays.asList(caller, target));
        ContradictionReport result = detector.detect(report, null);

        List<ContradictionFinding> contradicted = filterContradictory(result.getFindings());
        assertEquals(1, contradicted.size());
    }

    // ─── Case 4: C4 字段自相矛盾 ─────────────────────

    @Test
    void testC4_FieldSelfContradiction() {
        // complexity=HIGH but complexityValue=2 (should be 7-10)
        MethodReport highButLow = new MethodReport();
        highButLow.setMethodName("simpleGetter");
        highButLow.setComplexity("HIGH");
        highButLow.setComplexityValue(2);
        highButLow.setL1Evidence(new L1Evidence(
                new ArrayList<L1Call>(),
                new ArrayList<String>(),
                new ArrayList<String>()));
        highButLow.setRisks(new ArrayList<RiskItem>());

        MethodReport normal = buildMethod("normalMethod", "LOW", 1,
                new ArrayList<L1Call>(),
                new ArrayList<String>(),
                new ArrayList<RiskItem>());

        AnalysisReport report = buildReport("MEDIUM", Arrays.asList(highButLow, normal));
        ContradictionReport result = detector.detect(report, null);

        List<ContradictionFinding> contradicted = filterContradictory(result.getFindings());
        assertEquals(1, contradicted.size());
        ContradictionFinding f = contradicted.get(0);
        assertEquals(ContradictionFinding.ContradictionType.FIELD_SELF_CONTRADICTION, f.getType());
        assertEquals(ContradictionFinding.Severity.LOW, f.getSeverity());
        assertEquals("simpleGetter", f.getSourceMethod());
        assertEquals(-0.1, f.getConfidencePenalty(), 0.001);
        assertTrue(f.getDescription().contains("HIGH"));
        assertTrue(f.getDescription().contains("2"));
    }

    // ─── Case 5: 混合矛盾（C1 + C4） ─────────────────

    @Test
    void testC1AndC4Mixed() {
        // C4: process has HIGH complexity but low value
        L1Call processCall = new L1Call("this.validate", 10, 0, 1);

        MethodReport process = buildMethod("process", "HIGH", 2, // HIGH with value=2 → C4
                Arrays.asList(processCall),
                Arrays.asList("com.example.Main.run"),
                new ArrayList<RiskItem>());

        // C1: validate's calledBy doesn't include process
        MethodReport validate = buildMethod("validate", "LOW", 4,
                new ArrayList<L1Call>(),
                Arrays.asList("com.example.Other.method"), // doesn't include process
                new ArrayList<RiskItem>());

        AnalysisReport report = buildReport("MEDIUM", Arrays.asList(process, validate));
        ContradictionReport result = detector.detect(report, null);

        List<ContradictionFinding> contradicted = filterContradictory(result.getFindings());
        assertEquals(2, contradicted.size());

        // Verify both types are present
        boolean hasC1 = false, hasC4 = false;
        for (ContradictionFinding f : contradicted) {
            if (f.getType() == ContradictionFinding.ContradictionType.CALL_GRAPH_MISMATCH) {
                hasC1 = true;
                assertEquals(-0.2, f.getConfidencePenalty(), 0.001);
            }
            if (f.getType() == ContradictionFinding.ContradictionType.FIELD_SELF_CONTRADICTION) {
                hasC4 = true;
                assertEquals(-0.1, f.getConfidencePenalty(), 0.001);
            }
        }
        assertTrue(hasC1, "Should have C1 contradiction");
        assertTrue(hasC4, "Should have C4 contradiction");

        // Score: C1(MEDIUM=0.15) + C4(LOW=0.05) = 0.20
        assertEquals(0.20, result.getContradictionScore(), 0.001);
        assertTrue(result.getAffectedMethods().contains("process"));
    }

    // ─── C2: 摘要-细节冲突 ───────────────────────────

    @Test
    void testC2_SummaryDetailConflict() {
        // 3 HIGH methods out of 5 total, but summary says LOW
        List<MethodReport> methods = new ArrayList<MethodReport>();
        methods.add(buildMethod("m1", "HIGH", 8, new ArrayList<L1Call>(),
                new ArrayList<String>(), new ArrayList<RiskItem>()));
        methods.add(buildMethod("m2", "HIGH", 9, new ArrayList<L1Call>(),
                new ArrayList<String>(), new ArrayList<RiskItem>()));
        methods.add(buildMethod("m3", "HIGH", 7, new ArrayList<L1Call>(),
                new ArrayList<String>(), new ArrayList<RiskItem>()));
        methods.add(buildMethod("m4", "LOW", 2, new ArrayList<L1Call>(),
                new ArrayList<String>(), new ArrayList<RiskItem>()));
        methods.add(buildMethod("m5", "LOW", 3, new ArrayList<L1Call>(),
                new ArrayList<String>(), new ArrayList<RiskItem>()));

        AnalysisReport report = buildReport("LOW", methods); // summary says LOW
        ContradictionReport result = detector.detect(report, null);

        List<ContradictionFinding> contradicted = filterContradictory(result.getFindings());
        assertEquals(1, contradicted.size());
        ContradictionFinding f = contradicted.get(0);
        assertEquals(ContradictionFinding.ContradictionType.SUMMARY_DETAIL_CONFLICT, f.getType());
        assertEquals(ContradictionFinding.Severity.HIGH, f.getSeverity());
        assertEquals(-0.3, f.getConfidencePenalty(), 0.001);
    }

    // ─── C3: 风险-证据矛盾 ───────────────────────────

    @Test
    void testC3_RiskEvidenceContradiction() {
        RiskItem badRisk = new RiskItem();
        badRisk.setType("SECURITY");
        badRisk.setDescription("SQL injection risk");
        badRisk.setLine(2);  // line 2 is a comment
        badRisk.setSeverity("HIGH");

        MethodReport method = buildMethod("queryData", "MEDIUM", 5,
                new ArrayList<L1Call>(),
                new ArrayList<String>(),
                Arrays.asList(badRisk));

        AnalysisReport report = buildReport("MEDIUM", Arrays.asList(method));

        // Source: line 1 is real, line 2 is comment
        String[] sourceLines = {
                "public List<Data> queryData(String sql) {",  // line 1
                "    // TODO: add validation",                   // line 2 (comment!)
                "    return executeQuery(sql);",                 // line 3
                "}"                                             // line 4
        };

        ContradictionReport result = detector.detect(report, sourceLines);
        List<ContradictionFinding> contradicted = filterContradictory(result.getFindings());
        assertEquals(1, contradicted.size());
        ContradictionFinding f = contradicted.get(0);
        assertEquals(ContradictionFinding.ContradictionType.RISK_EVIDENCE_CONTRADICTION, f.getType());
        assertEquals(ContradictionFinding.Severity.HIGH, f.getSeverity());
        assertEquals(-0.5, f.getConfidencePenalty(), 0.001);
        assertEquals("queryData", f.getSourceMethod());
    }

    // ─── isCrossObjectCall ───────────────────────────

    @Test
    void testIsCrossObjectCall() {
        assertTrue("deptMapper.selectDeptById should be cross-object",
                ContradictionDetector.isCrossObjectCall("deptMapper.selectDeptById"));
        assertTrue("SysDeptMapper.selectDeptList should be cross-object",
                ContradictionDetector.isCrossObjectCall("SysDeptMapper.selectDeptList"));
        assertFalse("this.validate should NOT be cross-object",
                ContradictionDetector.isCrossObjectCall("this.validate"));
        assertFalse("validate should NOT be cross-object",
                ContradictionDetector.isCrossObjectCall("validate"));
        assertFalse("selectDeptById should NOT be cross-object",
                ContradictionDetector.isCrossObjectCall("selectDeptById"));
        assertFalse("null should NOT be cross-object",
                ContradictionDetector.isCrossObjectCall(null));
        assertFalse("empty should NOT be cross-object",
                ContradictionDetector.isCrossObjectCall(""));
    }

    // ─── 工具方法 ───────────────────────────────────

    /**
     * 构建 AnalysisReport 辅助方法。
     */
    private AnalysisReport buildReport(String overallComplexity, List<MethodReport> methods) {
        AnalysisReport report = new AnalysisReport();
        report.setClassName("com.example.Test");
        report.setOverallComplexity(overallComplexity);
        report.setSummary("Test summary");
        report.setMethods(methods);
        return report;
    }

    /**
     * 构建 MethodReport 辅助方法。
     */
    private MethodReport buildMethod(String name, String complexity, int complexityValue,
                                      List<L1Call> calls, List<String> calledBy,
                                      List<RiskItem> risks) {
        MethodReport m = new MethodReport();
        m.setMethodName(name);
        m.setComplexity(complexity);
        m.setComplexityValue(complexityValue);
        m.setL1Evidence(new L1Evidence(
                calls != null ? calls : new ArrayList<L1Call>(),
                calledBy != null ? calledBy : new ArrayList<String>(),
                new ArrayList<String>()));
        m.setL2Confidence(new L2Confidence(0.85, "test", new ArrayList<String>()));
        m.setRisks(risks != null ? risks : new ArrayList<RiskItem>());
        return m;
    }

    /**
     * 过滤出 status=CONTRADICTORY 的 findings。
     */
    private List<ContradictionFinding> filterContradictory(List<ContradictionFinding> findings) {
        List<ContradictionFinding> result = new ArrayList<ContradictionFinding>();
        if (findings == null) return result;
        for (ContradictionFinding f : findings) {
            if (f.getStatus() == ContradictionFinding.Status.CONTRADICTORY) {
                result.add(f);
            }
        }
        return result;
    }

    /**
     * 统计 status=CONTRADICTORY 的 findings 数量。
     */
    private int countContradictory(List<ContradictionFinding> findings) {
        return filterContradictory(findings).size();
    }
}
