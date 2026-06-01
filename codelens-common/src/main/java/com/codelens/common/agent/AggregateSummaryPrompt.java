package com.codelens.common.agent;

import com.codelens.common.agent.AggregateSummaryInput.CrossPackageDep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

/**
 * 聚合摘要 Agent 的 Prompt 模板。
 * <p>
 * 封装包级（Package）和模块级（Module）两级的 System + User Prompt 生成。
 * </p>
 */
public class AggregateSummaryPrompt {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ========================================================================
    // 包级 Prompt
    // ========================================================================

    private static final String PACKAGE_SYSTEM_PROMPT =
            "你是一位 Java 代码架构分析专家。请根据输入的包级分析数据，生成该包的结构化聚合摘要。\n" +
            "\n" +
            "输出格式必须为 JSON，包含以下字段：\n" +
            "1. packageName: 包名 (String)\n" +
            "2. architectureLayer: 架构层次 (String)，可选值：CONTROLLER / SERVICE / REPOSITORY / HANDLER / CONFIG / CLIENT / MODEL / UTIL / UNKNOWN\n" +
            "3. layerComposition: 层次组成说明 (String)，如 \"80% SERVICE + 20% CONTROLLER\"\n" +
            "4. summary: 包的整体职责摘要 (String, ≤200 字)\n" +
            "5. coreEntries: 核心入口类/接口列表 (Array of String, ≤5 项)\n" +
            "6. coreResponsibilities: 核心职责列表 (Array of String, ≤5 项)\n" +
            "7. crossPackageDeps: 跨包依赖列表 (Array of Object)，每项包含 targetPackage(String)、viaMethods(Array of String)、direction(String)\n" +
            "8. riskOverview: 风险概述 (String)，无风险时可为空字符串\n" +
            "9. totalFiles: 文件总数 (Number)\n" +
            "10. totalMethods: 方法总数 (Number)\n" +
            "11. riskCategories: 风险分类列表 (Array of Object)，归纳包内共性风险，每项包含：\n" +
            "    - category: 类别名 (String)，如\"资源未关闭\"、\"异常吞没\"、\"硬编码配置\"\n" +
            "    - severity: 严重程度 (String)，可选值: HIGH / MEDIUM / LOW\n" +
            "    - description: 该类风险的共性描述 (String, ≤100字)\n" +
            "    - affectedFiles: 受影响文件列表 (Array of String)\n" +
            "    无风险时输出空数组 []\n" +
            "\n" +
            "约束：\n" +
            "- 仅输出 JSON，不要包含 ```json 标记\n" +
            "- summary 不超过 200 字\n" +
            "- coreEntries 不超过 5 项\n" +
            "- coreResponsibilities 不超过 5 项\n" +
            "- 总输出 Token 不超过 1100";

    private static final String PACKAGE_USER_TEMPLATE =
            "请根据以下输入数据，生成包级别的聚合摘要。\n" +
            "\n" +
            "=== 包名 ===\n" +
            "{{packageName}}\n" +
            "\n" +
            "=== 架构层次分布 ===\n" +
            "{{layerDistribution}}\n" +
            "\n" +
            "=== 文件摘要列表 ===\n" +
            "{{fileSummaries}}\n" +
            "\n" +
            "=== 跨包依赖 ===\n" +
            "{{crossPackageDeps}}";

    // ========================================================================
    // 模块级 Prompt
    // ========================================================================

    private static final String MODULE_SYSTEM_PROMPT =
            "你是一位 Java 代码架构分析专家。请根据输入的多个包的聚合摘要，生成模块级别的整体摘要。\n" +
            "\n" +
            "输出格式必须为 JSON，包含以下字段：\n" +
            "1. moduleName: 模块名 (String)\n" +
            "2. summary: 模块整体职责摘要 (String, ≤300 字)\n" +
            "3. corePackages: 核心包列表 (Array of String, ≤5 项)\n" +
            "4. architectureOverview: 架构概述 (String)\n" +
            "5. crossModuleDeps: 跨模块依赖描述 (String)\n" +
            "6. riskOverview: 风险概述 (String)，无风险时可为空字符串\n" +
            "7. highRiskPackageCount: 高风险包数量 (Number)\n" +
            "8. mediumRiskPackageCount: 中风险包数量 (Number)\n" +
            "\n" +
            "约束：\n" +
            "- 仅输出 JSON，不要包含 ```json 标记\n" +
            "- summary 不超过 300 字\n" +
            "- corePackages 不超过 5 项\n" +
            "- 总输出 Token 不超过 1000";

    private static final String MODULE_USER_TEMPLATE =
            "请根据以下多个包的聚合摘要，生成模块级别的整体摘要。\n" +
            "\n" +
            "=== 模块名 ===\n" +
            "{{moduleName}}\n" +
            "\n" +
            "=== 包含的包汇总 ===\n" +
            "{{packageSummaries}}";

    // ========================================================================
    // 公开方法
    // ========================================================================

    /**
     * 生成包级 System Prompt。
     */
    public String buildPackageSystemPrompt() {
        return PACKAGE_SYSTEM_PROMPT;
    }

    /**
     * 根据输入数据生成包级 User Prompt。
     *
     * @param input 聚合摘要输入
     * @return 完整的 User Prompt 字符串
     */
    public String buildPackageUserPrompt(AggregateSummaryInput input) {
        if (input == null) {
            return PACKAGE_USER_TEMPLATE
                    .replace("{{packageName}}", "")
                    .replace("{{layerDistribution}}", "")
                    .replace("{{fileSummaries}}", "")
                    .replace("{{crossPackageDeps}}", "");
        }
        String packageName = nullSafe(input.getPackageName());
        String layerDist = input.getLayerDistribution() != null
                ? GSON.toJson(input.getLayerDistribution()) : "";
        String fileSummaries = input.getFileSummaries() != null
                ? GSON.toJson(input.getFileSummaries()) : "";
        String crossDeps = input.getCrossPackageDeps() != null
                ? GSON.toJson(input.getCrossPackageDeps()) : "";

        return PACKAGE_USER_TEMPLATE
                .replace("{{packageName}}", packageName)
                .replace("{{layerDistribution}}", layerDist)
                .replace("{{fileSummaries}}", fileSummaries)
                .replace("{{crossPackageDeps}}", crossDeps);
    }

    /**
     * 生成完整的包级 Prompt（System + User）。
     */
    public String buildPackagePrompt(AggregateSummaryInput input) {
        return buildPackageSystemPrompt() + "\n\n" + buildPackageUserPrompt(input);
    }

    /**
     * 生成模块级 System Prompt。
     */
    public String buildModuleSystemPrompt() {
        return MODULE_SYSTEM_PROMPT;
    }

    /**
     * 根据多个包的聚合摘要生成模块级 User Prompt。
     *
     * @param moduleName       模块名
     * @param packageSummaries 各包聚合摘要列表
     * @return 完整的 User Prompt 字符串
     */
    public String buildModuleUserPrompt(String moduleName,
                                         List<AggregateSummaryOutput> packageSummaries) {
        String mn = nullSafe(moduleName);
        String summaries = packageSummaries != null
                ? GSON.toJson(packageSummaries) : "";

        return MODULE_USER_TEMPLATE
                .replace("{{moduleName}}", mn)
                .replace("{{packageSummaries}}", summaries);
    }

    /**
     * 生成完整的模块级 Prompt（System + User）。
     */
    public String buildModulePrompt(String moduleName,
                                     List<AggregateSummaryOutput> packageSummaries) {
        return buildModuleSystemPrompt() + "\n\n" + buildModuleUserPrompt(moduleName, packageSummaries);
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
