package com.codelens.common.agent;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReportMergerTest {

    private final ReportMerger merger = new ReportMerger();

    @Test
    public void testMerge_Normal() {
        String summaryJson = "{\n" +
                "  \"className\": \"com.example.OrderService\",\n" +
                "  \"stereotype\": \"SERVICE\",\n" +
                "  \"keyMethods\": [{\"name\": \"processOrder\", \"role\": \"核心业务\"}],\n" +
                "  \"dependencies\": [\"OrderRepository\"],\n" +
                "  \"complexity\": \"MEDIUM\"\n" +
                "}";

        String methodJson = "{\n" +
                "  \"method\": \"processOrder\",\n" +
                "  \"l1Evidence\": {\n" +
                "    \"calls\": [\"validateOrder\"],\n" +
                "    \"calledBy\": [\"handleRequest\"],\n" +
                "    \"fieldsUsed\": [\"orderRepo\"]\n" +
                "  },\n" +
                "  \"l2Confidence\": {\n" +
                "    \"overallScore\": 0.9,\n" +
                "    \"reasoningBasis\": \"SOLID_ANALYSIS\",\n" +
                "    \"riskIndicators\": []\n" +
                "  }\n" +
                "}";

        AnalysisReport report = merger.merge(summaryJson, Collections.singletonList(methodJson));

        assertEquals("com.example.OrderService", report.getClassName());
        assertEquals("SERVICE", report.getStereotype());
        assertEquals("MEDIUM", report.getOverallComplexity());
        assertEquals(1, report.getDependencies().size());
        assertEquals("OrderRepository", report.getDependencies().get(0));

        assertEquals(1, report.getMethods().size());
        assertEquals("processOrder", report.getMethods().get(0).getMethodName());
        assertNotNull(report.getMethods().get(0).getL1Evidence());
        assertNotNull(report.getMethods().get(0).getL2Confidence());
        assertEquals(0.9, report.getMethods().get(0).getL2Confidence().getOverallScore(), 0.001);
    }

    @Test
    public void testMerge_EmptyMethodList() {
        String summaryJson = "{\n" +
                "  \"className\": \"com.example.EmptyClass\",\n" +
                "  \"stereotype\": \"DTO\",\n" +
                "  \"keyMethods\": [],\n" +
                "  \"dependencies\": [],\n" +
                "  \"complexity\": \"LOW\"\n" +
                "}";

        AnalysisReport report = merger.merge(summaryJson, Collections.<String>emptyList());

        assertEquals("com.example.EmptyClass", report.getClassName());
        assertTrue(report.getMethods().isEmpty());
        assertTrue(report.getDependencies().isEmpty());
    }

    @Test
    public void testMerge_MethodOverridesSummary() {
        String summaryJson = "{\n" +
                "  \"className\": \"com.example.Test\",\n" +
                "  \"stereotype\": \"SERVICE\",\n" +
                "  \"keyMethods\": [{\"name\": \"oldMethod\"}],\n" +
                "  \"dependencies\": [\"DepA\"],\n" +
                "  \"complexity\": \"HIGH\"\n" +
                "}";

        String methodJson = "{\n" +
                "  \"method\": \"newMethod\",\n" +
                "  \"l1Evidence\": {\n" +
                "    \"calls\": [\"helper\"],\n" +
                "    \"calledBy\": [],\n" +
                "    \"fieldsUsed\": []\n" +
                "  },\n" +
                "  \"l2Confidence\": {\n" +
                "    \"overallScore\": 0.7,\n" +
                "    \"reasoningBasis\": \"HEURISTIC\",\n" +
                "    \"riskIndicators\": []\n" +
                "  }\n" +
                "}";

        AnalysisReport report = merger.merge(summaryJson, Collections.singletonList(methodJson));

        // 类级信息来自摘要
        assertEquals("com.example.Test", report.getClassName());
        assertEquals("SERVICE", report.getStereotype());
        assertEquals("HIGH", report.getOverallComplexity());

        // 方法级信息来自方法分析
        assertEquals(1, report.getMethods().size());
        assertEquals("newMethod", report.getMethods().get(0).getMethodName());
        assertEquals(0.7, report.getMethods().get(0).getL2Confidence().getOverallScore(), 0.001);
    }
}
