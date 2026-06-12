package com.codelens.common.profile;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ArchitectureProfile → Prompt 上下文段转换工具。
 * <p>
 * 将 ArchitectureProfile 中的信息转为纯文本段，嵌入 SystemPrompt，
 * 帮助 LLM 理解项目架构上下文，减少误报。
 * 两端共用，零外部依赖。
 */
public class ArchitectureProfilePromptHelper {

    private ArchitectureProfilePromptHelper() {}

    /**
     * 将 ArchitectureProfile 转为 Prompt 上下文段。
     *
     * @param profile 架构画像，可为 null
     * @return 上下文文本，如果 profile 为 null 或无信息则返回空字符串
     */
    public static String generateContext(ArchitectureProfile profile) {
        if (profile == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // 1. 架构模式 + 置信度
        ArchitecturePattern pattern = profile.getArchitecturePattern();
        ArchitecturePattern.Confidence confidence = profile.getConfidence();
        if (pattern != null) {
            sb.append("架构模式: ").append(pattern.name());
            if (confidence != null) {
                sb.append(" (置信度: ").append(confidence.name()).append(")");
            }
            sb.append("\n");
        }

        // 2. 层分布（按类数降序取 top 5）
        Map<String, Integer> distribution = profile.getLayerDistribution();
        if (distribution != null && !distribution.isEmpty()) {
            sb.append("架构层分布:\n");
            distribution.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> sb.append("  - ").append(e.getKey()).append(": ").append(e.getValue()).append(" classes\n"));
        }

        // 3. 分层规则
        Map<String, List<String>> layerRules = profile.getLayerRules();
        if (layerRules != null && !layerRules.isEmpty()) {
            sb.append("分层约定:\n");
            for (Map.Entry<String, List<String>> entry : layerRules.entrySet()) {
                String caller = entry.getKey();
                List<String> allowed = entry.getValue();
                sb.append("  - ").append(caller).append(" → ");
                if (allowed.isEmpty()) {
                    sb.append("(无数据)");
                } else {
                    sb.append(String.join(", ", allowed));
                }
                sb.append("\n");
            }
        }

        // 4. 约束（只列 ERROR 级别，最多 3 条）
        List<Constraint> constraints = profile.getConstraints();
        if (constraints != null && !constraints.isEmpty()) {
            sb.append("架构约束:\n");
            int count = 0;
            for (Constraint c : constraints) {
                if ("ERROR".equals(c.getSeverity()) && count < 3) {
                    sb.append("  - ").append(c.getDescription()).append("\n");
                    count++;
                }
            }
        }

        // 5. 跨切关注点（只列类别+机制，帮 LLM 理解项目已有的横切机制）
        List<CrossCuttingConcern> concerns = profile.getCrossCuttingConcerns();
        if (concerns != null && !concerns.isEmpty()) {
            sb.append("已识别的跨切关注点: ");
            boolean first = true;
            for (CrossCuttingConcern c : concerns) {
                if (!first) sb.append(", ");
                sb.append(c.getCategory());
                first = false;
            }
            sb.append("\n");
        }

        // 6. 关键推理规则（给 LLM 的额外指导，仅当画像中有对应内容时输出）
        boolean hasExceptionHandling = false;
        boolean hasSecurity = false;
        if (concerns != null) {
            for (CrossCuttingConcern c : concerns) {
                if ("EXCEPTION_HANDLING".equals(c.getCategory())) hasExceptionHandling = true;
                if ("SECURITY".equals(c.getCategory())) hasSecurity = true;
            }
        }
        boolean hasLayerRules = layerRules != null && !layerRules.isEmpty();

        if (hasExceptionHandling || hasSecurity || hasLayerRules) {
            sb.append("\n基于以上架构上下文，分析时请注意：\n");
            if (hasExceptionHandling) {
                sb.append("- 项目有全局异常处理机制，不要因为方法内缺少 try-catch 而报告\"异常处理缺失\"\n");
            }
            if (hasSecurity) {
                sb.append("- 项目有安全框架（如 Spring Security），不要因为方法缺少权限检查而报告\"权限控制缺失\"\n");
            }
            if (hasLayerRules) {
                sb.append("- 跨层调用如果违反上述分层约定，应标注为架构违规风险\n");
            }
        }

        return sb.toString();
    }
}
