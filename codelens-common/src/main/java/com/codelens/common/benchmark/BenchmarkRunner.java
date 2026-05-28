package com.codelens.common.benchmark;

import java.util.ArrayList;
import java.util.List;

/**
 * 基准测试运行器。
 * <p>
 * 对每个测试样本运行 Agent 并收集指标。
 * Phase 3 为框架实现，不依赖实际 LLM 调用。
 */
public class BenchmarkRunner {

    /**
     * 运行基准测试。
     *
     * @param loader 样本加载器
     * @param handler 每个样本的处理逻辑（由上层提供，Phase 3 使用 Stub）
     * @return 测试结果列表
     * @throws Exception 加载或处理异常
     */
    public List<BenchmarkResult> run(SampleLoader loader, SampleHandler handler) throws Exception {
        List<BenchmarkResult> results = new ArrayList<>();
        List<SampleLoader.Sample> samples = loader.loadAll();

        if (samples.isEmpty()) {
            return results;
        }

        for (SampleLoader.Sample sample : samples) {
            long start = System.currentTimeMillis();
            boolean passed;
            double l1Coverage = 0.0;
            double l2Accuracy = 0.0;
            int tokenCount = 0;

            try {
                BenchmarkResult result = handler.handle(sample);
                if (result != null) {
                    passed = result.isPassed();
                    l1Coverage = result.getL1Coverage();
                    l2Accuracy = result.getL2Accuracy();
                    tokenCount = result.getTokenCount();
                } else {
                    passed = false;
                }
            } catch (Exception e) {
                passed = false;
            }

            long latencyMs = System.currentTimeMillis() - start;
            results.add(new BenchmarkResult(sample.getName(), l1Coverage, l2Accuracy,
                    tokenCount, latencyMs, passed));
        }

        return results;
    }

    /**
     * 样本处理回调接口。
     */
    public interface SampleHandler {
        BenchmarkResult handle(SampleLoader.Sample sample) throws Exception;
    }
}
