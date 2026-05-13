package com.codelens;

import java.util.*;
import java.util.regex.*;

public class EvidenceValidator {

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
                    continue;
                }
                if (findInNearbyLines(sourceLines, claimedLine, name, 2)) {
                    result.passedCount++;
                } else {
                    addIssue(result, "dependencies", i, claimedLine, "name", name,
                            sourceLines[claimedLine - 1].trim(), "行号附近未找到依赖名称", Confidence.MEDIUM);
                }
            } catch (NumberFormatException e) { /* skip */ }
        }
    }

    private static void validateRisks(String json, String[] sourceLines, ValidationResult result) {
        String arrayContent = extractJsonArray(json, "risks");
        if (arrayContent == null) return;
        List<Map<String, String>> items = parseJsonObjects(arrayContent);
        for (int i = 0; i < items.size(); i++) {
            Map<String, String> item = items.get(i);
            String lineStr = item.get("line");
            String description = item.get("description");
            if (lineStr == null || description == null) continue;
            try {
                int claimedLine = Integer.parseInt(lineStr.trim());
                result.totalChecked++;
                if (claimedLine < 1 || claimedLine > sourceLines.length) {
                    addIssue(result, "risks", i, claimedLine, "description", description, null,
                            "行号超出源码范围（源码共 " + sourceLines.length + " 行）", Confidence.LOW);
                    continue;
                }
                result.passedCount++;
            } catch (NumberFormatException e) { /* skip */ }
        }
    }

    private static void validateKeyMethods(String json, String[] sourceLines, ValidationResult result) {
        String arrayContent = extractJsonArray(json, "key_methods");
        if (arrayContent == null) return;
        List<Map<String, String>> items = parseJsonObjects(arrayContent);
        for (int i = 0; i < items.size(); i++) {
            Map<String, String> item = items.get(i);
            String lineStr = item.get("line");
            String methodName = item.get("name");
            if (lineStr == null || methodName == null) continue;
            try {
                int claimedLine = Integer.parseInt(lineStr.trim());
                result.totalChecked++;
                if (claimedLine < 1 || claimedLine > sourceLines.length) {
                    addIssue(result, "key_methods", i, claimedLine, "name", methodName, null,
                            "行号超出源码范围", Confidence.LOW);
                    continue;
                }
                if (findMethodDefinition(sourceLines, claimedLine, methodName, 3)) {
                    result.passedCount++;
                } else {
                    addIssue(result, "key_methods", i, claimedLine, "name", methodName,
                            sourceLines[claimedLine - 1].trim(), "行号附近未找到方法定义", Confidence.MEDIUM);
                }
            } catch (NumberFormatException e) { /* skip */ }
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

    static boolean findInNearbyLines(String[] lines, int centerLine, String keyword, int range) {
        String[] parts = keyword.split("[./\\\\\\s]+");
        String searchTerm = parts[0];
        for (String p : parts) {
            if (p.length() > searchTerm.length() && p.length() > 2) {
                searchTerm = p;
            }
        }
        if (searchTerm.length() <= 3 && keyword.contains(".")) {
            String[] dotParts = keyword.split("\\.");
            searchTerm = dotParts[dotParts.length - 1];
            if (searchTerm.length() <= 2 && dotParts.length > 1) {
                searchTerm = dotParts[dotParts.length - 2];
            }
            if (searchTerm.length() <= 2) searchTerm = keyword;
        }
        int start = Math.max(1, centerLine - range);
        int end = Math.min(lines.length, centerLine + range);
        for (int i = start; i <= end; i++) {
            if (lines[i - 1].contains(searchTerm) || lines[i - 1].contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    static boolean findMethodDefinition(String[] lines, int centerLine, String methodName, int range) {
        // Extract simple method name: remove parameter signature
        // LLM may return "generatorCode(String)" but source has "generatorCode(String tableName, ZipOutputStream...)"
        String simpleName = methodName;
        int parenIdx = simpleName.indexOf('(');
        if (parenIdx > 0) simpleName = simpleName.substring(0, parenIdx).trim();
        
        int start = Math.max(1, centerLine - range);
        int end = Math.min(lines.length, centerLine + range);
        for (int i = start; i <= end; i++) {
            if (lines[i - 1].contains(simpleName)) {
                return true;
            }
        }
        return false;
    }

    static String extractJsonArray(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int idx = json.indexOf(searchKey);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + searchKey.length());
        if (colonIdx < 0) return null;
        int bracketStart = json.indexOf('[', colonIdx);
        if (bracketStart < 0) return null;
        int depth = 1;
        int i = bracketStart + 1;
        boolean inString = false;
        while (i < json.length() && depth > 0) {
            char c = json.charAt(i);
            if (c == '\\' && inString) { i += 2; continue; }
            if (c == '"') inString = !inString;
            if (!inString) {
                if (c == '[') depth++;
                else if (c == ']') depth--;
            }
            i++;
        }
        return json.substring(bracketStart, i);
    }

    static List<Map<String, String>> parseJsonObjects(String arrayJson) {
        List<Map<String, String>> result = new ArrayList<>();
        if (arrayJson == null || !arrayJson.startsWith("[")) return result;
        int i = 1;
        while (i < arrayJson.length()) {
            int objStart = arrayJson.indexOf('{', i);
            if (objStart < 0) break;
            int depth = 1;
            int j = objStart + 1;
            boolean inStr = false;
            while (j < arrayJson.length() && depth > 0) {
                char c = arrayJson.charAt(j);
                if (c == '\\' && inStr) { j += 2; continue; }
                if (c == '"') inStr = !inStr;
                if (!inStr) {
                    if (c == '{') depth++;
                    else if (c == '}') depth--;
                }
                j++;
            }
            result.add(parseFlatJsonObject(arrayJson.substring(objStart, j)));
            i = j;
        }
        return result;
    }

    static Map<String, String> parseFlatJsonObject(String objJson) {
        Map<String, String> map = new LinkedHashMap<>();
        String content = objJson.trim();
        if (content.startsWith("{")) content = content.substring(1);
        if (content.endsWith("}")) content = content.substring(0, content.length() - 1);
        Pattern p = Pattern.compile("\"(\\w+)\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([\\d]+))");
        Matcher m = p.matcher(content);
        while (m.find()) {
            String key = m.group(1);
            String strVal = m.group(2);
            String numVal = m.group(3);
            if (strVal != null) {
                map.put(key, unescapeJson(strVal));
            } else if (numVal != null) {
                map.put(key, numVal);
            }
        }
        return map;
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
