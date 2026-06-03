package com.codelens.common.agent;

import com.codelens.common.agent.AggregateSummaryInput.FileSummaryEntry;
import com.codelens.common.analyzer.ArchitectureLayerDetector;
import com.codelens.common.cache.GranularCache;
import com.codelens.common.llm.LLMClient;
import com.codelens.common.models.ArchitectureLayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final boolean noValidate;

    public AgentRunner(LLMClient llmClient, GranularCache cache) {
        this(llmClient, cache, false);
    }

    public AgentRunner(LLMClient llmClient, GranularCache cache, boolean noValidate) {
        this.llmClient = llmClient;
        this.cache = cache;
        this.noValidate = noValidate;
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
     * 执行包级聚合摘要。
     * <p>
     * 等子 Task 完成 → 收集 File Summary → 组装 AggregateSummaryInput → 调 AggregateSummaryAgent。
     * 支持增量更新：文件修改时对比输出，变了才重跑 Package Summary。
     *
     * @param fileTasks    已完成的文件级 SUMMARY 任务列表
     * @param crossDeps    跨包依赖列表
     * @param moduleName   模块名（用于缓存键）
     * @return 聚合摘要输出
     */
    public AggregateSummaryOutput runAggregate(List<AnalysisTask<String, String>> fileTasks,
                                                 List<AggregateSummaryInput.CrossPackageDep> crossDeps,
                                                 String moduleName) {
        return runAggregate(fileTasks, crossDeps, moduleName, "");
    }

    /**
     * 执行包级聚合摘要（带包名）。
     *
     * @param fileTasks    已完成的文件级 SUMMARY 任务列表
     * @param crossDeps    跨包依赖列表
     * @param moduleName   模块名（用于缓存键）
     * @param packageName  包名（用于覆盖 LLM 输出中的 packageName）
     * @return 聚合摘要输出
     */
    public AggregateSummaryOutput runAggregate(List<AnalysisTask<String, String>> fileTasks,
                                                 List<AggregateSummaryInput.CrossPackageDep> crossDeps,
                                                 String moduleName,
                                                 String packageName) {
        AggregateSummaryInput input = buildAggregateInput(fileTasks, crossDeps, packageName);
        AggregateSummaryAgent agent = new AggregateSummaryAgent(llmClient, cache);
        return agent.execute(input);
    }

    /**
     * 增量更新聚合摘要。
     * <p>
     * 对已修改的文件重新执行 SUMMARY，比对输出是否确实变化。
     * 仅当子级输出真正改变时才重跑 AGGREGATE_SUMMARY。
     *
     * @param changedFiles 已修改的文件列表（新 AnalysisTask，含新 sourceCode）
     * @param allFiles     所有文件的 AnalysisTask（含未修改的）
     * @param crossDeps    跨包依赖列表
     * @param metadata     索引元数据
     * @return true 表示 Package Summary 已更新
     */
    public boolean runAggregateIncremental(List<AnalysisTask<String, String>> changedFiles,
                                            List<AnalysisTask<String, String>> allFiles,
                                            List<AggregateSummaryInput.CrossPackageDep> crossDeps,
                                            String metadata) {
        boolean anySummaryChanged = false;

        // 重跑已修改文件的 SUMMARY，比对输出
        for (AnalysisTask<String, String> task : changedFiles) {
            if (task.getTaskType() != TaskType.SUMMARY) continue;

            // 获取旧的输出（缓存中的）
            String oldOutput = task.getOutput();
            String cacheKey = CacheGranule.generateKey(task.getInput(), TaskType.SUMMARY);
            Optional<CacheGranule> cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                oldOutput = cached.get().getOutput();
            }

            // 重新执行 SUMMARY
            run(task, metadata);

            String newOutput = task.getOutput();
            // 仅当输出确实变了才标记
            if (oldOutput != null && !oldOutput.equals(newOutput)) {
                anySummaryChanged = true;
            } else if (oldOutput == null && newOutput != null) {
                anySummaryChanged = true;
            }
        }

        // 有增删（changedFiles 包含新文件）或 SUMMARY 输出变化时重跑聚合
        if (anySummaryChanged || hasFileChanges(changedFiles, allFiles)) {
            runAggregate(allFiles, crossDeps, "");
            return true;
        }
        return false;
    }

    /**
     * 检查是否有文件增删（新增或删除的文件）。
     */
    private boolean hasFileChanges(List<AnalysisTask<String, String>> changedFiles,
                                    List<AnalysisTask<String, String>> allFiles) {
        // 如果 changedFiles 中的 task 没有 oldOutput（是新文件），或某文件不再在 allFiles 中
        for (AnalysisTask<String, String> task : changedFiles) {
            String cacheKey = CacheGranule.generateKey(task.getInput(), TaskType.SUMMARY);
            Optional<CacheGranule> cached = cache.get(cacheKey);
            if (!cached.isPresent()) {
                return true; // 新文件，无缓存
            }
        }
        return false;
    }

    /**
     * 将文件级任务列表组装为 AggregateSummaryInput（从第一个文件推断包名）。
     */
    private AggregateSummaryInput buildAggregateInput(
            List<AnalysisTask<String, String>> fileTasks,
            List<AggregateSummaryInput.CrossPackageDep> crossDeps) {
        return buildAggregateInput(fileTasks, crossDeps, "");
    }

    /**
     * 将文件级任务列表组装为 AggregateSummaryInput（使用传入包名）。
     *
     * @param fileTasks   已完成的文件级 SUMMARY 任务列表
     * @param crossDeps   跨包依赖列表
     * @param packageName 包名（为空时从第一个文件推断）
     */
    private AggregateSummaryInput buildAggregateInput(
            List<AnalysisTask<String, String>> fileTasks,
            List<AggregateSummaryInput.CrossPackageDep> crossDeps,
            String packageName) {
        if (fileTasks == null || fileTasks.isEmpty()) {
            return new AggregateSummaryInput(
                    packageName != null ? packageName : "",
                    new ArrayList<FileSummaryEntry>(),
                    crossDeps != null ? crossDeps : new ArrayList<AggregateSummaryInput.CrossPackageDep>(),
                    new HashMap<ArchitectureLayer, Integer>());
        }

        if (packageName == null) packageName = "";
        List<FileSummaryEntry> entries = new ArrayList<FileSummaryEntry>();
        Map<ArchitectureLayer, Integer> layerDist = new HashMap<ArchitectureLayer, Integer>();

        for (AnalysisTask<String, String> task : fileTasks) {
            if (task.getTaskType() != TaskType.SUMMARY) continue;
            String output = task.getOutput();
            if (output == null || output.trim().isEmpty()) continue;

            // 从输出中解析文件名和层信息（简化：使用任务 id 作为文件名）
            String fileName = task.getTaskId() + ".java";
            String summary = output;
            ArchitectureLayer layer = ArchitectureLayerDetector.detectClassLayer(
                    null, task.getTaskId(), packageName);
            if (layer == null) {
                layer = ArchitectureLayer.UNKNOWN;
            }

            FileSummaryEntry entry = new FileSummaryEntry(
                    fileName, layer, summary, "", "", "",
                    new ArrayList<String>(), new ArrayList<String>());
            entries.add(entry);

            // 更新层分布
            Integer count = layerDist.get(layer);
            layerDist.put(layer, count != null ? count + 1 : 1);
        }

        return new AggregateSummaryInput(
                packageName,
                entries,
                crossDeps != null ? crossDeps : new ArrayList<AggregateSummaryInput.CrossPackageDep>(),
                layerDist);
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
            // Post-validation for METHOD_ANALYSIS: validate claims against source code
            String finalOutput = llmOutput;
            if (!noValidate && task.getTaskType() == TaskType.METHOD_ANALYSIS && cacheInput != null) {
                try {
                    finalOutput = ValidationPostProcessor.process(llmOutput, cacheInput);
                } catch (Exception e) {
                    // Post-validation failure must NOT break main flow
                }
            }

            task.setOutput(finalOutput);
            CacheGranule granule = CacheGranule.builder()
                    .taskType(task.getTaskType())
                    .version("1.0")
                    .contentType("json")
                    .contentHash(contentHash)
                    .modelId("stub")
                    .output(finalOutput)
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
