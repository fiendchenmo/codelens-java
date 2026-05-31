package com.codelens.common.agent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * 报告写入器。
 * <p>
 * 将聚合摘要输出写入 JSON 和 Markdown 格式文件。
 * </p>
 */
public class ReportWriter {

    private static final Logger LOG = Logger.getLogger(ReportWriter.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 写入包级摘要报告。
     *
     * @param output    包级聚合摘要
     * @param outputDir 输出目录
     */
    public void writePackageSummary(AggregateSummaryOutput output, String outputDir) {
        if (output == null || output.getPackageName() == null) return;
        try {
            Path dir = Paths.get(outputDir);
            Files.createDirectories(dir);

            // 包名转安全文件名
            String safeName = output.getPackageName().replace('.', '_');
            Path jsonFile = dir.resolve(safeName + "_package.json");
            Path mdFile = dir.resolve(safeName + "_package.md");

            Files.write(jsonFile, GSON.toJson(output).getBytes(StandardCharsets.UTF_8));
            Files.write(mdFile, buildPackageMarkdown(output).getBytes(StandardCharsets.UTF_8));

            LOG.info("Package summary written: " + jsonFile);
        } catch (IOException e) {
            LOG.warning("Failed to write package summary: " + e.getMessage());
        }
    }

    /**
     * 写入模块级摘要报告。
     *
     * @param output    模块级聚合摘要
     * @param outputDir 输出目录
     */
    public void writeModuleSummary(AggregateSummaryOutput output, String outputDir) {
        if (output == null) return;
        try {
            Path dir = Paths.get(outputDir);
            Files.createDirectories(dir);

            String moduleName = output.getPackageName() != null
                    ? output.getPackageName() : "module";
            String safeName = moduleName.replace('.', '_');
            Path jsonFile = dir.resolve(safeName + "_module.json");
            Path mdFile = dir.resolve(safeName + "_module.md");

            Files.write(jsonFile, GSON.toJson(output).getBytes(StandardCharsets.UTF_8));
            Files.write(mdFile, buildModuleMarkdown(output).getBytes(StandardCharsets.UTF_8));

            LOG.info("Module summary written: " + jsonFile);
        } catch (IOException e) {
            LOG.warning("Failed to write module summary: " + e.getMessage());
        }
    }

    /**
     * 构建包级 Markdown 内容。
     */
    private String buildPackageMarkdown(AggregateSummaryOutput output) {
        StringBuilder sb = new StringBuilder();
        String pkg = output.getPackageName() != null ? output.getPackageName() : "";
        sb.append("# ").append(pkg).append("\n\n");

        sb.append("## 概览\n\n");
        sb.append("- **架构层次**: ").append(nullSafe(output.getArchitectureLayer())).append("\n");
        String comp = output.getLayerComposition();
        if (comp != null) {
            sb.append("- **层次组成**: ").append(comp).append("\n");
        }
        sb.append("- **文件数**: ").append(output.getTotalFiles()).append("\n");
        sb.append("- **方法数**: ").append(output.getTotalMethods()).append("\n");
        sb.append("- **高风险**: ").append(output.getHighRiskCount()).append("\n");
        sb.append("- **中风险**: ").append(output.getMediumRiskCount()).append("\n\n");

        sb.append("## 摘要\n\n");
        sb.append(nullSafe(output.getSummary())).append("\n\n");

        if (output.getCoreEntries() != null && !output.getCoreEntries().isEmpty()) {
            sb.append("## 核心入口\n\n");
            for (String entry : output.getCoreEntries()) {
                sb.append("- ").append(entry).append("\n");
            }
            sb.append("\n");
        }

        if (output.getCoreResponsibilities() != null && !output.getCoreResponsibilities().isEmpty()) {
            sb.append("## 核心职责\n\n");
            for (String resp : output.getCoreResponsibilities()) {
                sb.append("- ").append(resp).append("\n");
            }
            sb.append("\n");
        }

        String risk = output.getRiskOverview();
        if (risk != null && !risk.trim().isEmpty()) {
            sb.append("## 风险概述\n\n");
            sb.append(risk).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 构建模块级 Markdown 内容。
     */
    private String buildModuleMarkdown(AggregateSummaryOutput output) {
        StringBuilder sb = new StringBuilder();
        String name = output.getPackageName() != null ? output.getPackageName() : "Module";
        sb.append("# 模块: ").append(name).append("\n\n");

        sb.append("## 概览\n\n");
        sb.append("- **架构层次**: ").append(nullSafe(output.getArchitectureLayer())).append("\n");
        sb.append("- **文件数**: ").append(output.getTotalFiles()).append("\n");
        sb.append("- **方法数**: ").append(output.getTotalMethods()).append("\n");
        sb.append("- **高风险**: ").append(output.getHighRiskCount()).append("\n");
        sb.append("- **中风险**: ").append(output.getMediumRiskCount()).append("\n\n");

        sb.append("## 摘要\n\n");
        sb.append(nullSafe(output.getSummary())).append("\n\n");

        if (output.getCoreEntries() != null && !output.getCoreEntries().isEmpty()) {
            sb.append("## 核心包/入口\n\n");
            for (String entry : output.getCoreEntries()) {
                sb.append("- ").append(entry).append("\n");
            }
            sb.append("\n");
        }

        String risk = output.getRiskOverview();
        if (risk != null && !risk.trim().isEmpty()) {
            sb.append("## 风险概述\n\n");
            sb.append(risk).append("\n\n");
        }

        return sb.toString();
    }

    private static String nullSafe(Object obj) {
        return obj != null ? obj.toString() : "";
    }
}
