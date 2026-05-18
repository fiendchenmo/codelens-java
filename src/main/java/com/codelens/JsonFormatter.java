package com.codelens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JSON 格式化工具 - 提供 JSON 处理和格式化功能
 */
public class JsonFormatter {

    private static final Logger LOGGER = Logger.getLogger(JsonFormatter.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonFormatter() {
        // 工具类，禁止实例化
    }

    /**
     * JSON 格式化输出（使用 Gson）
     * 
     * @param json 原始 JSON 字符串
     * @return 格式化后的 JSON 字符串
     */
    public static String prettyPrintJson(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return GSON.toJson(element);
        } catch (Exception e) {
            // 如果解析失败，尝试返回原始 JSON
            return json;
        }
    }

    /**
     * 将 callers 信息合并到 JSON 结果中
     * 
     * @param jsonResult 原始分析结果 JSON
     * @param callers 调用者信息列表
     * @param callerClass 调用者信息类
     * @param <T> 调用者信息类型
     * @return 合并后的 JSON 字符串
     */
    public static <T> String mergeCallersToJson(String jsonResult, List<T> callers, CallerWrapper<T> wrapper) {
        if (callers == null || callers.isEmpty()) {
            return jsonResult;
        }
        
        try {
            // 用 Gson 解析 JSON，避免 lastIndexOf('}') 被字符串内的 } 干扰
            JsonObject root = JsonParser.parseString(jsonResult).getAsJsonObject();
            
            JsonArray callersArray = new JsonArray();
            for (T caller : callers) {
                callersArray.add(wrapper.toJson(caller));
            }
            root.add("callers", callersArray);
            
            return GSON.toJson(root);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "合并 callers 到 JSON 失败", e);
        }
        return jsonResult;
    }

    /**
     * 合并 CallerFinder.CallerInfo 到 JSON
     */
    public static String mergeCallersToJson(String jsonResult, List<CallerFinder.CallerInfo> callers) {
        if (callers.isEmpty()) {
            return jsonResult;
        }
        
        try {
            JsonObject root = JsonParser.parseString(jsonResult).getAsJsonObject();
            
            JsonArray callersArray = new JsonArray();
            for (CallerFinder.CallerInfo caller : callers) {
                JsonObject callerObj = new JsonObject();
                callerObj.addProperty("file", caller.filePath);
                callerObj.addProperty("type", caller.type);
                callerObj.addProperty("line", caller.lineNumber);
                callerObj.addProperty("description", caller.description);
                callersArray.add(callerObj);
            }
            root.add("callers", callersArray);
            
            return GSON.toJson(root);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "合并 callers 到 JSON 失败", e);
        }
        return jsonResult;
    }

    /**
     * 转义 JSON 字符串
     */
    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }

    /**
     * CallerInfo 到 JsonObject 的转换接口
     */
    public interface CallerWrapper<T> {
        JsonObject toJson(T caller);
    }
}
