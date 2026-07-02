package com.codelens.agent.qa;

import com.codelens.agent.data.AnalysisDataProvider;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;

/**
 * Tool: 按关键词搜索项目中的 Java 方法。
 * <p>
 * 从 AnalysisDataProvider.searchMethods() 获取搜索结果，
 * 返回匹配的方法列表（类名、方法名、签名）。
 * </p>
 */
public class SearchMethodsTool extends CodeLensTool {

    private static final Gson gson = new Gson();

    public SearchMethodsTool(AnalysisDataProvider dataProvider) {
        super(dataProvider);
    }

    @Override
    public String name() {
        return "search_methods";
    }

    @Override
    public String description() {
        return "按关键词搜索项目中的 Java 方法。"
                + "适用于用户只知道方法名部分关键词时定位方法。"
                + "返回匹配的方法列表，包含类名、方法名、签名。"
                + "参数 keyword 为搜索关键词。可选参数 limit 限制返回数量，默认 10。";
    }

    @Override
    public String parameterSchema() {
        return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "\"keyword\":{\"type\":\"string\",\"description\":\"搜索关键词，如方法名或类名片段\"},"
                + "\"limit\":{\"type\":\"integer\",\"description\":\"最大返回数量，默认 10\"}"
                + "},"
                + "\"required\":[\"keyword\"]"
                + "}";
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String keyword = (String) arguments.get("keyword");
        if (keyword == null || keyword.isEmpty()) {
            return "{\"error\": \"参数 keyword 不能为空\"}";
        }

        // limit 取值处理
        int limit = 10;
        Object limitObj = arguments.get("limit");
        if (limitObj instanceof Number) {
            limit = ((Number) limitObj).intValue();
        } else if (limitObj instanceof String) {
            try {
                limit = Integer.parseInt((String) limitObj);
            } catch (NumberFormatException e) {
                // 用默认值
            }
        }
        if (limit <= 0 || limit > 50) {
            limit = 10;
        }

        String json = dataProvider.searchMethods(keyword, limit);
        if (json == null || json.isEmpty()) {
            return "{\"error\": \"未找到与 " + keyword + " 相关的方法\"}";
        }

        try {
            // 搜索结果预期是 JSON 数组，防御性解析
            List<Object> results = new ArrayList<Object>();
            if (json.trim().startsWith("[")) {
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    if (arr.get(i).isJsonObject()) {
                        JsonObject item = arr.get(i).getAsJsonObject();
                        Map<String, Object> simplified = new LinkedHashMap<String, Object>();
                        String cn = getStringSafe(item, "className");
                        if (cn != null) simplified.put("className", cn);
                        String mn = getStringSafe(item, "methodName");
                        if (mn != null) simplified.put("methodName", mn);
                        String sig = getStringSafe(item, "signature");
                        if (sig != null) simplified.put("signature", sig);
                        results.add(simplified);
                    }
                }
            }

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("keyword", keyword);
            result.put("resultCount", results.size());
            result.put("results", results);

            return gson.toJson(result);

        } catch (Exception e) {
            return "{\"error\": \"搜索结果 JSON 解析失败: " + e.getMessage() + "\"}";
        }
    }
}
