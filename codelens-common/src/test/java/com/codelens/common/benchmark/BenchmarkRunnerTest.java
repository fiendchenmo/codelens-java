package com.codelens.common.benchmark;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BenchmarkRunnerTest {

    @Test
    public void testRun_AllSamplesPass() throws Exception {
        SampleLoader loader = new SampleLoader(null) {
            @Override
            public List<SampleLoader.Sample> loadAll() {
                return Arrays.asList(
                        new SampleLoader.Sample("test1", "code1", "{\"result\":\"ok\"}"),
                        new SampleLoader.Sample("test2", "code2", "{\"result\":\"ok\"}")
                );
            }
        };

        BenchmarkRunner runner = new BenchmarkRunner();
        List<BenchmarkResult> results = runner.run(loader, sample -> {
            Thread.sleep(10); // 模拟处理耗时
            return new BenchmarkResult(sample.getName(), 0.9, 0.8, 100, 10, true);
        });

        assertEquals(2, results.size());
        assertTrue(results.get(0).isPassed());
        assertTrue(results.get(1).isPassed());
        assertEquals("test1", results.get(0).getSampleName());
        assertEquals("test2", results.get(1).getSampleName());
        assertTrue(results.get(0).getLatencyMs() >= 10);
    }

    @Test
    public void testRun_HandlerThrowsException() throws Exception {
        SampleLoader loader = new SampleLoader(null) {
            @Override
            public List<SampleLoader.Sample> loadAll() {
                return Arrays.asList(
                        new SampleLoader.Sample("good", "code", null),
                        new SampleLoader.Sample("bad", "code", null)
                );
            }
        };

        BenchmarkRunner runner = new BenchmarkRunner();
        List<BenchmarkResult> results = runner.run(loader, sample -> {
            if ("bad".equals(sample.getName())) {
                throw new RuntimeException("处理失败");
            }
            return new BenchmarkResult(sample.getName(), 0.5, 0.5, 50, 5, true);
        });

        assertEquals(2, results.size());
        assertTrue(results.get(0).isPassed());
        assertFalse(results.get(1).isPassed());
    }
}
