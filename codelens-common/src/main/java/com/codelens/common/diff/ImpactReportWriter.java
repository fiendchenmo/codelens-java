package com.codelens.common.diff;

import com.codelens.common.models.ArchitectureLayer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 影响分析报告输出工具。
 * <p>
 * 提供 JSON/Markdown/Console 格式的序列化输出，
 * 以及热力图数据生成和文件写入功能。
 * </p>
 */
public class ImpactReportWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ImpactReportWriter() {
        // utility class
    }

    // ==================== JSON 输出 ====================

    /**
     * 将 ImpactReport 序列化为 JSON 字符串。
     */
    public static String toJson(ImpactReport report) {
        if (report == null) return "{}";
        if (isEmptyReport(report)) {
            return "{\"message\":\"No changes detected\"}";
        }
        return GSON.toJson(report);
    }

    // ==================== Markdown 输出 ====================

    /**
     * 生成人类可读的 Markdown 报告。
     */
    public static String toMarkdown(ImpactReport report) {
        if (report == null || isEmptyReport(report)) {
            return "# Impact Report\n\nNo changes detected.";
        }

        StringBuilder md = new StringBuilder();

        // 标题
        md.append("# Impact Report\n\n");

        // 变更概览
        md.append("## 变更概览\n");
        md.append("- 基准 Commit: ").append(nullSafe(report.commitHash)).append("\n");
        md.append("- 变更文件: ").append(report.summary != null ? report.summary.totalChangedFiles : 0).append("\n");
        md.append("- 变更方法: ").append(report.summary != null ? report.summary.totalChangedMethods : 0).append("\n");

        if (report.summary != null && report.summary.note != null && !report.summary.note.isEmpty()) {
            md.append("- 备注: ").append(report.summary.note).append("\n");
        }
        md.append("\n");

        // 变更列表
        md.append("## 变更列表\n");
        md.append("| 文件 | 方法 | 变更类型 |\n");
        md.append("|------|------|----------|\n");
        for (ChangedFile file : report.changes) {
            if (file.changedMethods == null || file.changedMethods.isEmpty()) {
                md.append("| ").append(nullSafe(file.filePath))
                        .append(" | — | ").append(file.changeType).append(" |\n");
            } else {
                for (ChangedMethod method : file.changedMethods) {
                    md.append("| ").append(nullSafe(file.filePath))
                            .append(" | ").append(nullSafe(method.methodName))
                            .append(" | ").append(method.changeType).append(" |\n");
                }
            }
        }
        md.append("\n");

        // 影响分析
        md.append("## 影响分析\n");
        if (report.impacts == null || report.impacts.isEmpty()) {
            md.append("无影响分析（无扩散节点）。\n\n");
            return md.toString();
        }

        int directCount = report.summary != null ? report.summary.directImpactCount : 0;
        int indirectCount = report.summary != null ? report.summary.indirectImpactCount : 0;
        md.append("- 直接影响: ").append(directCount).append("\n");
        md.append("- 间接影响: ").append(indirectCount).append("\n");

        // 层分布
        if (report.summary != null && report.summary.impactedLayerDist != null
                && !report.summary.impactedLayerDist.isEmpty()) {
            StringBuilder layerStr = new StringBuilder();
            for (Map.Entry<ArchitectureLayer, Integer> entry : report.summary.impactedLayerDist.entrySet()) {
                if (layerStr.length() > 0) layerStr.append(", ");
                layerStr.append(entry.getKey()).append("(").append(entry.getValue()).append(")");
            }
            md.append("- 影响层分布: ").append(layerStr).append("\n");
        }
        md.append("\n");

        // 高风险路径
        List<String> highRisk = report.summary != null ? report.summary.highRiskPaths : null;
        if (highRisk != null && !highRisk.isEmpty()) {
            List<String> red = new ArrayList<>();
            List<String> yellow = new ArrayList<>();
            for (String path : highRisk) {
                if (path.startsWith("🔴")) {
                    red.add(path);
                } else {
                    yellow.add(path);
                }
            }

            if (!red.isEmpty()) {
                md.append("### 🔴 高风险影响路径\n");
                for (int i = 0; i < red.size(); i++) {
                    md.append(i + 1).append(". ").append(red.get(i).replace("🔴 ", "")).append("\n");
                }
                md.append("\n");
            }

            if (!yellow.isEmpty()) {
                md.append("### 🟡 中风险影响路径\n");
                for (int i = 0; i < yellow.size(); i++) {
                    md.append(i + 1).append(". ").append(yellow.get(i).replace("🟡 ", "")).append("\n");
                }
                md.append("\n");
            }
        }

        // 影响热力图
        String heatmapJson = toHeatmapJson(report);
        if (heatmapJson != null && !"[]".equals(heatmapJson) && !"{}".equals(heatmapJson)) {
            md.append("## 影响热力图\n");
            md.append("| 包 | 直接影响 | 间接影响 | 总计 |\n");
            md.append("|----|----------|----------|------|\n");
            try {
                com.google.gson.JsonObject obj = GSON.fromJson(heatmapJson, com.google.gson.JsonObject.class);
                if (obj != null && obj.has("packages") && obj.get("packages").isJsonArray()) {
                    for (com.google.gson.JsonElement elem : obj.getAsJsonArray("packages")) {
                        com.google.gson.JsonObject pkg = elem.getAsJsonObject();
                        String name = getString(pkg, "name");
                        int direct = getInt(pkg, "directImpact");
                        int indirect = getInt(pkg, "indirectImpact");
                        int total = getInt(pkg, "total");
                        md.append("| ").append(name).append(" | ").append(direct)
                                .append(" | ").append(indirect).append(" | ").append(total).append(" |\n");
                    }
                }
            } catch (Exception ignored) {
                // 解析失败则跳过热力图
            }
            md.append("\n");
        }

        return md.toString();
    }

    /**
     * 生成 JSON 格式的热力图数据，按包聚合。
     * <p>
     * 输出格式：
     * <pre>
     * {
     *   "packages": [
     *     {"name": "com.example.service", "directImpact": 3, "indirectImpact": 5, "total": 8}
     *   ]
     * }
     * </pre>
     * 按 total 降序排列。
     * </p>
     */
    public static String toHeatmapJson(ImpactReport report) {
        if (report == null || report.impacts == null || report.impacts.isEmpty()) {
            return "{\"packages\":[]}";
        }

        // 按包聚合
        Map<String, int[]> packageCounts = new LinkedHashMap<>();
        for (ImpactNode node : report.impacts) {
            String pkg = extractPackageName(node.className);
            int[] counts = packageCounts.get(pkg);
            if (counts == null) {
                counts = new int[2]; // direct, indirect
                packageCounts.put(pkg, counts);
            }
            if (node.level == ImpactLevel.DIRECT) {
                counts[0]++;
            } else {
                counts[1]++;
            }
        }

        // 排序：按 total 降序
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(packageCounts.entrySet());
        Collections.sort(sorted, (a, b) -> {
            int ta = a.getValue()[0] + a.getValue()[1];
            int tb = b.getValue()[0] + b.getValue()[1];
            if (ta != tb) return tb - ta;
            return a.getKey().compareTo(b.getKey());
        });

        // 构建 JSON
        StringBuilder json = new StringBuilder();
        json.append("{\"packages\":[");
        boolean first = true;
        for (Map.Entry<String, int[]> entry : sorted) {
            if (!first) json.append(",");
            first = false;
            int direct = entry.getValue()[0];
            int indirect = entry.getValue()[1];
            json.append("{\"name\":\"").append(escapeJson(entry.getKey()))
                    .append("\",\"directImpact\":").append(direct)
                    .append(",\"indirectImpact\":").append(indirect)
                    .append(",\"total\":").append(direct + indirect)
                    .append("}");
        }
        json.append("]}");
        return json.toString();
    }

    // ==================== 文件写入 ====================

    /**
     * 将报告写入指定目录。
     *
     * @param report    影响分析报告
     * @param outputDir 输出目录（如 .codelens）
     * @param format    输出格式（json/md/console；console 时不写文件）
     */
    public static void writeFiles(ImpactReport report, Path outputDir, String format) throws IOException {
        if (report == null || outputDir == null) return;
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        if ("json".equals(format)) {
            String json = toJson(report);
            Files.write(outputDir.resolve("impact_report.json"),
                    json.getBytes(StandardCharsets.UTF_8));
        } else if ("md".equals(format)) {
            String md = toMarkdown(report);
            Files.write(outputDir.resolve("impact_report.md"),
                    md.getBytes(StandardCharsets.UTF_8));
        }

        // 热力图数据始终写入（json 格式时）
        if ("json".equals(format)) {
            String heatmap = toHeatmapJson(report);
            Files.write(outputDir.resolve("impact_heatmap.json"),
                    heatmap.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ==================== 内部工具 ====================

    private static boolean isEmptyReport(ImpactReport report) {
        return report.changes == null || report.changes.isEmpty();
    }

    /**
     * 从 className 中提取包名。
     * "com.example.OrderService" → "com.example"
     */
    private static String extractPackageName(String className) {
        if (className == null || className.isEmpty()) return "(default)";
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            return className.substring(0, lastDot);
        }
        return "(default)";
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String getString(com.google.gson.JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }

    private static int getInt(com.google.gson.JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsInt();
        }
        return 0;
    }
}
