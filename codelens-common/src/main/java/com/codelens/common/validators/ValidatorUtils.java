package com.codelens.common.validators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 校验器工具类 - EvidenceValidator 和 ConfidenceAnnotator 的共享方法。
 * <p>
 * 抽取自两个验证器中重复的 JSON 解析方法。
 */
public class ValidatorUtils {

    private ValidatorUtils() {}

    /**
     * 从 JSON 字符串中提取指定数组的内容（返回逗号分隔的对象字符串列表）。
     * 使用状态机手动解析，不依赖 Gson。
     */
    public static String extractJsonArray(String json, String arrayName) {
        String search = "\"" + arrayName + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        int start = json.indexOf('[', colon);
        if (start < 0) return null;
        int depth = 0;
        int i = start;
        boolean inString = false;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        return json.substring(start + 1, i);
                    }
                }
            }
            i++;
        }
        return null;
    }

    /**
     * 解析 JSON 对象数组内容为 Map 列表。
     * 优先使用 Gson 解析，失败时回退到正则匹配。
     */
    public static List<Map<String, String>> parseJsonObjects(String arrayContent) {
        List<Map<String, String>> result = new ArrayList<>();
        if (arrayContent == null || arrayContent.trim().isEmpty()) return result;

        try {
            JsonArray arr = JsonParser.parseString("[" + arrayContent + "]").getAsJsonArray();
            for (JsonElement element : arr) {
                if (element.isJsonObject()) {
                    Map<String, String> map = parseJsonObject(element.getAsJsonObject());
                    if (!map.isEmpty()) {
                        result.add(map);
                    }
                }
            }
        } catch (Exception e) {
            int i = 0;
            while (i < arrayContent.length()) {
                while (i < arrayContent.length() && Character.isWhitespace(arrayContent.charAt(i))) i++;
                if (i >= arrayContent.length()) break;

                if (arrayContent.charAt(i) == '{') {
                    int end = findMatchingBrace(arrayContent, i);
                    if (end > i) {
                        String obj = arrayContent.substring(i, end + 1);
                        Map<String, String> map = parseJsonObject(obj);
                        if (!map.isEmpty()) {
                            result.add(map);
                        }
                        i = end + 1;
                    } else {
                        i++;
                    }
                } else {
                    i++;
                }
            }
        }
        return result;
    }

    /**
     * 查找匹配的右大括号位置。
     */
    public static int findMatchingBrace(String s, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    /**
     * 将 Gson JsonObject 转为 Map。
     */
    public static Map<String, String> parseJsonObject(JsonObject obj) {
        Map<String, String> result = new LinkedHashMap<>();
        if (obj == null) return result;
        for (String key : obj.keySet()) {
            JsonElement element = obj.get(key);
            String value;
            if (element.isJsonPrimitive()) {
                value = element.getAsString();
            } else {
                value = element.toString();
            }
            result.put(key, value);
        }
        return result;
    }

    /**
     * 解析 JSON 对象字符串为 Map。
     * 优先使用 Gson 解析，失败时回退到正则匹配。
     */
    public static Map<String, String> parseJsonObject(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || !json.startsWith("{")) return result;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            return parseJsonObject(obj);
        } catch (Exception e) {
            Pattern keyValue = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|\\d+)");
            Matcher m = keyValue.matcher(json);
            while (m.find()) {
                String key = m.group(1);
                String value = m.group(2);
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                result.put(key, value);
            }
        }
        return result;
    }
}
