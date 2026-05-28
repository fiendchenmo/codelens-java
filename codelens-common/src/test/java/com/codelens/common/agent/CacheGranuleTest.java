package com.codelens.common.agent;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class CacheGranuleTest {

    @Test
    public void testGenerateKey_SameInputSameHash() {
        String hash1 = CacheGranule.generateKey("source code", TaskType.SUMMARY);
        String hash2 = CacheGranule.generateKey("source code", TaskType.SUMMARY);

        assertEquals(hash1, hash2, "相同输入应产生相同 hash");
        assertEquals(64, hash1.length());
    }

    @Test
    public void testGenerateKey_DifferentInputDifferentHash() {
        String hash1 = CacheGranule.generateKey("source code", TaskType.SUMMARY);
        String hash2 = CacheGranule.generateKey("different source", TaskType.SUMMARY);

        assertNotEquals(hash1, hash2, "不同输入应产生不同 hash");
    }

    @Test
    public void testGenerateKey_DifferentTypeDifferentHash() {
        String hash1 = CacheGranule.generateKey("source code", TaskType.SUMMARY);
        String hash2 = CacheGranule.generateKey("source code", TaskType.METHOD_ANALYSIS);

        assertNotEquals(hash1, hash2, "不同任务类型应产生不同 hash");
    }

    @Test
    public void testBuilder_AllFields() {
        long now = System.currentTimeMillis();
        CacheGranule granule = CacheGranule.builder()
                .taskType(TaskType.SUMMARY)
                .version("1.0")
                .contentType("application/json")
                .contentHash("abc123")
                .modelId("deepseek-v4-flash")
                .output("{\"result\":\"ok\"}")
                .createdAt(now)
                .invalidatedBy(Collections.singletonList("OldService.java"))
                .build();

        assertEquals(TaskType.SUMMARY, granule.getTaskType());
        assertEquals("1.0", granule.getVersion());
        assertEquals("application/json", granule.getContentType());
        assertEquals("abc123", granule.getContentHash());
        assertEquals("deepseek-v4-flash", granule.getModelId());
        assertEquals("{\"result\":\"ok\"}", granule.getOutput());
        assertEquals(now, granule.getCreatedAt());
        assertEquals(1, granule.getInvalidatedBy().size());
        assertTrue(granule.getInvalidatedBy().contains("OldService.java"));
    }

    @Test
    public void testConstructor_AllFields() {
        long now = System.currentTimeMillis();
        CacheGranule granule = new CacheGranule(
                TaskType.METHOD_ANALYSIS, "2.0", "text/plain",
                "def456", "gpt-4", "output text",
                now, Collections.emptyList());

        assertEquals(TaskType.METHOD_ANALYSIS, granule.getTaskType());
        assertEquals("2.0", granule.getVersion());
        assertEquals("text/plain", granule.getContentType());
        assertEquals("def456", granule.getContentHash());
        assertEquals("gpt-4", granule.getModelId());
        assertEquals("output text", granule.getOutput());
        assertEquals(now, granule.getCreatedAt());
        assertTrue(granule.getInvalidatedBy().isEmpty());
    }
}
