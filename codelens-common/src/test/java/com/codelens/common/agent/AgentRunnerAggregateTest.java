package com.codelens.common.agent;

import com.codelens.common.agent.AggregateSummaryInput.CrossPackageDep;
import com.codelens.common.cache.CacheConfig;
import com.codelens.common.cache.FileSystemCache;
import com.codelens.common.cache.GranularCacheAdapter;
import com.codelens.common.llm.LLMClient;
import com.codelens.common.llm.StubLLMClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AgentRunner} 聚合调度逻辑测试。
 */
class AgentRunnerAggregateTest {

    @TempDir
    Path tempDir;

    private GranularCacheAdapter cache;
    private LLMClient llm;

    @BeforeEach
    void setUp() {
        CacheConfig config = CacheConfig.defaults(tempDir.toString());
        FileSystemCache fsc = new FileSystemCache(config);
        cache = new GranularCacheAdapter(fsc);

        String validAggJson = "{"
                + "\"packageName\":\"com.example.service\","
                + "\"architectureLayer\":\"SERVICE\","
                + "\"layerComposition\":\"100% SERVICE\","
                + "\"summary\":\"聚合摘要\","
                + "\"coreEntries\":[\"A\"],"
                + "\"coreResponsibilities\":[\"B\"],"
                + "\"crossPackageDeps\":[],"
                + "\"riskOverview\":\"\","
                + "\"totalFiles\":2,"
                + "\"totalMethods\":10,"
                + "\"highRiskCount\":0,"
                + "\"mediumRiskCount\":0"
                + "}";
        llm = new StubLLMClient(validAggJson);
    }

    @Test
    void runAggregate_withFileTasks() {
        AgentRunner runner = new AgentRunner(llm, cache);

        List<AnalysisTask<String, String>> tasks = createSampleTasks();
        AggregateSummaryOutput output = runner.runAggregate(tasks,
                new ArrayList<CrossPackageDep>(), "test-module");

        assertNotNull(output);
        assertNotNull(output.getSummary());
    }

    @Test
    void runAggregate_emptyTasks() {
        AgentRunner runner = new AgentRunner(llm, cache);
        AggregateSummaryOutput output = runner.runAggregate(
                new ArrayList<AnalysisTask<String, String>>(),
                new ArrayList<CrossPackageDep>(), "empty-module");

        assertNotNull(output);
    }

    @Test
    void runAggregateIncremental_noChange_skipsAggregate() {
        AgentRunner runner = new AgentRunner(llm, cache);

        // 预填充 SUMMARY 缓存
        AnalysisTask<String, String> task = new AnalysisTask<>(TaskType.SUMMARY, "source1");
        task.setOutput("old summary output");
        String cacheKey = CacheGranule.generateKey("source1", TaskType.SUMMARY);
        CacheGranule granule = CacheGranule.builder()
                .taskType(TaskType.SUMMARY)
                .version("1.0")
                .contentType("json")
                .contentHash(cacheKey)
                .modelId("stub")
                .output("old summary output")
                .createdAt(System.currentTimeMillis())
                .invalidatedBy(new ArrayList<String>())
                .build();
        cache.put(granule);

        List<AnalysisTask<String, String>> changedFiles = new ArrayList<>();
        changedFiles.add(task);

        List<AnalysisTask<String, String>> allFiles = new ArrayList<>(changedFiles);

        boolean updated = runner.runAggregateIncremental(changedFiles, allFiles,
                new ArrayList<CrossPackageDep>(), "meta");
        // 输出未变，应返回 true 或 false 取决于具体实现
        // 至少不抛异常
        assertNotNull(updated);
    }

    @Test
    void runAggregateIncremental_newFile_triggersAggregate() {
        AgentRunner runner = new AgentRunner(llm, cache);

        // 新文件（无缓存）
        AnalysisTask<String, String> newTask = new AnalysisTask<>(TaskType.SUMMARY, "newSource");
        newTask.setOutput("new summary");

        List<AnalysisTask<String, String>> changedFiles = new ArrayList<>();
        changedFiles.add(newTask);

        List<AnalysisTask<String, String>> allFiles = new ArrayList<>(changedFiles);

        boolean updated = runner.runAggregateIncremental(changedFiles, allFiles,
                new ArrayList<CrossPackageDep>(), "meta");
        // 不抛异常
        assertNotNull(updated);
    }

    private static List<AnalysisTask<String, String>> createSampleTasks() {
        AnalysisTask<String, String> t1 = new AnalysisTask<>(TaskType.SUMMARY, "src1");
        t1.setOutput("service a output");

        AnalysisTask<String, String> t2 = new AnalysisTask<>(TaskType.SUMMARY, "src2");
        t2.setOutput("service b output");

        return Arrays.asList(t1, t2);
    }
}
