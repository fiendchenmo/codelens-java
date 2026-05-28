package com.codelens.common.agent;

import com.codelens.common.cache.GranularCache;
import com.codelens.common.llm.LLMClient;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

/**
 * Agent 调度框架。
 * <p>
 * 核心流程：查缓存 → 命中返回 CACHED / 未命中 → LLM 调用 → 校验 → 缓存 → 返回。
 * 校验失败最多重试 1 次，仍失败标记 SKIPPED。
 */
public class AgentRunner {

    private final LLMClient llmClient;
    private final GranularCache cache;

    public AgentRunner(LLMClient llmClient, GranularCache cache) {
        this.llmClient = llmClient;
        this.cache = cache;
    }

    /**
     * 执行摘要任务。
     *
     * @param task     SUMMARY 任务，task.getInput() = source code
     * @param metadata 索引元数据
     * @return 执行轨迹
     */
    public ExecutionTrace run(AnalysisTask<String, String> task, String metadata) {
        if (task.getTaskType() != TaskType.SUMMARY) {
            throw new IllegalArgumentException("此方法仅支持 SUMMARY 类型，实际: " + task.getTaskType());
        }
        String sourceCode = task.getInput();
        String systemPrompt = new SummaryPrompt().generateSystemPrompt();
        String userPrompt = new SummaryPrompt().generateUserPrompt(sourceCode, metadata);
        return execute(task, systemPrompt, userPrompt, new SummaryValidator(), sourceCode);
    }

    /**
     * 执行方法分析任务。
     * <p>
     * 自动确保前置 SUMMARY 已完成（优先从缓存读取，否则执行新的摘要任务）。
     *
     * @param task            METHOD_ANALYSIS 任务，task.getInput() = method body
     * @param methodSignature 方法签名
     * @param sourceCode      所属文件源码（用于 SUMMARY 缓存查找）
     * @param fileSummary     前置 SUMMARY 结果（可空，为空时自动查缓存或执行）
     * @param metadata        索引元数据
     * @return 执行轨迹
     */
    public ExecutionTrace runMethodAnalysis(AnalysisTask<String, String> task,
                                             String methodSignature,
                                             String sourceCode,
                                             String fileSummary,
                                             String metadata) {
        if (task.getTaskType() != TaskType.METHOD_ANALYSIS) {
            throw new IllegalArgumentException("此方法仅支持 METHOD_ANALYSIS 类型，实际: " + task.getTaskType());
        }

        // 确保 SUMMARY 结果可用
        String resolvedSummary = resolveSummary(sourceCode, metadata);

        String methodBody = task.getInput();
        String systemPrompt = new MethodAnalysisPrompt().generateSystemPrompt();
        String userPrompt = new MethodAnalysisPrompt().generateUserPrompt(
                methodSignature, methodBody, resolvedSummary, metadata);

        return execute(task, systemPrompt, userPrompt, new MethodAnalysisValidator(), sourceCode);
    }

    /**
     * 解析 SUMMARY 结果：优先使用传入值，其次查缓存，最后自动执行。
     */
    private String resolveSummary(String sourceCode, String metadata) {
        String summaryCacheKey = CacheGranule.generateKey(sourceCode, TaskType.SUMMARY);
        Optional<CacheGranule> cached = cache.get(summaryCacheKey);
        if (cached.isPresent()) {
            return cached.get().getOutput();
        }

        // 自动执行 SUMMARY
        AnalysisTask<String, String> summaryTask = new AnalysisTask<>(TaskType.SUMMARY, sourceCode);
        ExecutionTrace trace = run(summaryTask, metadata);
        if (trace.getStatus() == ExecutionStatus.COMPLETED || trace.getStatus() == ExecutionStatus.CACHED) {
            return summaryTask.getOutput();
        }
        return "";
    }

    /**
     * 核心执行逻辑。
     */
    private ExecutionTrace execute(AnalysisTask<String, String> task,
                                    String systemPrompt, String userPrompt,
                                    Object validator, String cacheInput) {
        String contentHash = CacheGranule.generateKey(cacheInput, task.getTaskType());

        // 查缓存
        Optional<CacheGranule> cached = cache.get(contentHash);
        if (cached.isPresent()) {
            task.setOutput(cached.get().getOutput());
            return new ExecutionTrace(task.getTaskId(), task.getTaskType(),
                    ExecutionStatus.CACHED, true, 0, 0);
        }

        task.setStatus(ExecutionStatus.RUNNING);
        long start = System.currentTimeMillis();
        int retryCount = 0;
        String llmOutput = null;
        boolean success = false;

        // 初始调用
        llmOutput = llmClient.chat(systemPrompt, userPrompt);
        ValidationResult vr = invokeValidator(validator, llmOutput);
        success = vr.isValid();

        // 最多重试 1 次
        if (!success) {
            retryCount = 1;
            llmOutput = llmClient.chat(systemPrompt, userPrompt);
            vr = invokeValidator(validator, llmOutput);
            success = vr.isValid();
        }

        long latencyMs = System.currentTimeMillis() - start;

        if (success) {
            task.setOutput(llmOutput);
            CacheGranule granule = CacheGranule.builder()
                    .taskType(task.getTaskType())
                    .version("1.0")
                    .contentType("json")
                    .contentHash(contentHash)
                    .modelId("stub")
                    .output(llmOutput)
                    .createdAt(System.currentTimeMillis())
                    .invalidatedBy(Collections.<String>emptyList())
                    .build();
            cache.put(granule);
            return new ExecutionTrace(task.getTaskId(), task.getTaskType(),
                    ExecutionStatus.COMPLETED, false, retryCount, latencyMs);
        } else {
            task.setStatus(ExecutionStatus.FAILED);
            return new ExecutionTrace(task.getTaskId(), task.getTaskType(),
                    ExecutionStatus.SKIPPED, false, retryCount, latencyMs);
        }
    }

    /**
     * 通过反射调用 Validator 的 validate 方法。
     * SummaryValidator.validate(String) / MethodAnalysisValidator.validate(String, String)
     */
    private ValidationResult invokeValidator(Object validator, String llmOutput) {
        try {
            if (validator instanceof SummaryValidator) {
                return ((SummaryValidator) validator).validate(llmOutput);
            } else if (validator instanceof MethodAnalysisValidator) {
                return ((MethodAnalysisValidator) validator).validate(llmOutput, null);
            }
            // 兜底反射调用
            Method validateMethod = findValidateMethod(validator.getClass());
            if (validateMethod.getParameterCount() == 1) {
                return (ValidationResult) validateMethod.invoke(validator, llmOutput);
            } else {
                return (ValidationResult) validateMethod.invoke(validator, llmOutput, null);
            }
        } catch (Exception e) {
            return ValidationResult.fail("root", "校验器调用失败: " + e.getMessage());
        }
    }

    private Method findValidateMethod(Class<?> clazz) {
        for (Method m : clazz.getMethods()) {
            if ("validate".equals(m.getName()) && ValidationResult.class.equals(m.getReturnType())) {
                return m;
            }
        }
        throw new IllegalArgumentException(clazz.getSimpleName() + " 没有 validate 方法");
    }
}
