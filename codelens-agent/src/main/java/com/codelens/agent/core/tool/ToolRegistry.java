package com.codelens.agent.core.tool;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool 注册表。管理所有 ToolDefinition，生成 OpenAI tools JSON。
 * <p>
 * Claude Code 模式：Tool 注册后对 LLM 可见，LLM 根据 description + parameterSchema
 * 决定何时调用哪个 Tool。注册表本身是无状态的 Map，线程安全取决于调用方。
 * </p>
 */
public class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new LinkedHashMap<String, ToolDefinition>();
    private static final Gson gson = new Gson();

    /** 注册 Tool */
    public void register(ToolDefinition tool) {
        tools.put(tool.name(), tool);
    }

    /** 按名称获取 Tool */
    public ToolDefinition get(String name) {
        return tools.get(name);
    }

    /** 已注册 Tool 数量 */
    public int size() {
        return tools.size();
    }

    /** 是否为空 */
    public boolean isEmpty() {
        return tools.isEmpty();
    }

    /**
     * 生成 OpenAI tools JSON（function 数组）。
     * 格式: [{"type":"function","function":{"name":"...","description":"...","parameters":{...}}}]
     */
    public String toToolsJson() {
        List<Map<String, Object>> toolList = new ArrayList<Map<String, Object>>();
        for (ToolDefinition tool : tools.values()) {
            Map<String, Object> toolMap = new LinkedHashMap<String, Object>();
            toolMap.put("type", "function");

            Map<String, Object> funcMap = new LinkedHashMap<String, Object>();
            funcMap.put("name", tool.name());
            funcMap.put("description", tool.description());

            // parameterSchema 是 JSON String，解析为 JsonObject 嵌入
            String schema = tool.parameterSchema();
            if (schema != null && !schema.isEmpty()) {
                try {
                    JsonObject params = JsonParser.parseString(schema).getAsJsonObject();
                    funcMap.put("parameters", gson.fromJson(params, Map.class));
                } catch (Exception e) {
                    // schema 解析失败，使用空 parameters
                    Map<String, Object> emptyParams = new LinkedHashMap<String, Object>();
                    emptyParams.put("type", "object");
                    emptyParams.put("properties", new LinkedHashMap<String, Object>());
                    funcMap.put("parameters", emptyParams);
                }
            }

            toolMap.put("function", funcMap);
            toolList.add(toolMap);
        }
        return gson.toJson(toolList);
    }
}
