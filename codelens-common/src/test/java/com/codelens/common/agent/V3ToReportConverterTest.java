package com.codelens.common.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V3ToReportConverter 单元测试。
 */
public class V3ToReportConverterTest {

    @Test
    void convert_withTopLevelRisks_mapped() {
        String v3Json = ""
                + "{"
                + "  \"summary\": \"Test class summary\","
                + "  \"framework\": \"Spring\","
                + "  \"risks\": ["
                + "    {"
                + "      \"type\": \"SECURITY\","
                + "      \"description\": \"Hardcoded secret key\","
                + "      \"line\": 42,"
                + "      \"severity\": \"HIGH\","
                + "      \"impact\": \"Credential leak if code is shared\","
                + "      \"suggestion\": \"Use environment variable\","
                + "      \"confidence\": \"CERTAIN\""
                + "    },"
                + "    {"
                + "      \"type\": \"MAINTAINABILITY\","
                + "      \"description\": \"Method too long\","
                + "      \"line\": 100,"
                + "      \"severity\": \"LOW\","
                + "      \"impact\": \"Hard to read\","
                + "      \"suggestion\": \"Extract smaller methods\","
                + "      \"confidence\": \"POSSIBLE\""
                + "    }"
                + "  ],"
                + "  \"fields\": [],"
                + "  \"methods\": []"
                + "}";

        AnalysisReport report = V3ToReportConverter.convert(v3Json, "com.example.SecurityConfig");

        assertNotNull(report);
        assertEquals("com.example.SecurityConfig", report.getClassName());
        assertNotNull(report.getRisks(), "top-level risks should not be null");
        assertEquals(2, report.getRisks().size());

        // 第一个 risk
        RiskItem r1 = report.getRisks().get(0);
        assertEquals("SECURITY", r1.getType());
        assertEquals("Hardcoded secret key", r1.getDescription());
        assertEquals(42, r1.getLine());
        assertEquals("HIGH", r1.getSeverity());
        assertEquals("Credential leak if code is shared", r1.getImpact());
        assertEquals("Use environment variable", r1.getSuggestion());

        // 第二个 risk
        RiskItem r2 = report.getRisks().get(1);
        assertEquals("MAINTAINABILITY", r2.getType());
        assertEquals("Method too long", r2.getDescription());
        assertEquals(100, r2.getLine());
        assertEquals("LOW", r2.getSeverity());
    }
}
