package com.codelens.common.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ExecutionTraceTest {

    @Test
    public void testTraceCreation() {
        ExecutionTrace trace = new ExecutionTrace("task-1", TaskType.SUMMARY,
                ExecutionStatus.COMPLETED, false, 0, 100);

        assertEquals("task-1", trace.getTaskId());
        assertEquals(TaskType.SUMMARY, trace.getTaskType());
        assertEquals(ExecutionStatus.COMPLETED, trace.getStatus());
        assertFalse(trace.isCacheHit());
        assertEquals(0, trace.getRetryCount());
        assertEquals(100, trace.getLatencyMs());
    }

    @Test
    public void testCacheHitFlag() {
        ExecutionTrace cached = new ExecutionTrace("t1", TaskType.METHOD_ANALYSIS,
                ExecutionStatus.CACHED, true, 0, 0);
        assertTrue(cached.isCacheHit());
        assertEquals(ExecutionStatus.CACHED, cached.getStatus());
    }
}
