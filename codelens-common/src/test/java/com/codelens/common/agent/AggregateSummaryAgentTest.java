package com.codelens.common.agent;

import com.codelens.common.agent.AggregateSummaryInput.CrossPackageDep;
import com.codelens.common.agent.AggregateSummaryInput.FileSummaryEntry;
import com.codelens.common.cache.CacheConfig;
import com.codelens.common.cache.FileSystemCache;
import com.codelens.common.cache.GranularCacheAdapter;
import com.codelens.common.llm.StubLLMClient;
import com.codelens.common.models.ArchitectureLayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AggregateSummaryAgent} 测试。
 */
class AggregateSummaryAgentTest {

    @TempDir
    Path tempDir;

    private GranularCacheAdapter cache;
    private List<FileSummaryEntry> sampleFiles;

    @BeforeEach
    void setUp() {
        CacheConfig config = CacheConfig.defaults(tempDir.toString());
        FileSystemCache fsc = new FileSystemCache(config);
        cache = new GranularCacheAdapter(fsc);

        sampleFiles = new ArrayList<>();
        sampleFiles.add(new FileSummaryEntry("OrderService.java", ArchitectureLayer.SERVICE,
                "订单服务", "Spring", "订单处理", "低",
                Arrays.asList("createOrder"), Arrays.asList("OrderController")));
        sampleFiles.add(new FileSummaryEntry("PaymentService.java", ArchitectureLayer.SERVICE,
                "支付服务", "Spring", "支付处理", "中",
                Arrays.asList("pay"), new ArrayList<String>()));
    }

    @AfterEach
    void tearDown() {
        cache = null;
    }

    @Test
    void directAggregate_withinThreshold() {
        // ≤10 文件直接聚合
        String validJson = "{"
                + "\"packageName\":\"com.example.service\","
                + "\"architectureLayer\":\"SERVICE\","
                + "\"layerComposition\":\"100% SERVICE\","
                + "\"summary\":\"订单与支付服务包\","
                + "\"coreEntries\":[\"OrderService\",\"PaymentService\"],"
                + "\"coreResponsibilities\":[\"订单处理\",\"支付处理\"],"
                + "\"crossPackageDeps\":[],"
                + "\"riskOverview\":\"\","
                + "\"totalFiles\":2,"
                + "\"totalMethods\":10,"
                + "\"highRiskCount\":0,"
                + "\"mediumRiskCount\":1"
                + "}";
        StubLLMClient llm = new StubLLMClient(validJson);
        AggregateSummaryAgent agent = new AggregateSummaryAgent(llm, cache);

        AggregateSummaryInput input = new AggregateSummaryInput(
                "com.example.service", sampleFiles,
                new ArrayList<CrossPackageDep>(),
                new HashMap<ArchitectureLayer, Integer>());

        AggregateSummaryOutput output = agent.execute(input);
        assertNotNull(output);
        assertEquals("com.example.service", output.getPackageName());
        assertEquals("订单与支付服务包", output.getSummary());
        assertEquals(1, llm.getCallCount());
    }

    @Test
    void cacheHit_returnsCachedResult() {
        String validJson = "{"
                + "\"packageName\":\"com.example.service\","
                + "\"architectureLayer\":\"SERVICE\","
                + "\"layerComposition\":\"100% SERVICE\","
                + "\"summary\":\"缓存命中测试\","
                + "\"coreEntries\":[\"ServiceA\"],"
                + "\"coreResponsibilities\":[\"处理\"],"
                + "\"crossPackageDeps\":[],"
                + "\"riskOverview\":\"\","
                + "\"totalFiles\":2,"
                + "\"totalMethods\":5,"
                + "\"highRiskCount\":0,"
                + "\"mediumRiskCount\":0"
                + "}";
        StubLLMClient llm = new StubLLMClient(validJson);
        AggregateSummaryAgent agent = new AggregateSummaryAgent(llm, cache);

        AggregateSummaryInput input = new AggregateSummaryInput(
                "com.example.service", sampleFiles,
                new ArrayList<CrossPackageDep>(),
                new HashMap<ArchitectureLayer, Integer>());

        // 第一次调用 — LLM 调用
        AggregateSummaryOutput first = agent.execute(input);
        assertNotNull(first);

        int callCountAfterFirst = llm.getCallCount();

        // 第二次调用同一 input — 应命中缓存
        AggregateSummaryOutput second = agent.execute(input);
        assertNotNull(second);
        // 若缓存命中，LLM 调用次数应不变
        assertEquals(callCountAfterFirst, llm.getCallCount());
    }

    @Test
    void cacheMiss_newInput() {
        String validJson = "{"
                + "\"packageName\":\"com.example.util\","
                + "\"architectureLayer\":\"UTIL\","
                + "\"layerComposition\":\"100% UTIL\","
                + "\"summary\":\"工具包\","
                + "\"coreEntries\":[],"
                + "\"coreResponsibilities\":[],"
                + "\"crossPackageDeps\":[],"
                + "\"riskOverview\":\"\","
                + "\"totalFiles\":1,"
                + "\"totalMethods\":3,"
                + "\"highRiskCount\":0,"
                + "\"mediumRiskCount\":0"
                + "}";
        StubLLMClient llm = new StubLLMClient(validJson);
        AggregateSummaryAgent agent = new AggregateSummaryAgent(llm, cache);

        List<FileSummaryEntry> otherFiles = new ArrayList<>();
        otherFiles.add(new FileSummaryEntry("StringUtil.java", ArchitectureLayer.UTIL,
                "字符串工具", "", "工具类", "",
                new ArrayList<String>(), new ArrayList<String>()));

        AggregateSummaryInput input = new AggregateSummaryInput(
                "com.example.util", otherFiles,
                new ArrayList<CrossPackageDep>(),
                new HashMap<ArchitectureLayer, Integer>());

        AggregateSummaryOutput output = agent.execute(input);
        assertNotNull(output);
        assertEquals("com.example.util", output.getPackageName());
        assertEquals("工具包", output.getSummary());
    }

    @Test
    void generateCacheKey_differentInputs_differentKeys() {
        AggregateSummaryAgent agent = new AggregateSummaryAgent(
                new StubLLMClient("{}"), cache);

        AggregateSummaryInput input1 = new AggregateSummaryInput(
                "pkg", sampleFiles, new ArrayList<CrossPackageDep>(),
                new HashMap<ArchitectureLayer, Integer>());

        List<FileSummaryEntry> otherFiles = new ArrayList<>();
        otherFiles.add(new FileSummaryEntry("Other.java", ArchitectureLayer.UTIL,
                "", "", "", "",
                new ArrayList<String>(), new ArrayList<String>()));

        AggregateSummaryInput input2 = new AggregateSummaryInput(
                "pkg", otherFiles, new ArrayList<CrossPackageDep>(),
                new HashMap<ArchitectureLayer, Integer>());

        String key1 = agent.generateCacheKey(input1);
        String key2 = agent.generateCacheKey(input2);
        assertNotNull(key1);
        assertNotNull(key2);
        assertNotEquals(key1, key2);
    }

    @Test
    void parseAndValidateOutput_invalidJson_returnsNull() {
        AggregateSummaryAgent agent = new AggregateSummaryAgent(
                new StubLLMClient(""), cache);
        AggregateSummaryInput input = new AggregateSummaryInput(
                "com.example.test", sampleFiles,
                new ArrayList<CrossPackageDep>(),
                new HashMap<ArchitectureLayer, Integer>());
        assertNull(agent.parseAndValidateOutput("not json", input));
    }

    @Test
    void parseAndValidateOutput_nullJson_returnsNull() {
        AggregateSummaryAgent agent = new AggregateSummaryAgent(
                new StubLLMClient(""), cache);
        AggregateSummaryInput input = new AggregateSummaryInput(
                "com.example.test", sampleFiles,
                new ArrayList<CrossPackageDep>(),
                new HashMap<ArchitectureLayer, Integer>());
        assertNull(agent.parseAndValidateOutput(null, input));
    }
}
