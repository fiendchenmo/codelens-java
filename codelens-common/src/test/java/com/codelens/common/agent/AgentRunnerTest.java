package com.codelens.common.agent;

import com.codelens.common.cache.GranularCache;
import com.codelens.common.llm.StubLLMClient;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class AgentRunnerTest {

    // ========== 内存缓存实现 ==========

    private static class InMemoryCache implements GranularCache {
        final Map<String, CacheGranule> store = new LinkedHashMap<>();

        @Override
        public void put(CacheGranule granule) {
            store.put(granule.getContentHash(), granule);
        }

        @Override
        public Optional<CacheGranule> get(String contentHash) {
            return Optional.ofNullable(store.get(contentHash));
        }

        @Override
        public void invalidate(String contentHash) {
            store.remove(contentHash);
        }

        @Override
        public void invalidateByFile(String filePath) {
            Iterator<CacheGranule> it = store.values().iterator();
            while (it.hasNext()) {
                if (it.next().getInvalidatedBy().stream().anyMatch(f -> f.contains(filePath))) {
                    it.remove();
                }
            }
        }

        @Override
        public List<CacheGranule> listByType(TaskType taskType) {
            List<CacheGranule> result = new ArrayList<>();
            for (CacheGranule g : store.values()) {
                if (g.getTaskType() == taskType) {
                    result.add(g);
                }
            }
            return result;
        }
    }

    // ========== 测试数据 ==========

    private static final String VALID_SUMMARY = "{\n" +
            "  \"className\": \"com.example.OrderService\",\n" +
            "  \"stereotype\": \"SERVICE\",\n" +
            "  \"keyMethods\": [{\"name\": \"processOrder\", \"role\": \"core\", \"complexity\": 5}],\n" +
            "  \"dependencies\": [\"OrderRepository\"],\n" +
            "  \"complexity\": \"MEDIUM\"\n" +
            "}";

    private static final String VALID_METHOD_ANALYSIS = "{\n" +
            "  \"method\": \"processOrder\",\n" +
            "  \"l1Evidence\": {\n" +
            "    \"calls\": [\"validateOrder\"],\n" +
            "    \"calledBy\": [\"handleRequest\"],\n" +
            "    \"fieldsUsed\": [\"orderRepo\"]\n" +
            "  },\n" +
            "  \"l2Confidence\": {\n" +
            "    \"overallScore\": 0.85,\n" +
            "    \"reasoningBasis\": \"SOLID_ANALYSIS\",\n" +
            "    \"riskIndicators\": []\n" +
            "  }\n" +
            "}";

    // ========== 测试用例 ==========

    @Test
    public void testRunSummary_CompletesSuccessfully() {
        StubLLMClient client = new StubLLMClient(VALID_SUMMARY);
        InMemoryCache cache = new InMemoryCache();
        AgentRunner runner = new AgentRunner(client, cache);

        AnalysisTask<String, String> task = new AnalysisTask<>(TaskType.SUMMARY, "public class OrderService {}");
        ExecutionTrace trace = runner.run(task, "");

        assertEquals(ExecutionStatus.COMPLETED, trace.getStatus());
        assertFalse(trace.isCacheHit());
        assertNotNull(task.getOutput());
        assertEquals(1, client.getCallCount());
    }

    @Test
    public void testRunMethodAnalysis_CompletesSuccessfully() {
        // 先缓存 SUMMARY 结果
        InMemoryCache cache = new InMemoryCache();
        String sourceCode = "public class OrderService { public void processOrder() {} }";
        String summaryHash = CacheGranule.generateKey(sourceCode, TaskType.SUMMARY);
        cache.put(CacheGranule.builder()
                .taskType(TaskType.SUMMARY)
                .version("1.0")
                .contentType("json")
                .contentHash(summaryHash)
                .modelId("stub")
                .output(VALID_SUMMARY)
                .createdAt(System.currentTimeMillis())
                .invalidatedBy(Collections.<String>emptyList())
                .build());

        StubLLMClient client = new StubLLMClient(VALID_METHOD_ANALYSIS);
        AgentRunner runner = new AgentRunner(client, cache);

        AnalysisTask<String, String> task = new AnalysisTask<>(TaskType.METHOD_ANALYSIS, "public void processOrder() {}");
        ExecutionTrace trace = runner.runMethodAnalysis(task,
                "public void processOrder(Long orderId)",
                sourceCode, null, "");

        assertEquals(ExecutionStatus.COMPLETED, trace.getStatus());
        assertNotNull(task.getOutput());
        assertEquals(1, client.getCallCount());
    }

    @Test
    public void testRunSummary_CacheHitReturnsCached() {
        InMemoryCache cache = new InMemoryCache();
        String sourceCode = "public class Test {}";
        String hash = CacheGranule.generateKey(sourceCode, TaskType.SUMMARY);
        cache.put(CacheGranule.builder()
                .taskType(TaskType.SUMMARY)
                .version("1.0")
                .contentType("json")
                .contentHash(hash)
                .modelId("stub")
                .output(VALID_SUMMARY)
                .createdAt(System.currentTimeMillis())
                .invalidatedBy(Collections.<String>emptyList())
                .build());

        StubLLMClient client = new StubLLMClient(VALID_SUMMARY);
        AgentRunner runner = new AgentRunner(client, cache);

        AnalysisTask<String, String> task = new AnalysisTask<>(TaskType.SUMMARY, sourceCode);
        ExecutionTrace trace = runner.run(task, "");

        assertEquals(ExecutionStatus.CACHED, trace.getStatus());
        assertTrue(trace.isCacheHit());
        // 缓存命中，不应调用 LLM
        assertEquals(0, client.getCallCount());
    }

    @Test
    public void testRunSummary_RetryOnValidationFailure() {
        // 第一次返回非法 JSON（触发校验失败重试），第二次返回合法 JSON
        Queue<String> responses = new LinkedList<>();
        responses.add("invalid json");
        responses.add(VALID_SUMMARY);

        StubLLMClient client = new StubLLMClient(responses);
        InMemoryCache cache = new InMemoryCache();
        AgentRunner runner = new AgentRunner(client, cache);

        AnalysisTask<String, String> task = new AnalysisTask<>(TaskType.SUMMARY, "public class Test {}");
        ExecutionTrace trace = runner.run(task, "");

        assertEquals(ExecutionStatus.COMPLETED, trace.getStatus());
        assertEquals(1, trace.getRetryCount());
        assertEquals(2, client.getCallCount());
    }

    @Test
    public void testRunSummary_BothAttemptsFailReturnsSkipped() {
        // 始终返回非法 JSON
        StubLLMClient client = new StubLLMClient("not valid json");
        InMemoryCache cache = new InMemoryCache();
        AgentRunner runner = new AgentRunner(client, cache);

        AnalysisTask<String, String> task = new AnalysisTask<>(TaskType.SUMMARY, "public class Test {}");
        ExecutionTrace trace = runner.run(task, "");

        assertEquals(ExecutionStatus.SKIPPED, trace.getStatus());
        assertEquals(1, trace.getRetryCount(), "重试1次应记录");
        assertEquals(2, client.getCallCount(), "初始调用 + 1次重试 = 2次");
    }

    @Test
    public void testRunMethodAnalysis_AutoRunsSummary() {
        // 没有预先缓存 SUMMARY，METHOD_ANALYSIS 应自动执行 SUMMARY
        // 队列: 第一个响应给 SUMMARY (自动), 第二个响应给 METHOD_ANALYSIS
        Queue<String> responses = new LinkedList<>();
        responses.add(VALID_SUMMARY);
        responses.add(VALID_METHOD_ANALYSIS);

        StubLLMClient client = new StubLLMClient(responses);
        InMemoryCache cache = new InMemoryCache();
        AgentRunner runner = new AgentRunner(client, cache);

        String sourceCode = "public class OrderService { public void processOrder() {} }";
        AnalysisTask<String, String> task = new AnalysisTask<>(TaskType.METHOD_ANALYSIS, "public void processOrder() {}");
        ExecutionTrace trace = runner.runMethodAnalysis(task,
                "public void processOrder(Long orderId)",
                sourceCode, null, "");

        assertEquals(ExecutionStatus.COMPLETED, trace.getStatus());
        assertNotNull(task.getOutput());

        // SUMMARY 应已被缓存
        String summaryHash = CacheGranule.generateKey(sourceCode, TaskType.SUMMARY);
        assertTrue(cache.get(summaryHash).isPresent(), "SUMMARY 应自动缓存");
    }
}
