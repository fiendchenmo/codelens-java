package com.codelens.agent.qa;

import com.codelens.agent.data.AnalysisDataProvider;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;

/**
 * Tool: 查询类的矛盾检测结果。
 * <p>
 * 从 AnalysisDataProvider.getContradictionReportJson() 获取矛盾报告，
 * 提取矛盾数量、评分、详情列表等关键信息返回精简 JSON。
 * </p>
 */
public class QueryContradictionsTool extends CodeLensTool {

    private static final Gson gson = new Gson();

    public QueryContradictionsTool(AnalysisDataProvider dataProvider) {
        super(dataProvider);
    }

    @Override
    public String name() {
        return "query_contradictions";
    }

    @Override
    public String description() {
        return "查询指定类的矛盾检测结果，包括代码与注释矛盾、风险证据矛盾等。"
                + "适用于发现代码中潜在的不一致问题。"
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
        String json = dataProvider.getContradictionReportJson(filePath);
        if (json == null || json.isEmpty()) {
            return "{\"error\": \"类 " + className + " 尚无矛盾检测数据\"}";
        }

        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("class", className);

            // 矛盾数量和评分
            int contradictionCount = 0;
            if (root.has("contradictionCount")) {
                contradictionCount = root.get("contradictionCount").getAsInt();
            }
            result.put("contradictionCount", contradictionCount);

            double score = 0.0;
            if (root.has("score")) {
                score = root.get("score").getAsDouble();
            }
            result.put("score", score);

            // 矛盾详情列表（防御性解析）
            if (root.has("contradictions") && root.get("contradictions").isJsonArray()) {
                JsonArray contraArr = root.getAsJsonArray("contradictions");
                List<Map<String, Object>> contradictions = new ArrayList<Map<String, Object>>();
                for (int i = 0; i < contraArr.size(); i++) {
                    if (contraArr.get(i).isJsonObject()) {
                        JsonObject c = contraArr.get(i).getAsJsonObject();
                        Map<String, Object> item = new LinkedHashMap<String, Object>();
                        item.put("type", getStringSafe(c, "type"));
                        item.put("description", getStringSafe(c, "description"));
                        String method = getStringSafe(c, "method");
                        if (method != null) {
                            item.put("method", method);
                        }
                        item.put("line", getIntSafe(c, "line"));
                        if (c.has("confidence")) {
                            item.put("confidence", c.get("confidence").getAsDouble());
                        }
                        contradictions.add(item);
                    }
                }
                result.put("contradictions", contradictions);
            }

            return gson.toJson(result);

        } catch (Exception e) {
            return "{\"error\": \"矛盾报告 JSON 解析失败: " + e.getMessage() + "\"}";
        }
    }
}
