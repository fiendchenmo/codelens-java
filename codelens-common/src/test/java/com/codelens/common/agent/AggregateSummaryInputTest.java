package com.codelens.common.agent;

import com.codelens.common.models.ArchitectureLayer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AggregateSummaryInput} 序列化与反序列化测试。
 */
class AggregateSummaryInputTest {

    private static final Gson GSON = new GsonBuilder().create();

    @Test
    void serializeToJson() {
        AggregateSummaryInput input = createSampleInput();
        String json = GSON.toJson(input);
        assertNotNull(json);
        assertTrue(json.contains("com.example.service"));
        assertTrue(json.contains("OrderService"));
        assertTrue(json.contains("SERVICE"));
    }

    @Test
    void deserializeFromJson() {
        String json = "{"
                + "\"packageName\":\"com.example.service\","
                + "\"fileSummaries\":["
                + "  {\"fileName\":\"OrderService.java\",\"layer\":\"SERVICE\","
                + "   \"summary\":\"订单服务\",\"framework\":\"Spring\","
                + "   \"overallDesign\":\"处理订单\",\"riskSummary\":\"无\","
                + "   \"coreMethods\":[\"createOrder\"],"
                + "   \"calledByExternal\":[\"OrderController\"]}"
                + "],"
                + "\"crossPackageDeps\":["
                + "  {\"targetPackage\":\"com.example.repository\","
                + "   \"viaMethods\":[\"orderMapper.insert\"],"
                + "   \"direction\":\"outgoing\"}"
                + "],"
                + "\"layerDistribution\":{\"SERVICE\":3,\"CONTROLLER\":1}"
                + "}";
        AggregateSummaryInput input = GSON.fromJson(json, AggregateSummaryInput.class);
        assertNotNull(input);
        assertEquals("com.example.service", input.getPackageName());
        assertNotNull(input.getFileSummaries());
        assertEquals(1, input.getFileSummaries().size());
        assertEquals("OrderService.java", input.getFileSummaries().get(0).getFileName());
        assertEquals(ArchitectureLayer.SERVICE, input.getFileSummaries().get(0).getLayer());
        assertNotNull(input.getCrossPackageDeps());
        assertEquals(1, input.getCrossPackageDeps().size());
        assertEquals("com.example.repository", input.getCrossPackageDeps().get(0).getTargetPackage());
        assertNotNull(input.getLayerDistribution());
        assertTrue(input.getLayerDistribution().containsKey(ArchitectureLayer.SERVICE));
    }

    @Test
    void defaultValues() {
        AggregateSummaryInput input = new AggregateSummaryInput();
        assertNull(input.getPackageName());
        assertNull(input.getFileSummaries());
        assertNull(input.getCrossPackageDeps());
        assertNull(input.getLayerDistribution());
    }

    @Test
    void fileSummaryEntryDefaults() {
        AggregateSummaryInput.FileSummaryEntry entry = new AggregateSummaryInput.FileSummaryEntry();
        assertNull(entry.getFileName());
        assertNull(entry.getLayer());
        assertNull(entry.getSummary());
        assertNull(entry.getCoreMethods());
        assertNull(entry.getCalledByExternal());
    }

    @Test
    void crossPackageDepDefaults() {
        AggregateSummaryInput.CrossPackageDep dep = new AggregateSummaryInput.CrossPackageDep();
        assertNull(dep.getTargetPackage());
        assertNull(dep.getViaMethods());
        assertNull(dep.getDirection());
    }

    @Test
    void serializeFileSummaryEntry() {
        AggregateSummaryInput.FileSummaryEntry entry = new AggregateSummaryInput.FileSummaryEntry(
                "TestService.java", ArchitectureLayer.SERVICE,
                "测试服务", "Spring", "设计良好", "无风险",
                Arrays.asList("doSomething"), Arrays.asList("TestController"));
        String json = GSON.toJson(entry);
        assertTrue(json.contains("TestService.java"));
        assertTrue(json.contains("SERVICE"));
    }

    @Test
    void serializeCrossPackageDep() {
        AggregateSummaryInput.CrossPackageDep dep = new AggregateSummaryInput.CrossPackageDep(
                "com.example.dao", Arrays.asList("findById"), "outgoing");
        String json = GSON.toJson(dep);
        assertTrue(json.contains("com.example.dao"));
        assertTrue(json.contains("outgoing"));
    }

    private static AggregateSummaryInput createSampleInput() {
        AggregateSummaryInput.FileSummaryEntry entry = new AggregateSummaryInput.FileSummaryEntry(
                "OrderService.java", ArchitectureLayer.SERVICE,
                "订单核心服务", "Spring Boot", "基于策略模式", "低风险",
                Arrays.asList("createOrder", "cancelOrder"),
                Arrays.asList("OrderController"));

        AggregateSummaryInput.CrossPackageDep dep = new AggregateSummaryInput.CrossPackageDep(
                "com.example.repository", Arrays.asList("orderMapper.insert"), "outgoing");

        Map<ArchitectureLayer, Integer> dist = new HashMap<>();
        dist.put(ArchitectureLayer.SERVICE, 3);
        dist.put(ArchitectureLayer.CONTROLLER, 1);

        return new AggregateSummaryInput(
                "com.example.service",
                Collections.singletonList(entry),
                Collections.singletonList(dep),
                dist);
    }
}
