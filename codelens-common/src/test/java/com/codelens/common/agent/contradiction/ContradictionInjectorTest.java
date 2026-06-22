package com.codelens.common.agent.contradiction;

import com.codelens.common.agent.AnalysisReport;
import com.codelens.common.agent.L2Confidence;
import com.codelens.common.agent.MethodReport;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * ContradictionInjector 单元测试。
 * <p>
 * 覆盖两个关键逻辑：
 * <ol>
 *   <li>L2 地板值 — penalty 叠加后不低于 0.1</li>
 *   <li>INCOMPLETE finding 不注入 MethodReport</li>
 * </ol>
 * </p>
 */
public class ContradictionInjectorTest {

    private final ContradictionInjector injector = new ContradictionInjector();

    @Test
    void testL2FloorValue_PenaltyExceedsScore() {
        // 原 L2=0.3 + penalty=-0.5 → 应被地板值 0.1 兜底
        MethodReport method = new MethodReport();
        method.setMethodName("riskyMethod");
        method.setL2Confidence(new L2Confidence(0.3, "high risk", new ArrayList<String>()));

        AnalysisReport report = new AnalysisReport();
        report.setClassName("com.example.Test");
        report.setMethods(Arrays.asList(method));

        ContradictionFinding f = new ContradictionFinding(
                ContradictionFinding.ContradictionType.RISK_EVIDENCE_CONTRADICTION,
                ContradictionFinding.Severity.HIGH,
                "riskyMethod", null,
                -0.5,
                "risk points to comment",
                "evidence",
                ContradictionFinding.Status.CONTRADICTORY
        );

        ContradictionReport cr = new ContradictionReport();
        cr.setFindings(Arrays.asList(f));
        cr.computeScore();

        injector.inject(report, cr);

        assertEquals(0.1, method.getL2Confidence().getOverallScore(), 0.001,
                "L2 should be floored at 0.1 when penalty exceeds");
        assertEquals(-0.5, method.getL2Confidence().getContradictionPenalty(), 0.001);
    }

    @Test
    void testL2FloorValue_NoPenaltyBelowFloor() {
        // 原 L2=0.85 + penalty=-0.1 → 0.75 (正常累加，不到地板值)
        MethodReport method = new MethodReport();
        method.setMethodName("normalMethod");
        method.setL2Confidence(new L2Confidence(0.85, "normal", new ArrayList<String>()));

        AnalysisReport report = new AnalysisReport();
        report.setClassName("com.example.Test");
        report.setMethods(Arrays.asList(method));

        ContradictionFinding f = new ContradictionFinding(
                ContradictionFinding.ContradictionType.FIELD_SELF_CONTRADICTION,
                ContradictionFinding.Severity.LOW,
                "normalMethod", null,
                -0.1,
                "complexity mismatch",
                "evidence",
                ContradictionFinding.Status.CONTRADICTORY
        );

        ContradictionReport cr = new ContradictionReport();
        cr.setFindings(Arrays.asList(f));
        cr.computeScore();

        injector.inject(report, cr);

        assertEquals(0.75, method.getL2Confidence().getOverallScore(), 0.001,
                "L2 should be 0.75 when penalty doesn't hit floor");
    }

    @Test
    void testIncompleteFindingNotInjected() {
        // INCOMPLETE finding 不应写入 MethodReport.contradictionFindings
        MethodReport method = new MethodReport();
        method.setMethodName("targetMethod");
        method.setL2Confidence(new L2Confidence(0.9, "good", new ArrayList<String>()));

        AnalysisReport report = new AnalysisReport();
        report.setClassName("com.example.Test");
        report.setMethods(Arrays.asList(method));

        ContradictionFinding incomplete = new ContradictionFinding(
                ContradictionFinding.ContradictionType.CALL_GRAPH_MISMATCH,
                ContradictionFinding.Severity.MEDIUM,
                "targetMethod", null,
                0.0,  // INCOMPLETE 无惩罚
                "calledBy empty",
                "evidence",
                ContradictionFinding.Status.INCOMPLETE
        );

        ContradictionReport cr = new ContradictionReport();
        cr.setFindings(Arrays.asList(incomplete));
        cr.computeScore();

        injector.inject(report, cr);

        // MethodReport.contradictionFindings 应为 null（未被注入）
        assertNull(method.getContradictionFindings(),
                "INCOMPLETE finding should NOT be injected into MethodReport");
        // L2 不变
        assertEquals(0.9, method.getL2Confidence().getOverallScore(), 0.001);
    }

    @Test
    void testContradictoryFindingIsInjected() {
        // CONTRADICTORY finding 应正确注入 MethodReport
        MethodReport method = new MethodReport();
        method.setMethodName("buggyMethod");
        method.setL2Confidence(new L2Confidence(0.7, "ok", new ArrayList<String>()));

        AnalysisReport report = new AnalysisReport();
        report.setClassName("com.example.Test");
        report.setMethods(Arrays.asList(method));

        ContradictionFinding contradicted = new ContradictionFinding(
                ContradictionFinding.ContradictionType.CALL_GRAPH_MISMATCH,
                ContradictionFinding.Severity.MEDIUM,
                "buggyMethod", null,
                -0.2,
                "call graph mismatch",
                "evidence",
                ContradictionFinding.Status.CONTRADICTORY
        );

        ContradictionReport cr = new ContradictionReport();
        cr.setFindings(Arrays.asList(contradicted));
        cr.computeScore();

        injector.inject(report, cr);

        // 应注入
        assertNotNull(method.getContradictionFindings(),
                "CONTRADICTORY finding should be injected");
        assertEquals(1, method.getContradictionFindings().size());
        assertEquals(ContradictionFinding.ContradictionType.CALL_GRAPH_MISMATCH,
                method.getContradictionFindings().get(0).getType());
        // L2 应更新
        assertEquals(0.5, method.getL2Confidence().getOverallScore(), 0.001);
    }
}
