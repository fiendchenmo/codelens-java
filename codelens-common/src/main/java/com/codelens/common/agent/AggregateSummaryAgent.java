package com.codelens.common.agent;

import com.codelens.common.agent.AggregateSummaryInput.FileSummaryEntry;
import com.codelens.common.agent.AggregateSummaryInput.CrossPackageDep;
import com.codelens.common.cache.GranularCache;
import com.codelens.common.llm.LLMClient;
import com.codelens.common.analyzer.ArchitectureLayerDetector;
import com.codelens.common.models.ArchitectureLayer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 聚合摘要 Agent。
 * <p>
 * 根据包内各文件的摘要信息，生成包级和模块级的聚合摘要。
 * 支持直接聚合（≤10 文件）和分组聚合（>10 文件）两种模式。
 * </p>
 */
public class AggregateSummaryAgent {

    private static final Gson GSON = new GsonBuilder().create();
    private static final int DIRECT_THRESHOLD = 10;

    private final LLMClient llmClient;
    private final GranularCache cache;

    public AggregateSummaryAgent(LLMClient llmClient, GranularCache cache) {
        this.llmClient = llmClient;
        this.cache = cache;
    }

    /**
     * 执行包级聚合摘要。
     *
     * @param input 聚合摘要输入
     * @return 聚合摘要输出
     */
    public AggregateSummaryOutput execute(AggregateSummaryInput input) {
        String cacheKey = generateCacheKey(input);

        // 缓存查找
        Optional<CacheGranule> cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            AggregateSummaryOutput output = parseAndValidateOutput(cached.get().getOutput(), input);
            if (output != null) {
                return output;
            }
        }

        AggregateSummaryOutput result;

        if (input.getFileSummaries() == null || input.getFileSummaries().size() <= DIRECT_THRESHOLD) {
            // ≤10 文件：直接聚合
            result = directAggregate(input);
        } else {
            // >10 文件：先分组，再逐组聚合，最后合并
            result = groupedAggregate(input);
        }

        // 校验通过后存缓存
        String jsonOutput = GSON.toJson(result);
        String contentHash = sha256(jsonOutput);
        CacheGranule granule = CacheGranule.builder()
                .taskType(TaskType.AGGREGATE_SUMMARY)
                .version("1.0")
                .contentType("json")
                .contentHash(cacheKey)
                .modelId("aggregate")
                .output(jsonOutput)
                .createdAt(System.currentTimeMillis())
                .invalidatedBy(new ArrayList<String>())
                .build();
        cache.put(granule);

        return result;
    }

    /**
     * ≤10 文件：直接调用 LLM 生成聚合摘要。
     */
    private AggregateSummaryOutput directAggregate(AggregateSummaryInput input) {
        AggregateSummaryPrompt prompt = new AggregateSummaryPrompt();

        for (int attempt = 0; attempt < 2; attempt++) {
            String systemPrompt = prompt.buildPackageSystemPrompt();
            String userPrompt = prompt.buildPackageUserPrompt(input);
            String llmOutput = llmClient.chat(systemPrompt, userPrompt);

            AggregateSummaryOutput output = parseAndValidateOutput(llmOutput, input);
            if (output != null) {
                return output;
            }
        }

        // 两次都失败，返回默认值
        return buildDefaultOutput(input);
    }

    /**
     * >10 文件：分组聚合。
     * <ol>
     *   <li>GroupingPrompt → LLM 获取分组表</li>
     *   <li>每组分批调用 AggregateSummaryPrompt → LLM 获取组摘要</li>
     *   <li>合并所有组摘要为最终输出</li>
     * </ol>
     */
    private AggregateSummaryOutput groupedAggregate(AggregateSummaryInput input) {
        List<FileSummaryEntry> allFiles = input.getFileSummaries();

        // 1. 分组
        List<List<FileSummaryEntry>> groups = performGrouping(allFiles);

        // 2. 各组聚合
        List<AggregateSummaryOutput> groupOutputs = new ArrayList<AggregateSummaryOutput>();
        for (List<FileSummaryEntry> group : groups) {
            AggregateSummaryInput groupInput = buildGroupInput(input, group);
            AggregateSummaryOutput groupOutput = directAggregate(groupInput);
            if (groupOutput != null) {
                groupOutputs.add(groupOutput);
            }
        }

        // 3. 合并
        return mergeGroupOutputs(input, groupOutputs);
    }

    /**
     * 调用 GroupingPrompt → LLM 进行分组。
     */
    List<List<FileSummaryEntry>> performGrouping(List<FileSummaryEntry> allFiles) {
        GroupingPrompt groupingPrompt = new GroupingPrompt();
        String systemPrompt = groupingPrompt.buildSystemPrompt();
        String userPrompt = groupingPrompt.buildUserPrompt(allFiles);

        String llmOutput = llmClient.chat(systemPrompt, userPrompt);

        return parseGroups(llmOutput, allFiles);
    }

    /**
     * 解析 LLM 返回的分组 JSON。
     */
    List<List<FileSummaryEntry>> parseGroups(String json, List<FileSummaryEntry> allFiles) {
        // 按文件名索引
        Map<String, FileSummaryEntry> fileIndex = new HashMap<String, FileSummaryEntry>();
        for (FileSummaryEntry entry : allFiles) {
            fileIndex.put(entry.getFileName(), entry);
        }

        List<List<FileSummaryEntry>> groups = new ArrayList<List<FileSummaryEntry>>();

        try {
            JsonArray arr = GSON.fromJson(json, JsonArray.class);
            if (arr == null || arr.size() == 0) {
                // 分组失败，全部作为一组
                groups.add(new ArrayList<FileSummaryEntry>(allFiles));
                return groups;
            }

            Set<String> assigned = new HashSet<String>();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject groupObj = arr.get(i).getAsJsonObject();
                JsonArray filesArr = groupObj.getAsJsonArray("files");
                if (filesArr == null) continue;

                List<FileSummaryEntry> group = new ArrayList<FileSummaryEntry>();
                for (int j = 0; j < filesArr.size(); j++) {
                    String fileName = filesArr.get(j).getAsString();
                    FileSummaryEntry entry = fileIndex.get(fileName);
                    if (entry != null && !assigned.contains(fileName)) {
                        group.add(entry);
                        assigned.add(fileName);
                    }
                }
                if (!group.isEmpty()) {
                    groups.add(group);
                }
            }

            // 未分配的文件自成一组
            List<FileSummaryEntry> leftover = new ArrayList<FileSummaryEntry>();
            for (FileSummaryEntry entry : allFiles) {
                if (!assigned.contains(entry.getFileName())) {
                    leftover.add(entry);
                }
            }
            if (!leftover.isEmpty()) {
                groups.add(leftover);
            }

        } catch (Exception e) {
            // 解析失败，全部作为一组
            groups.add(new ArrayList<FileSummaryEntry>(allFiles));
        }

        if (groups.isEmpty()) {
            groups.add(new ArrayList<FileSummaryEntry>(allFiles));
        }
        return groups;
    }

    /**
     * 为指定的组构建 AggregateSummaryInput。
     */
    private AggregateSummaryInput buildGroupInput(AggregateSummaryInput original,
                                                   List<FileSummaryEntry> groupFiles) {
        return new AggregateSummaryInput(
                original.getPackageName(),
                groupFiles,
                original.getCrossPackageDeps(),
                original.getLayerDistribution());
    }

    /**
     * 合并多个组输出为最终输出。
     */
    private AggregateSummaryOutput mergeGroupOutputs(AggregateSummaryInput input,
                                                      List<AggregateSummaryOutput> groupOutputs) {
        if (groupOutputs.isEmpty()) {
            return buildDefaultOutput(input);
        }

        // 取第一个非空输出为主
        AggregateSummaryOutput merged = null;
        for (AggregateSummaryOutput out : groupOutputs) {
            if (out != null) {
                merged = out;
                break;
            }
        }
        if (merged == null) {
            return buildDefaultOutput(input);
        }

        // 汇总字段
        merged.setPackageName(input.getPackageName());

        // 使用 input 的 layerDistribution 推算 architectureLayer
        if (input.getLayerDistribution() != null && !input.getLayerDistribution().isEmpty()) {
            ArchitectureLayer detected = ArchitectureLayerDetector.detectPackageLayer(
                    input.getLayerDistribution());
            merged.setArchitectureLayer(detected);
            String composition = ArchitectureLayerDetector.getLayerComposition(
                    input.getLayerDistribution());
            merged.setLayerComposition(composition);
        }

        // 统计总数
        int totalFiles = 0;
        int totalMethods = 0;
        int highRisk = 0;
        int mediumRisk = 0;
        List<String> allCoreEntries = new ArrayList<String>();
        List<String> allCoreResp = new ArrayList<String>();

        for (AggregateSummaryOutput out : groupOutputs) {
            if (out == null) continue;
            totalFiles += out.getTotalFiles();
            totalMethods += out.getTotalMethods();
            highRisk += out.getHighRiskCount();
            mediumRisk += out.getMediumRiskCount();
            if (out.getCoreEntries() != null) allCoreEntries.addAll(out.getCoreEntries());
            if (out.getCoreResponsibilities() != null) allCoreResp.addAll(out.getCoreResponsibilities());
        }

        merged.setTotalFiles(totalFiles);
        merged.setTotalMethods(totalMethods);
        merged.setHighRiskCount(highRisk);
        merged.setMediumRiskCount(mediumRisk);

        // 限制列表长度
        if (allCoreEntries.size() > 5) allCoreEntries = allCoreEntries.subList(0, 5);
        if (allCoreResp.size() > 5) allCoreResp = allCoreResp.subList(0, 5);
        merged.setCoreEntries(allCoreEntries);
        merged.setCoreResponsibilities(allCoreResp);

        return merged;
    }

    /**
     * 解析并校验 LLM 输出。
     */
    AggregateSummaryOutput parseAndValidateOutput(String llmOutput, AggregateSummaryInput input) {
        if (llmOutput == null || llmOutput.trim().isEmpty()) {
            return null;
        }

        AggregateSummaryValidator validator = new AggregateSummaryValidator(input);
        ValidationResult vr = validator.validate(llmOutput);
        if (!vr.isValid()) {
            return null;
        }

        try {
            return GSON.fromJson(llmOutput, AggregateSummaryOutput.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 生成缓存 key：aggregate.pkg.{sha256(文件名排序拼接)}。
     * 不含冒号以确保跨平台文件名兼容。
     */
    String generateCacheKey(AggregateSummaryInput input) {
        if (input == null || input.getFileSummaries() == null) {
            return "aggregate.pkg.empty";
        }
        List<String> fileNames = new ArrayList<String>();
        for (FileSummaryEntry entry : input.getFileSummaries()) {
            StringBuilder sb = new StringBuilder();
            sb.append(entry.getFileName() != null ? entry.getFileName() : "");
            sb.append(".");
            sb.append(entry.getLayer() != null ? entry.getLayer().name() : "");
            fileNames.add(sb.toString());
        }
        Collections.sort(fileNames);
        String joined = String.join("|", fileNames);
        return "aggregate.pkg." + sha256(joined);
    }

    /**
     * 构建默认输出（降级兜底）。
     */
    private AggregateSummaryOutput buildDefaultOutput(AggregateSummaryInput input) {
        AggregateSummaryOutput output = new AggregateSummaryOutput();
        output.setPackageName(input.getPackageName());
        output.setSummary("（聚合摘要生成失败）");
        output.setCoreEntries(new ArrayList<String>());
        output.setCoreResponsibilities(new ArrayList<String>());
        output.setCrossPackageDeps(new ArrayList<AggregateSummaryOutput.CrossPackageDep>());
        output.setRiskOverview("");
        output.setTotalFiles(input.getFileSummaries() != null ? input.getFileSummaries().size() : 0);
        output.setTotalMethods(0);
        output.setHighRiskCount(0);
        output.setMediumRiskCount(0);
        return output;
    }

    /**
     * SHA-256 工具。
     */
    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 is guaranteed by Java spec", e);
        }
    }
}
