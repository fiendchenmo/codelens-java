package com.codelens.common.agent;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class AnalysisReportTest {

    @Test
    public void testToJson_ContainsAllFields() {
        L1Evidence l1 = new L1Evidence(
                Arrays.asList(new L1Call("validate"), new L1Call("save")),
                Collections.singletonList("handle"),
                Collections.singletonList("repository"));

        L2Confidence l2 = new L2Confidence(0.85, "SOLID_ANALYSIS",
                Collections.singletonList("NULL_CHECK_MISSING"));

        MethodReport method = new MethodReport("processOrder",
                "public void processOrder(Long id)", 10, l1, l2);

        AnalysisReport report = new AnalysisReport(
                "com.example.OrderService", "SERVICE",
                Collections.singletonList(method),
                Arrays.asList("OrderRepository", "PaymentGateway"),
                "MEDIUM");

        String json = report.toJson();

        assertNotNull(json);
        assertTrue(json.contains("OrderService"), "JSON 应包含 className");
        assertTrue(json.contains("SERVICE"), "JSON 应包含 stereotype");
        assertTrue(json.contains("processOrder"), "JSON 应包含方法名");
        assertTrue(json.contains("MEDIUM"), "JSON 应包含复杂度");
    }

    @Test
    public void testFieldsDirectly() {
        AnalysisReport report = new AnalysisReport();
        report.setClassName("TestClass");
        report.setStereotype("DTO");
        report.setOverallComplexity("LOW");
        report.setDependencies(Collections.<String>emptyList());
        report.setMethods(Collections.<MethodReport>emptyList());

        assertEquals("TestClass", report.getClassName());
        assertEquals("DTO", report.getStereotype());
        assertEquals("LOW", report.getOverallComplexity());
        assertTrue(report.getMethods().isEmpty());
    }
}
