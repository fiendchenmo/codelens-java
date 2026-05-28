package com.codelens.common.cache;

import com.codelens.common.agent.CacheGranule;
import com.codelens.common.agent.TaskType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class GranularCacheAdapterTest {

    private Path tempDir;
    private GranularCacheAdapter adapter;

    @BeforeEach
    public void setUp() {
        try {
            tempDir = Files.createTempDirectory("codelens-granular-test");
            CacheConfig config = new CacheConfig(tempDir.toString(), 0, 0);
            FileSystemCache fsCache = new FileSystemCache(config);
            adapter = new GranularCacheAdapter(fsCache);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up test", e);
        }
    }

    @AfterEach
    public void tearDown() {
        try {
            deleteDirectory(tempDir.toFile());
        } catch (Exception ignored) {
        }
    }

    @Test
    public void testPutAndGet() {
        CacheGranule granule = CacheGranule.builder()
                .taskType(TaskType.SUMMARY)
                .version("1.0")
                .contentType("application/json")
                .contentHash("test-hash-1")
                .modelId("test-model")
                .output("{\"result\":\"ok\"}")
                .createdAt(System.currentTimeMillis())
                .invalidatedBy(Collections.emptyList())
                .build();

        adapter.put(granule);

        Optional<CacheGranule> found = adapter.get("test-hash-1");
        assertTrue(found.isPresent(), "写入后应能读取");
        assertEquals(TaskType.SUMMARY, found.get().getTaskType());
        assertEquals("{\"result\":\"ok\"}", found.get().getOutput());
    }

    @Test
    public void testGet_NotFound() {
        Optional<CacheGranule> result = adapter.get("non-existent-hash");
        assertFalse(result.isPresent(), "不存在的 hash 应返回 empty");
    }

    @Test
    public void testInvalidate() {
        CacheGranule granule = CacheGranule.builder()
                .taskType(TaskType.SUMMARY)
                .version("1.0")
                .contentType("application/json")
                .contentHash("to-delete")
                .modelId("test")
                .output("data")
                .createdAt(System.currentTimeMillis())
                .invalidatedBy(Collections.emptyList())
                .build();

        adapter.put(granule);
        assertTrue(adapter.get("to-delete").isPresent());

        adapter.invalidate("to-delete");
        assertFalse(adapter.get("to-delete").isPresent(), "失效后应不可读取");
    }

    @Test
    public void testInvalidateByFile() {
        CacheGranule granule = CacheGranule.builder()
                .taskType(TaskType.SUMMARY)
                .version("1.0")
                .contentType("application/json")
                .contentHash("file-linked")
                .modelId("test")
                .output("data")
                .createdAt(System.currentTimeMillis())
                .invalidatedBy(Collections.singletonList("OldService.java"))
                .build();

        adapter.put(granule);

        adapter.invalidateByFile("OldService.java");
        assertFalse(adapter.get("file-linked").isPresent(), "匹配文件的缓存应被失效");
    }

    @Test
    public void testListByType() {
        CacheGranule summaryGranule = CacheGranule.builder()
                .taskType(TaskType.SUMMARY)
                .version("1.0")
                .contentType("application/json")
                .contentHash("s1")
                .modelId("test")
                .output("summary")
                .createdAt(System.currentTimeMillis())
                .invalidatedBy(Collections.emptyList())
                .build();

        CacheGranule methodGranule = CacheGranule.builder()
                .taskType(TaskType.METHOD_ANALYSIS)
                .version("1.0")
                .contentType("application/json")
                .contentHash("m1")
                .modelId("test")
                .output("method analysis")
                .createdAt(System.currentTimeMillis())
                .invalidatedBy(Collections.emptyList())
                .build();

        adapter.put(summaryGranule);
        adapter.put(methodGranule);

        List<CacheGranule> summaries = adapter.listByType(TaskType.SUMMARY);
        assertEquals(1, summaries.size(), "应有 1 个 SUMMARY 条目");
        assertEquals("s1", summaries.get(0).getContentHash());

        List<CacheGranule> methods = adapter.listByType(TaskType.METHOD_ANALYSIS);
        assertEquals(1, methods.size(), "应有 1 个 METHOD_ANALYSIS 条目");
        assertEquals("m1", methods.get(0).getContentHash());
    }

    @Test
    public void testDisabledCache_ReturnsEmpty() {
        CacheConfig disabledConfig = CacheConfig.disabled();
        FileSystemCache fsCache = new FileSystemCache(disabledConfig);
        GranularCacheAdapter disabledAdapter = new GranularCacheAdapter(fsCache);

        CacheGranule granule = CacheGranule.builder()
                .taskType(TaskType.SUMMARY)
                .version("1.0")
                .contentType("application/json")
                .contentHash("disabled-hash")
                .modelId("test")
                .output("data")
                .createdAt(System.currentTimeMillis())
                .invalidatedBy(Collections.emptyList())
                .build();

        disabledAdapter.put(granule);
        Optional<CacheGranule> result = disabledAdapter.get("disabled-hash");
        assertFalse(result.isPresent(), "禁用缓存时应返回 empty");
    }

    private void deleteDirectory(java.io.File dir) {
        if (dir.isDirectory()) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    deleteDirectory(f);
                }
            }
        }
        dir.delete();
    }
}
