package com.codelens.agent.qa;

import com.codelens.agent.data.AnalysisDataProvider;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool: 查询类的代码分析结果。
 * <p>
 * 从 V3 分析 JSON 中提取方法级风险、调用关系等关键字段，
 * 返回精简 JSON（非全量 V3 JSON），控制 token 消耗。
 * </p>
 *
 * <h3>返回示例</h3>
 * <pre>{@code
 * {
 *   "class": "UserService",
 *   "methodCount": 12,
 *   "riskCount": 3,
 *   "topRisks": ["C3:SQL注入(第45行)", "C2:硬编码密码(第78行)", "C1:NPE风险(第102行)"],
 *   "contradictionCount": 0
 * }
 * }</pre>
 */
public class QueryClassAnalysisTool extends CodeLensTool {

    private static final Gson gson = new Gson();

    public QueryClassAnalysisTool(AnalysisDataProvider dataProvider) {
        super(dataProvider);
    }

    @Override
    public String name() {
        return "query_class_analysis";
    }

    @Override
    public String description() {
        return "查询指定 Java 类的代码分析结果，包含方法数量、风险项、矛盾检测等摘要信息。"
                + "适用于了解某个类的整体代码质量时使用。"
                + "参数 className 为完全限定类名，如 com.example.UserService。";
    }

    @Override
    public String parameterSchema() {
        return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "\"className\":{\"type\":\"string\",\"description\":\"完全限定类名，如 com.example.UserService\"}"
                + "},"
                + "\"required\":[\"className\"]"
                + "}";
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String className = (String) arguments.get("className");
        if (className == null || className.isEmpty()) {
            return "{\"error\": \"参数 className 不能为空\"}";
        }

        String filePath = classNameToFilePath(className);
        String json = dataProvider.getV3AnalysisJson(filePath);
        if (json == null || json.isEmpty()) {
            return "{\"error\": \"类 " + className + " 尚未分析，请先运行白盒分析\"}";
        }

        return extractSummary(json, className);
    }

    /**
     * 从全量 V3 JSON 提取摘要。
     * 只保留 LLM 需要的关键字段，控制 token。
     */
    String extractSummary(String v3Json, String className) {
        try {
            JsonObject root = JsonParser.parseString(v3Json).getAsJsonObject();

            Map<String, Object> summary = new HashMap<String, Object>();
            summary.put("class", className);

            // 方法数
            int methodCount = 0;
            if (root.has("methods") && root.get("methods").isJsonArray()) {
                methodCount = root.getAsJsonArray("methods").size();
            }
            summary.put("methodCount", methodCount);

            // 风险统计（methods[].risks + 顶层 risks）
            int riskCount = 0;
            java.util.List<String> topRisks = new java.util.ArrayList<String>();

            // 顶层 risks
            if (root.has("risks") && root.get("risks").isJsonArray()) {
                riskCount += root.getAsJsonArray("risks").size();
                extractTopRiskItems(root.getAsJsonArray("risks"), topRisks, 3);
            }

            // methods[].risks
            if (root.has("methods") && root.get("methods").isJsonArray()) {
                for (int i = 0; i < root.getAsJsonArray("methods").size(); i++) {
                    JsonObject method = root.getAsJsonArray("methods").get(i).getAsJsonObject();
                    if (method.has("risks") && method.get("risks").isJsonArray()) {
                        riskCount += method.getAsJsonArray("risks").size();
                        extractTopRiskItems(method.getAsJsonArray("risks"), topRisks, 5);
                    }
                }
            }
            summary.put("riskCount", riskCount);
            summary.put("topRisks", topRisks);

            // 矛盾数（如果存在）
            int contradictionCount = 0;
            if (root.has("contradiction_count")) {
                contradictionCount = root.get("contradiction_count").getAsInt();
            }
            summary.put("contradictionCount", contradictionCount);

            return gson.toJson(summary);

        } catch (Exception e) {
            return "{\"error\": \"V3 JSON 解析失败: " + e.getMessage() + "\"}";
        }
    }

    private void extractTopRiskItems(com.google.gson.JsonArray risks,
                                      java.util.List<String> out, int maxCount) {
        for (int i = 0; i < risks.size() && out.size() < maxCount; i++) {
            JsonObject risk = risks.get(i).getAsJsonObject();
            String type = getStringSafe(risk, "type");
            String desc = getStringSafe(risk, "description");
            int line = getIntSafe(risk, "line");
            StringBuilder sb = new StringBuilder();
            if (type != null && !type.isEmpty()) {
                sb.append(type).append(": ");
            }
            if (desc != null && !desc.isEmpty()) {
                sb.append(desc);
            }
            if (line > 0) {
                sb.append("(第").append(line).append("行)");
            }
            if (sb.length() > 0) {
                out.add(sb.toString());
            }
        }
    }
}
