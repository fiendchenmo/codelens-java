// SYNC_SOURCE: codelens-java/src/main/java/com/codelens/EvidenceValidator.java
// SYNC_VERSION: 2026-05-16-v2
// 维护方：喵呜（CLI端），prompt/校验器相关由喵呜拍板
// 同步说明：零 IntelliJ SDK 依赖，纯文本处理，CLI 单测可覆盖

package com.codelens.common.validators;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;
import java.util.regex.*;

/**
 * L1 证据校验器
 * 验证 LLM 生成的 JSON 分析结果中引用的行号、字段名是否与源码匹配。
 * 
 * 使用方式：
 * 1. 调用 validate() 获取校验结果
 * 2. 检查 result.issues 列表
 * 3. 调用 result.formatReport() 生成可读报告
 */
public class EvidenceValidator {

    private static final Gson GSON = new GsonBuilder().create();

    public enum Confidence {
        CERTAIN, HIGH, MEDIUM, LOW
    }

    public static class ValidationIssue {
        public String category;
        public int index;
        public int claimedLine;
        public String fieldName;
        public String claimedValue;
        public String actualValue;
        public String issue;
        public Confidence confidence;

        @Override
        public String toString() {
            String loc = category + "[" + index + "]";
            String line = "L" + claimedLine;
            if (actualValue != null && !actualValue.isEmpty()) {
                String truncated = actualValue.length() > 60 ? actualValue.substring(0, 60) + "..." : actualValue;
                return loc + ": " + issue + " (" + line + " " + claimedValue + " -> 实际: " + truncated + ")";
            }
            return loc + ": " + issue + " (" + line + " " + claimedValue + ")";
        }
    }

    public static class ValidationResult {
        public List<ValidationIssue> issues = new ArrayList<>();
        public int totalChecked = 0;
        public int passedCount = 0;

        public Confidence overallConfidence() {
            if (totalChecked == 0) return Confidence.CERTAIN;
            double passRate = (double) passedCount / totalChecked;
            if (passRate >= 1.0) return Confidence.CERTAIN;
            if (passRate >= 0.8) return Confidence.HIGH;
            if (passRate >= 0.5) return Confidence.MEDIUM;
            return Confidence.LOW;
        }

        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            Confidence c = overallConfidence();
            String label;
            switch (c) {
                case CERTAIN: label = "[OK] CERTAIN"; break;
                case HIGH:    label = "[!!] HIGH";    break;
                case MEDIUM:  label = "[!] MEDIUM";  break;
                default:      label = "[XX] LOW";     break;
            }
            sb.append("校验结果: ").append(label)
              .append(" (").append(passedCount).append("/").append(totalChecked).append(" 通过)\n");
            if (!issues.isEmpty()) {
                for (ValidationIssue issue : issues) {
                    String prefix;
                    switch (issue.confidence) {
                        case LOW:    prefix = "[XX]"; break;
                        case MEDIUM: prefix = "[!]"; break;
                        default:     prefix = "[!!]"; break;
                    }
                    sb.append(prefix).append(" ").append(issue.toString()).append("\n");
                }
            }
            return sb.toString();
        }
    }

    public static ValidationResult validate(String llmJson, String sourceCode, String[] sourceLines) {
        if (sourceLines == null) {
            sourceLines = sourceCode.split("\n");
        }
        ValidationResult result = new ValidationResult();
        validateDependencies(llmJson, sourceLines, result);
        validateRisks(llmJson, sourceLines, result);
        validateKeyMethods(llmJson, sourceLines, result);
        return result;
    }

    private static void validateDependencies(String json, String[] sourceLines, ValidationResult result) {
        String arrayContent = extractJsonArray(json, "dependencies");
        if (arrayContent == null) return;
        List<Map<String, String>> items = parseJsonObjects(arrayContent);
        for (int i = 0; i < items.size(); i++) {
            Map<String, String> item = items.get(i);
            String lineStr = item.get("line");
            String name = item.get("name");
            if (lineStr == null || name == null) continue;
            try {
                int claimedLine = Integer.parseInt(lineStr.trim());
                result.totalChecked++;
                if (claimedLine < 1 || claimedLine > sourceLines.length) {
                    addIssue(result, "dependencies", i, claimedLine, "name", name, null,
                            "行号超出源码范围（源码共 " + sourceLines.length + " 行）", Confidence.LOW);
                } else {
                    String actualLine = sourceLines[claimedLine - 1];
                    if (actualLine.contains(name) || name.contains("Mapper") && actualLine.contains("@Mapper")) {
                        result.passedCount++;
                    } else {
                        // 尝试模糊匹配（检查行首非空字符）
                        String trimmed = actualLine.trim();
                        if (!trimmed.startsWith("//") && !trimmed.startsWith("*")) {
                            addIssue(result, "dependencies", i, claimedLine, "name", name, actualLine.trim(),
                                    "行内容中未找到字段名 '" + name + "'", Confidence.MEDIUM);
                        } else {
                            result.passedCount++;
                        }
                    }
                }
            } catch (NumberFormatException e) {
                addIssue(result, "dependencies", i, 0, "line", lineStr, null,
                        "无效的行号格式", Confidence.LOW);
            }
        }
    }

    private static void validateRisks(String json, String[] sourceLines, ValidationResult result) {
        String arrayContent = extractJsonArray(json, "risks");
        if (arrayContent == null) return;
        List<Map<String, String>> items = parseJsonObjects(arrayContent);
        for (int i = 0; i < items.size(); i++) {
            Map<String, String> item = items.get(i);
            String lineStr = item.get("line");
            if (lineStr == null) continue;
            try {
                int claimedLine = Integer.parseInt(lineStr.trim());
                result.totalChecked++;
                if (claimedLine >= 1 && claimedLine <= sourceLines.length) {
                    result.passedCount++;
                } else {
                    addIssue(result, "risks", i, claimedLine, "line", lineStr, null,
                            "行号超出源码范围", Confidence.LOW);
                }
            } catch (NumberFormatException e) {
                addIssue(result, "risks", i, 0, "line", lineStr, null,
                        "无效的行号格式", Confidence.LOW);
            }
        }
    }

    private static void validateKeyMethods(String json, String[] sourceLines, ValidationResult result) {
        String arrayContent = extractJsonArray(json, "keyMethods");
        if (arrayContent == null) arrayContent = extractJsonArray(json, "methods");
        if (arrayContent == null) return;
        List<Map<String, String>> items = parseJsonObjects(arrayContent);
        for (int i = 0; i < items.size(); i++) {
            Map<String, String> item = items.get(i);
            String lineStr = item.get("line");
            String name = item.get("name");
            if (lineStr == null) continue;
            try {
                int claimedLine = Integer.parseInt(lineStr.trim());
                result.totalChecked++;
                if (claimedLine >= 1 && claimedLine <= sourceLines.length) {
                    result.passedCount++;
                } else {
                    addIssue(result, "keyMethods", i, claimedLine, "line", lineStr, null,
                            "行号超出源码范围", Confidence.LOW);
                }
            } catch (NumberFormatException e) {
                addIssue(result, "keyMethods", i, 0, "line", lineStr, null,
                        "无效的行号格式", Confidence.LOW);
            }
        }
    }

    private static void addIssue(ValidationResult result, String category, int index,
                                  int claimedLine, String fieldName, String claimedValue,
                                  String actualValue, String issue, Confidence confidence) {
        ValidationIssue vi = new ValidationIssue();
        vi.category = category;
        vi.index = index;
        vi.claimedLine = claimedLine;
        vi.fieldName = fieldName;
        vi.claimedValue = claimedValue;
        vi.actualValue = actualValue;
        vi.issue = issue;
        vi.confidence = confidence;
        result.issues.add(vi);
    }

    public static String extractJsonArray(String json, String arrayName) {
        String search = "\"" + arrayName + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        // 找第一个 '['
        int start = json.indexOf('[', colon);
        if (start < 0) return null;
        // 找匹配的 ']'
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

    public static List<Map<String, String>> parseJsonObjects(String arrayContent) {
        List<Map<String, String>> result = new ArrayList<>();
        if (arrayContent == null || arrayContent.trim().isEmpty()) return result;

        try {
            // 用 Gson 解析 JSON 数组
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
            // 备用：原始手写解析
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

    private static int findMatchingBrace(String s, int start) {
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

    private static Map<String, String> parseJsonObject(JsonObject obj) {
        Map<String, String> result = new LinkedHashMap<>();
        if (obj == null) return result;
        for (String key : obj.keySet()) {
            JsonElement element = obj.get(key);
            String value;
            if (element.isJsonPrimitive()) {
                value = element.getAsString();
            } else {
                // 嵌套对象或数组，转为字符串
                value = element.toString();
            }
            result.put(key, value);
        }
        return result;
    }

    private static Map<String, String> parseJsonObject(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || !json.startsWith("{")) return result;
        try {
            // 优先用 Gson 解析
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            return parseJsonObject(obj);
        } catch (Exception e) {
            // 备用：正则解析（不支持布尔/嵌套/转义）
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
