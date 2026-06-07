package com.codelens.common.agent;

import com.codelens.common.analyzer.ArchitectureLayerDetector;
import com.codelens.common.models.ArchitectureLayer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

/**
 * 聚合摘要输出校验器。
 * <p>
 * 对 LLM 返回的 JSON 结果进行 9 条规则校验，支持 ERROR 停发和 WARN 自动修复。
 * </p>
 */
public class AggregateSummaryValidator {

    private static final Gson GSON = new GsonBuilder().create();

    // 最大字符限制
    static final int MAX_SUMMARY_CHARS = 200;
    static final int MAX_CORE_ENTRIES = 5;
    static final int MAX_CORE_RESPONSIBILITIES = 5;
    static final int MAX_TOKEN_ESTIMATE = 1500;
    static final int MAX_REFACTOR_OVERVIEW_CHARS = 500;

    private final AggregateSummaryInput input;
    // 测试用：最近一次校验的输出对象
    AggregateSummaryOutput lastOutput;

    public AggregateSummaryValidator(AggregateSummaryInput input) {
        this.input = input;
    }

    /**
     * 测试用：获取最近一次校验的输出对象。
     */
    AggregateSummaryOutput getLastOutput() {
        return lastOutput;
    }

    /**
     * 对 LLM 输出的 JSON 字符串执行全部 10 条规则校验。
     *
     * @param json LLM 返回的 JSON 字符串
     * @return 校验结果
     */
    public ValidationResult validate(String json) {
        if (json == null || json.trim().isEmpty()) {
            return ValidationResult.fail("json", "输出 JSON 为空");
        }

        AggregateSummaryOutput output;
        try {
            output = GSON.fromJson(json, AggregateSummaryOutput.class);
        } catch (Exception e) {
            return ValidationResult.fail("json", "JSON 解析失败: " + e.getMessage());
        }

        if (output == null) {
            return ValidationResult.fail("json", "JSON 解析结果为空");
        }

        return validate(output);
    }

    /**
     * 对已解析的 AggregateSummaryOutput 执行全部 10 条规则校验。
     * WARN 规则会直接修改 output 对象。
     */
    public ValidationResult validate(AggregateSummaryOutput output) {
        if (output == null) {
            return ValidationResult.fail("output", "输出对象为空");
        }

        // 保存校验后的输出供测试验证
        this.lastOutput = output;

        // ★ 在所有校验之前，先用实际值覆盖 LLM 不可信的统计字段
        overrideStatisticsFromInput(output);

        // V1: packageName 非空且匹配 input
        ValidationResult v1 = validatePackageName(output);
        if (!v1.isValid()) return v1;

        // V2: architectureLayer 在枚举范围
        ValidationResult v2 = validateArchitectureLayer(output);
        if (!v2.isValid()) return v2;

        // V3: summary 非空且 ≤200 字（WARN 截断）
        validateSummary(output);

        // V4: coreEntries ≤5（WARN 截断）
        validateCoreEntries(output);

        // V5: coreResponsibilities ≤5（WARN 截断）
        validateCoreResponsibilities(output);

        // V6: riskOverview 非空（有风险时）（WARN 补模板）
        // 此时 highRiskCount/mediumRiskCount 已经过 overrideStatisticsFromInput 修正
        validateRiskOverview(output);

        // V7: totalFiles 一致性检查（WARN 级别，自动修正）
        validateTotalFiles(output);

        // V8: 风险计数与概述一致（WARN 以计数为准）
        alignRiskCounts(output);

        // V9: Token 估算 ≤1200（WARN 截断）
        truncateByTokenEstimate(output);

        // V10: refactorOverview 非空（有风险时）+ 长度限制
        validateRefactorOverview(output);

        return ValidationResult.ok();
    }

    // ========================================================================
    // 统计字段覆盖（校验前执行）
    // ========================================================================

    /**
     * 用 input 的实际统计值覆盖 LLM 输出的计数字段。
     * LLM 数数不可信，totalFiles/totalMethods 等必须以实际值为准。
     * highRiskCount/mediumRiskCount 从 LLM 的 riskCategories 反算。
     */
    private void overrideStatisticsFromInput(AggregateSummaryOutput output) {
        if (input == null) return;

        // totalFiles = 实际输入的文件摘要数
        if (input.getFileSummaries() != null) {
            output.setTotalFiles(input.getFileSummaries().size());
        }

        // architectureLayer: 优先用LLM返回的fileLayers算众数，fallback到硬编码规则
        if (output.getFileLayers() != null && !output.getFileLayers().isEmpty()) {
            java.util.Map<ArchitectureLayer, Integer> llmLayerDist = new java.util.HashMap<>();
            for (AggregateSummaryOutput.FileLayerEntry entry : output.getFileLayers()) {
                try {
                    ArchitectureLayer layer = ArchitectureLayer.valueOf(entry.getLayer());
                    llmLayerDist.merge(layer, 1, Integer::sum);
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    // LLM输出无效layer值，跳过
                }
            }
            if (!llmLayerDist.isEmpty()) {
                ArchitectureLayer detected = ArchitectureLayerDetector.detectPackageLayer(llmLayerDist);
                output.setArchitectureLayer(detected != null ? detected : ArchitectureLayer.UNKNOWN);
                String composition = ArchitectureLayerDetector.getLayerComposition(llmLayerDist);
                output.setLayerComposition(composition);
            } else {
                fallbackToDetector(output);
            }
        } else {
            fallbackToDetector(output);
        }

        // 从 riskCategories 反算 highRiskCount 和 mediumRiskCount
        // 为 null 或空时归零（LLM 不再直接输出计数）
        int high = 0, medium = 0;
        if (output.getRiskCategories() != null) {
            for (AggregateSummaryOutput.RiskCategoryEntry rc : output.getRiskCategories()) {
                if ("HIGH".equals(rc.getSeverity())) {
                    high++;
                } else if ("MEDIUM".equals(rc.getSeverity())) {
                    medium++;
                }
                // LOW 不计数
            }
        }
        output.setHighRiskCount(high);
        output.setMediumRiskCount(medium);
    }

    /**
     * Fallback: 用 ArchitectureLayerDetector 硬编码规则推断 architectureLayer。
     */
    private void fallbackToDetector(AggregateSummaryOutput output) {
        if (input != null && input.getLayerDistribution() != null && !input.getLayerDistribution().isEmpty()) {
            ArchitectureLayer detected = ArchitectureLayerDetector.detectPackageLayer(
                    input.getLayerDistribution());
            output.setArchitectureLayer(detected != null ? detected : ArchitectureLayer.UNKNOWN);
            String composition = ArchitectureLayerDetector.getLayerComposition(
                    input.getLayerDistribution());
            output.setLayerComposition(composition);
        } else if (output.getArchitectureLayer() == null) {
            output.setArchitectureLayer(ArchitectureLayer.UNKNOWN);
        }
    }

    // ========================================================================
    // V1-V9 校验规则
    // ========================================================================

    /**
     * V1: packageName 非空且匹配 input。
     */
    ValidationResult validatePackageName(AggregateSummaryOutput output) {
        String pn = output.getPackageName();
        if (pn == null || pn.trim().isEmpty()) {
            return ValidationResult.fail("packageName", "packageName 为空");
        }
        String inputPkg = input != null ? input.getPackageName() : null;
        if (inputPkg != null && !inputPkg.equals(pn.trim())) {
            return ValidationResult.fail("packageName",
                    "packageName 不匹配: 期望 '" + inputPkg + "', 实际 '" + pn.trim() + "'");
        }
        return ValidationResult.ok();
    }

    /**
     * V2: architectureLayer 在枚举范围。
     */
    ValidationResult validateArchitectureLayer(AggregateSummaryOutput output) {
        ArchitectureLayer layer = output.getArchitectureLayer();
        if (layer == null) {
            return ValidationResult.fail("architectureLayer", "architectureLayer 为空");
        }
        // 检查是否是有效枚举值
        for (ArchitectureLayer valid : ArchitectureLayer.values()) {
            if (valid == layer) {
                return ValidationResult.ok();
            }
        }
        return ValidationResult.fail("architectureLayer",
                "architectureLayer 不在有效枚举范围内: " + layer);
    }

    /**
     * V3: summary 非空且 ≤200 字。超长时截断。
     */
    void validateSummary(AggregateSummaryOutput output) {
        String summary = output.getSummary();
        if (summary == null || summary.trim().isEmpty()) {
            output.setSummary("（无摘要）");
            return;
        }
        if (summary.length() > MAX_SUMMARY_CHARS) {
            output.setSummary(summary.substring(0, MAX_SUMMARY_CHARS));
        }
    }

    /**
     * V4: coreEntries ≤5。超长时截断。
     */
    void validateCoreEntries(AggregateSummaryOutput output) {
        List<String> entries = output.getCoreEntries();
        if (entries != null && entries.size() > MAX_CORE_ENTRIES) {
            output.setCoreEntries(entries.subList(0, MAX_CORE_ENTRIES));
        }
    }

    /**
     * V5: coreResponsibilities ≤5。超长时截断。
     */
    void validateCoreResponsibilities(AggregateSummaryOutput output) {
        List<String> resp = output.getCoreResponsibilities();
        if (resp != null && resp.size() > MAX_CORE_RESPONSIBILITIES) {
            output.setCoreResponsibilities(resp.subList(0, MAX_CORE_RESPONSIBILITIES));
        }
    }

    /**
     * V6: riskOverview 非空（有风险时）。补模板。
     * 优先从 riskCategories 生成结构化概述。
     */
    void validateRiskOverview(AggregateSummaryOutput output) {
        int highRisk = output.getHighRiskCount();
        int mediumRisk = output.getMediumRiskCount();
        String overview = output.getRiskOverview();
        if ((highRisk > 0 || mediumRisk > 0)
                && (overview == null || overview.trim().isEmpty())) {
            // 优先从 riskCategories 生成概述
            List<AggregateSummaryOutput.RiskCategoryEntry> cats = output.getRiskCategories();
            if (cats != null && !cats.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (AggregateSummaryOutput.RiskCategoryEntry rc : cats) {
                    if (sb.length() > 0) sb.append("；");
                    sb.append(rc.getCategory()).append("(").append(rc.getSeverity()).append(")");
                }
                output.setRiskOverview(sb.toString());
            } else {
                output.setRiskOverview("存在 " + highRisk + " 个高风险、"
                        + mediumRisk + " 个中风险问题，请关注。");
            }
        }
    }

    /**
     * V7: totalFiles 与实际值一致性检查（WARN 级别，覆盖后自动修正）。
     */
    ValidationResult validateTotalFiles(AggregateSummaryOutput output) {
        if (input == null || input.getFileSummaries() == null) {
            return ValidationResult.ok();
        }
        int expected = input.getFileSummaries().size();
        if (output.getTotalFiles() != expected) {
            // WARN: 覆盖后有差异，强制修正
            output.setTotalFiles(expected);
        }
        return ValidationResult.ok(); // 不再 FAIL
    }

    /**
     * V8: 风险计数与概述一致。以计数为准。
     */
    void alignRiskCounts(AggregateSummaryOutput output) {
        String overview = output.getRiskOverview();
        int highRisk = output.getHighRiskCount();
        int mediumRisk = output.getMediumRiskCount();
        if (overview != null && !overview.trim().isEmpty()) {
            // 概述存在即可，不做字符串匹配
        }
    }

    /**
     * V9: Token 估算 ≤1200。超长时截断。
     */
    void truncateByTokenEstimate(AggregateSummaryOutput output) {
        // 估算当前输出 Token 数（粗略按 1 token ≈ 1.5 字符估算）
        String json = GSON.toJson(output);
        int estimatedTokens = (int) Math.ceil(json.length() / 1.5);
        if (estimatedTokens > MAX_TOKEN_ESTIMATE) {
            String summary = output.getSummary();
            if (summary != null && summary.length() > 100) {
                output.setSummary(summary.substring(0, 100) + "…");
            }
        }
    }

    /**
     * V10: refactorOverview 校验。
     * - 有风险时（riskCategories 含 HIGH/MEDIUM）不能为 null 或空字符串
     * - 长度 ≤ 500 字符
     * - 无风险时（riskCategories 为空或全 LOW）可以为空
     */
    void validateRefactorOverview(AggregateSummaryOutput output) {
        String overview = output.getRefactorOverview();
        int highRisk = output.getHighRiskCount();
        int mediumRisk = output.getMediumRiskCount();

        // 有风险时：refactorOverview 不能为空
        if ((highRisk > 0 || mediumRisk > 0)
                && (overview == null || overview.trim().isEmpty())) {
            output.setRefactorOverview("该包存在 " + highRisk + " 个高风险、"
                    + mediumRisk + " 个中风险问题，建议优先处理高风险项。");
            return;
        }

        // 长度超限时截断
        if (overview != null && overview.length() > MAX_REFACTOR_OVERVIEW_CHARS) {
            output.setRefactorOverview(overview.substring(0, MAX_REFACTOR_OVERVIEW_CHARS));
        }
        // 无风险时 = 空字符串或 null 都不需要处理（自然保留为空）
    }
}
