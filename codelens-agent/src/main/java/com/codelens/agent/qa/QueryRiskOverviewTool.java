package com.codelens.agent.qa;

import com.codelens.agent.data.AnalysisDataProvider;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool: 查询文件级风险概览。
 * <p>
 * 从 V3 分析 JSON 中提取风险分布（按严重度分组 + Top N 风险项），
 * 返回精简 JSON。
 * </p>
 *
 * <h3>返回示例</h3>
 * <pre>{@code
 * {
 *   "class": "UserService",
 *   "riskCount": 5,
 *   "bySeverity": {"HIGH": 2, "MEDIUM": 2, "LOW": 1},
 *   "topRisks": [
 *     {"type": "C3", "severity": "HIGH", "description": "SQL注入", "line": 45},
 *     {"type": "C2", "severity": "HIGH", "description": "硬编码密码", "line": 78}
 *   ]
 * }
 * }</pre>
 */
public class QueryRiskOverviewTool extends CodeLensTool {

    private static final Gson gson = new Gson();

    public QueryRiskOverviewTool(AnalysisDataProvider dataProvider) {
        super(dataProvider);
    }

    @Override
    public String name() {
        return "query_risk_overview";
    }

    @Override
    public String description() {
        return "查询指定 Java 类的风险概览，按严重度分组（HIGH/MEDIUM/LOW），列出 Top 风险项。"
                + "适用于了解某个类的主要安全/质量问题。"
                + "参数 className 为完全限定类名。";
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

        return extractRiskOverview(json, className);
    }

    String extractRiskOverview(String v3Json, String className) {
        try {
            JsonObject root = JsonParser.parseString(v3Json).getAsJsonObject();

            // 收集所有 risk items
            List<JsonObject> allRisks = new ArrayList<JsonObject>();

            // 顶层 risks
            if (root.has("risks") && root.get("risks").isJsonArray()) {
                collectRisks(root.getAsJsonArray("risks"), allRisks);
            }

            // methods[].risks
            if (root.has("methods") && root.get("methods").isJsonArray()) {
                JsonArray methods = root.getAsJsonArray("methods");
                for (int i = 0; i < methods.size(); i++) {
                    JsonObject method = methods.get(i).getAsJsonObject();
                    if (method.has("risks") && method.get("risks").isJsonArray()) {
                        collectRisks(method.getAsJsonArray("risks"), allRisks);
                    }
                }
            }

            // 按严重度分组
            int high = 0, medium = 0, low = 0;
            List<Map<String, Object>> topRisks = new ArrayList<Map<String, Object>>();
            for (JsonObject risk : allRisks) {
                String severity = getStringSafe(risk, "severity");
                if ("HIGH".equalsIgnoreCase(severity)) {
                    high++;
                } else if ("MEDIUM".equalsIgnoreCase(severity)) {
                    medium++;
                } else {
                    low++;
                }
                // Top 5
                if (topRisks.size() < 5) {
                    Map<String, Object> item = new HashMap<String, Object>();
                    item.put("type", getStringSafe(risk, "type"));
                    item.put("severity", severity);
                    String desc = getStringSafe(risk, "description");
                    item.put("description", desc != null ? desc : "");
                    item.put("line", getIntSafe(risk, "line"));
                    topRisks.add(item);
                }
            }

            Map<String, Object> result = new HashMap<String, Object>();
            result.put("class", className);
            result.put("riskCount", allRisks.size());

            Map<String, Integer> bySeverity = new HashMap<String, Integer>();
            bySeverity.put("HIGH", high);
            bySeverity.put("MEDIUM", medium);
            bySeverity.put("LOW", low);
            result.put("bySeverity", bySeverity);
            result.put("topRisks", topRisks);

            return gson.toJson(result);

        } catch (Exception e) {
            return "{\"error\": \"V3 JSON 解析失败: " + e.getMessage() + "\"}";
        }
    }

    private void collectRisks(JsonArray risks, List<JsonObject> out) {
        for (int i = 0; i < risks.size(); i++) {
            if (risks.get(i).isJsonObject()) {
                out.add(risks.get(i).getAsJsonObject());
            }
        }
    }
}
