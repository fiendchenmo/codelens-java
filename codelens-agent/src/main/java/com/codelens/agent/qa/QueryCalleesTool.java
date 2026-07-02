package com.codelens.agent.qa;

import com.codelens.agent.data.AnalysisDataProvider;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;

/**
 * Tool: 查询类的被调用方（方法调用了哪些其他方法）。
 * <p>
 * 从 V3 分析 JSON 的 methods[].calls[] 中提取调用目标，
 * 支持按 className 查全部方法或按 methodName 过滤。
 * </p>
 */
public class QueryCalleesTool extends CodeLensTool {

    private static final Gson gson = new Gson();

    public QueryCalleesTool(AnalysisDataProvider dataProvider) {
        super(dataProvider);
    }

    @Override
    public String name() {
        return "query_callees";
    }

    @Override
    public String description() {
        return "查询指定类中各个方法调用了哪些其他方法（调用链下游）。"
                + "适用于分析依赖关系、追踪方法执行路径。"
                + "参数 className 为完全限定类名。可选参数 methodName 用于只查某个特定方法的被调用方。";
    }

    @Override
    public String parameterSchema() {
        return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "\"className\":{\"type\":\"string\",\"description\":\"完全限定类名，如 com.example.UserService\"},"
                + "\"methodName\":{\"type\":\"string\",\"description\":\"可选，只查指定方法的被调用方\"}"
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

        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String methodName = (String) arguments.get("methodName");

            if (!root.has("methods") || !root.get("methods").isJsonArray()) {
                return "{\"error\": \"类 " + className + " 的 V3 分析结果中没有 methods 数据\"}";
            }

            JsonArray methodsArr = root.getAsJsonArray("methods");

            if (methodName != null && !methodName.isEmpty()) {
                // 单方法模式
                return extractSingleMethodCallees(methodsArr, className, methodName);
            } else {
                // 全方法模式
                return extractAllMethodCallees(methodsArr, className);
            }

        } catch (Exception e) {
            return "{\"error\": \"V3 JSON 解析失败: " + e.getMessage() + "\"}";
        }
    }

    private String extractAllMethodCallees(JsonArray methodsArr, String className) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("class", className);
        result.put("methodCount", methodsArr.size());

        Map<String, List<Map<String, Object>>> callees = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (int i = 0; i < methodsArr.size(); i++) {
            JsonObject method = methodsArr.get(i).getAsJsonObject();
            String name = getStringSafe(method, "name");
            callees.put(name, extractCalls(method));
        }
        result.put("callees", callees);

        return gson.toJson(result);
    }

    private String extractSingleMethodCallees(JsonArray methodsArr, String className, String methodName) {
        for (int i = 0; i < methodsArr.size(); i++) {
            JsonObject method = methodsArr.get(i).getAsJsonObject();
            String name = getStringSafe(method, "name");
            if (methodName.equals(name)) {
                List<Map<String, Object>> calls = extractCalls(method);
                Map<String, Object> result = new LinkedHashMap<String, Object>();
                result.put("class", className);
                result.put("method", methodName);
                result.put("calleeCount", calls.size());
                result.put("callees", calls);
                return gson.toJson(result);
            }
        }
        return "{\"error\": \"未找到方法 " + methodName + "\"}";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractCalls(JsonObject method) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (!method.has("calls") || !method.get("calls").isJsonArray()) {
            return result;
        }
        JsonArray callsArr = method.getAsJsonArray("calls");
        for (int i = 0; i < callsArr.size(); i++) {
            if (callsArr.get(i).isJsonObject()) {
                JsonObject call = callsArr.get(i).getAsJsonObject();
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("target", getStringSafe(call, "target"));
                item.put("line", getIntSafe(call, "line"));
                item.put("type", getStringSafe(call, "type"));
                result.add(item);
            }
        }
        return result;
    }
}
