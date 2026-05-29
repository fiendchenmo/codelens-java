package com.codelens;

import com.codelens.common.agent.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class ReportConverterTest {

    @Test
    public void testConvertEmptyReport() {
        AnalysisReport report = new AnalysisReport();
        report.setClassName("com.example.Empty");
        report.setStereotype("DTO");
        report.setMethods(Collections.<MethodReport>emptyList());

        String json = ReportConverter.convert(report, Collections.<ExecutionTrace>emptyList());

        assertNotNull(json);
        assertTrue(json.contains("com.example.Empty"), "JSON 应包含 className");
        assertTrue(json.contains("DTO"), "JSON 应包含 stereotype");
        assertTrue(json.contains("\"methods\""), "JSON 应包含 methods 数组");
    }

    @Test
    public void testConvertSingleMethod() {
        L1Evidence l1 = new L1Evidence(
                Arrays.asList("validate", "save"),
                Collections.singletonList("handle"),
                Collections.singletonList("repository"));

        L2Confidence l2 = new L2Confidence(0.85, "SOLID_ANALYSIS",
                Collections.singletonList("NULL_CHECK_MISSING"));

        MethodReport method = new MethodReport("processOrder", "public void processOrder(Long id)", l1, l2);

        AnalysisReport report = new AnalysisReport(
                "com.example.OrderService", "SERVICE",
                Collections.singletonList(method),
                Arrays.asList("OrderRepository", "PaymentGateway"),
                "MEDIUM");

        String json = ReportConverter.convert(report, Collections.<ExecutionTrace>emptyList());

        assertNotNull(json);
        assertTrue(json.contains("processOrder"), "JSON 应包含方法名");
        assertTrue(json.contains("validate"), "JSON 应包含 L1 calls");
        assertTrue(json.contains("0.85"), "JSON 应包含 L2 overallScore");
        assertTrue(json.contains("SOLID_ANALYSIS"), "JSON 应包含 L2 reasoningBasis");
        assertTrue(json.contains("NULL_CHECK_MISSING"), "JSON 应包含 riskIndicators");
    }

    @Test
    public void testConvertExecutionTrace() {
        AnalysisReport report = new AnalysisReport();
        report.setClassName("com.example.Test");
        report.setMethods(Collections.<MethodReport>emptyList());

        ExecutionTrace trace = new ExecutionTrace("task-1", TaskType.SUMMARY,
                ExecutionStatus.COMPLETED, false, 0, 150);

        String json = ReportConverter.convert(report, Collections.singletonList(trace));

        assertNotNull(json);
        assertTrue(json.contains("executionTrace"), "JSON 应包含 executionTrace");
        assertTrue(json.contains("task-1"), "JSON 应包含 task ID");
        assertTrue(json.contains("COMPLETED"), "JSON 应包含状态");
    }
}
