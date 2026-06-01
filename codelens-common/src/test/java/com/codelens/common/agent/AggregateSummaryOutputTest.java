package com.codelens.common.agent;

import com.codelens.common.models.ArchitectureLayer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AggregateSummaryOutput} 反序列化与字段校验测试。
 */
class AggregateSummaryOutputTest {

    private static final Gson GSON = new GsonBuilder().create();

    @Test
    void deserializeFullJson() {
        String json = "{"
                + "\"packageName\":\"com.example.service\","
                + "\"architectureLayer\":\"SERVICE\","
                + "\"layerComposition\":\"80% SERVICE + 20% CONTROLLER\","
                + "\"summary\":\"订单服务包，处理订单创建与查询\","
                + "\"coreEntries\":[\"OrderService\"],"
                + "\"coreResponsibilities\":[\"订单创建\",\"订单查询\"],"
                + "\"crossPackageDeps\":["
                + "  {\"targetPackage\":\"com.example.repository\","
                + "   \"viaMethods\":[\"orderMapper.insert\"],"
                + "   \"direction\":\"outgoing\"}"
                + "],"
                + "\"riskOverview\":\"存在 1 个高风险\","
                + "\"totalFiles\":5,"
                + "\"totalMethods\":23,"
                + "\"highRiskCount\":1,"
                + "\"mediumRiskCount\":2"
                + "}";
        AggregateSummaryOutput output = GSON.fromJson(json, AggregateSummaryOutput.class);
        assertNotNull(output);
        assertEquals("com.example.service", output.getPackageName());
        assertEquals(ArchitectureLayer.SERVICE, output.getArchitectureLayer());
        assertEquals("80% SERVICE + 20% CONTROLLER", output.getLayerComposition());
        assertEquals("订单服务包，处理订单创建与查询", output.getSummary());
        assertNotNull(output.getCoreEntries());
        assertEquals(1, output.getCoreEntries().size());
        assertEquals("OrderService", output.getCoreEntries().get(0));
        assertNotNull(output.getCoreResponsibilities());
        assertEquals(2, output.getCoreResponsibilities().size());
        assertNotNull(output.getCrossPackageDeps());
        assertEquals(1, output.getCrossPackageDeps().size());
        assertEquals("outgoing", output.getCrossPackageDeps().get(0).getDirection());
        assertEquals("存在 1 个高风险", output.getRiskOverview());
        assertEquals(5, output.getTotalFiles());
        assertEquals(23, output.getTotalMethods());
        assertEquals(1, output.getHighRiskCount());
        assertEquals(2, output.getMediumRiskCount());
    }

    @Test
    void deserializePartialJson() {
        String json = "{"
                + "\"packageName\":\"com.example.config\","
                + "\"summary\":\"配置管理\""
                + "}";
        AggregateSummaryOutput output = GSON.fromJson(json, AggregateSummaryOutput.class);
        assertNotNull(output);
        assertEquals("com.example.config", output.getPackageName());
        assertNull(output.getArchitectureLayer());
        assertEquals("配置管理", output.getSummary());
        assertNull(output.getCoreEntries());
        assertEquals(0, output.getTotalFiles());
    }

    @Test
    void defaultValues() {
        AggregateSummaryOutput output = new AggregateSummaryOutput();
        assertNull(output.getPackageName());
        assertNull(output.getArchitectureLayer());
        assertNull(output.getSummary());
        assertEquals(0, output.getTotalFiles());
        assertEquals(0, output.getTotalMethods());
        assertEquals(0, output.getHighRiskCount());
        assertEquals(0, output.getMediumRiskCount());
    }

    @Test
    void serializationRoundTrip() {
        AggregateSummaryOutput output = new AggregateSummaryOutput();
        output.setPackageName("com.example.test");
        output.setArchitectureLayer(ArchitectureLayer.UTIL);
        output.setSummary("工具包");
        output.setCoreEntries(Arrays.asList("StringUtil"));
        output.setCoreResponsibilities(Arrays.asList("字符串处理"));
        output.setTotalFiles(3);
        output.setTotalMethods(15);

        String json = GSON.toJson(output);
        assertTrue(json.contains("com.example.test"));
        assertTrue(json.contains("UTIL"));

        AggregateSummaryOutput parsed = GSON.fromJson(json, AggregateSummaryOutput.class);
        assertEquals("com.example.test", parsed.getPackageName());
        assertEquals(ArchitectureLayer.UTIL, parsed.getArchitectureLayer());
        assertEquals("工具包", parsed.getSummary());
    }

    @Test
    void crossPackageDepRoundTrip() {
        AggregateSummaryOutput.CrossPackageDep dep = new AggregateSummaryOutput.CrossPackageDep();
        dep.setTargetPackage("com.example.dao");
        dep.setViaMethods(Arrays.asList("findAll"));
        dep.setDirection("incoming");

        String json = GSON.toJson(dep);
        assertTrue(json.contains("com.example.dao"));
        assertTrue(json.contains("incoming"));

        AggregateSummaryOutput.CrossPackageDep parsed =
                GSON.fromJson(json, AggregateSummaryOutput.CrossPackageDep.class);
        assertEquals("com.example.dao", parsed.getTargetPackage());
        assertEquals("incoming", parsed.getDirection());
    }

    @Test
    void riskCategoryEntry_serialization() {
        AggregateSummaryOutput.RiskCategoryEntry entry = new AggregateSummaryOutput.RiskCategoryEntry(
                "资源未关闭", "HIGH", "数据库连接未释放",
                java.util.Arrays.asList("OrderService.java", "PaymentService.java"));

        String json = GSON.toJson(entry);
        assertTrue(json.contains("资源未关闭"));
        assertTrue(json.contains("HIGH"));
        assertTrue(json.contains("OrderService.java"));
    }

    @Test
    void riskCategoryEntry_deserialization() {
        String json = "{\"category\":\"异常吞没\",\"severity\":\"MEDIUM\","
                + "\"description\":\"catch块空实现\","
                + "\"affectedFiles\":[\"Handler.java\"]}";
        AggregateSummaryOutput.RiskCategoryEntry entry =
                GSON.fromJson(json, AggregateSummaryOutput.RiskCategoryEntry.class);
        assertNotNull(entry);
        assertEquals("异常吞没", entry.getCategory());
        assertEquals("MEDIUM", entry.getSeverity());
        assertEquals("catch块空实现", entry.getDescription());
        assertNotNull(entry.getAffectedFiles());
        assertEquals(1, entry.getAffectedFiles().size());
        assertEquals("Handler.java", entry.getAffectedFiles().get(0));
    }

    @Test
    void riskCategoryEntry_inFullJson() {
        String json = "{"
                + "\"packageName\":\"com.example.service\","
                + "\"riskCategories\":["
                + "  {\"category\":\"资源未关闭\",\"severity\":\"HIGH\",\"description\":\"连接泄漏\",\"affectedFiles\":[\"A.java\"]},"
                + "  {\"category\":\"异常吞没\",\"severity\":\"MEDIUM\",\"description\":\"空catch\",\"affectedFiles\":[\"B.java\"]}"
                + "],"
                + "\"summary\":\"test\""
                + "}";
        AggregateSummaryOutput output = GSON.fromJson(json, AggregateSummaryOutput.class);
        assertNotNull(output);
        assertNotNull(output.getRiskCategories());
        assertEquals(2, output.getRiskCategories().size());
        assertEquals("资源未关闭", output.getRiskCategories().get(0).getCategory());
        assertEquals("HIGH", output.getRiskCategories().get(0).getSeverity());
        assertEquals("异常吞没", output.getRiskCategories().get(1).getCategory());
        assertEquals("MEDIUM", output.getRiskCategories().get(1).getSeverity());
        assertNotNull(output.getRiskCategories().get(1).getAffectedFiles());
        assertEquals(1, output.getRiskCategories().get(1).getAffectedFiles().size());
    }
}
