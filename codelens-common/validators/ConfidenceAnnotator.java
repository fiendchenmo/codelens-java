// SYNC_SOURCE: codelens-java/src/main/java/com/codelens/ConfidenceAnnotator.java
// SYNC_VERSION: 2026-05-16-v1
// 维护方：喵呜（CLI端），prompt/校验器相关由喵呜拍板
// 同步说明：依赖 EvidenceValidator，零 IntelliJ SDK 依赖，纯文本处理

package com.codelens.common.validators;

import com.codelens.common.validators.EvidenceValidator.Confidence;
import com.codelens.common.validators.EvidenceValidator.ValidationIssue;
import com.codelens.common.validators.EvidenceValidator.ValidationResult;

import java.util.*;

/**
 * L2 置信度标注器
 * 基于 L1 证据校验结果，为 LLM 分析的每条结果打上置信度标签。
 * 
 * 置信度等级：
 * - CERTAIN：L1 校验通过 + 低风险/无风险，行号精确匹配（偏差0行）
 * - HIGH：L1 校验通过 + 中风险，或行号偏差1-2行
 * - MEDIUM：L1 校验通过但行号偏移 >2，或 L1 未覆盖的条目
 * - LOW：L1 校验失败（行号超出/名称不匹配）
 */
public class ConfidenceAnnotator {

    public static class AnnotatedItem {
        public String category;
        public int index;
        public Map<String, String> fields;
        public Confidence confidence;
        public String reason;
        public int lineOffset;  // 行号偏差值（0=精确，-1=无法计算）

        @Override
        public String toString() {
            String offsetInfo = lineOffset >= 0 ? " (偏差" + lineOffset + "行)" : "";
            return category + "[" + index + "]: " + confidence + offsetInfo + " (" + reason + ")";
        }
    }

    public static class AnnotatedResult {
        public List<AnnotatedItem> items = new ArrayList<>();
        public Confidence overallConfidence;
        public int totalItems;
        public int validatedItems;
        public double passRate;

        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("L2 置信度标注: ");
            switch (overallConfidence) {
                case CERTAIN: sb.append("[OK] CERTAIN"); break;
                case HIGH:    sb.append("[!!] HIGH"); break;
                case MEDIUM:  sb.append("[!] MEDIUM"); break;
                default:      sb.append("[XX] LOW"); break;
            }
            sb.append(" (验证 ").append(validatedItems).append("/").append(totalItems)
              .append(", 通过率 ").append(String.format("%.0f%%", passRate * 100)).append(")\n");

            for (AnnotatedItem item : items) {
                String prefix;
                switch (item.confidence) {
                    case CERTAIN: prefix = "[OK]"; break;
                    case HIGH:    prefix = "[!!]"; break;
                    case MEDIUM:  prefix = "[!]"; break;
                    default:      prefix = "[XX]"; break;
                }
                sb.append(prefix).append(" ").append(item.category).append("[")
                  .append(item.index).append("] ");

                String name = item.fields.get("name");
                String desc = item.fields.get("description");
                String label = (name != null && !name.isEmpty()) ? name : desc;
                if (label != null && label.length() > 50) {
                    label = label.substring(0, 50) + "...";
                }
                sb.append(label != null ? label : "?");

                // 显示行号偏差
                if (item.lineOffset >= 0) {
                    sb.append(" [偏差").append(item.lineOffset).append("行]");
                }
                sb.append(" — ").append(item.reason).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * 为 LLM JSON 结果添加置信度标注
     */
    public static AnnotatedResult annotate(String llmJson,
                                           ValidationResult validationResult,
                                           String[] sourceLines) {
        AnnotatedResult result = new AnnotatedResult();
        result.overallConfidence = validationResult.overallConfidence();
        result.validatedItems = validationResult.totalChecked;
        result.passRate = validationResult.totalChecked > 0
                ? (double) validationResult.passedCount / validationResult.totalChecked : 1.0;

        // 构建 L1 issues 索引：category+index -> issue
        Map<String, ValidationIssue> issueMap = new LinkedHashMap<>();
        for (ValidationIssue issue : validationResult.issues) {
            String key = issue.category + ":" + issue.index;
            issueMap.put(key, issue);
        }

        // 标注 dependencies
        annotateDependencies(llmJson, sourceLines, issueMap, result);
        // 标注 risks
        annotateRisks(llmJson, sourceLines, issueMap, result);
        // 标注 keyMethods
        annotateKeyMethods(llmJson, sourceLines, issueMap, result);

        result.totalItems = result.items.size();
        return result;
    }

    private static void annotateDependencies(String json, String[] sourceLines,
                                             Map<String, ValidationIssue> issueMap,
                                             AnnotatedResult result) {
        String arrayContent = extractJsonArray(json, "dependencies");
        if (arrayContent == null) return;

        List<Map<String, String>> items = parseJsonObjects(arrayContent);
        for (int i = 0; i < items.size(); i++) {
            Map<String, String> item = items.get(i);
            AnnotatedItem ai = new AnnotatedItem();
            ai.category = "dependencies";
            ai.index = i;
            ai.fields = item;

            String key = "dependencies:" + i;
            ValidationIssue issue = issueMap.get(key);

            if (issue != null) {
                ai.confidence = issue.confidence;
                ai.reason = issue.actualValue != null
                    ? "行号正确，但字段名不匹配: " + issue.actualValue
                    : issue.issue;
                ai.lineOffset = 0;
            } else {
                String lineStr = item.get("line");
                if (lineStr != null && sourceLines != null) {
                    try {
                        int claimedLine = Integer.parseInt(lineStr.trim());
                        if (claimedLine >= 1 && claimedLine <= sourceLines.length) {
                            ai.confidence = Confidence.CERTAIN;
                            ai.reason = "L1 校验通过，行号精确匹配";
                            ai.lineOffset = 0;
                        } else {
                            ai.confidence = Confidence.LOW;
                            ai.reason = "行号超出范围";
                            ai.lineOffset = -1;
                        }
                    } catch (NumberFormatException e) {
                        ai.confidence = Confidence.LOW;
                        ai.reason = "无效行号格式";
                        ai.lineOffset = -1;
                    }
                } else {
                    ai.confidence = Confidence.MEDIUM;
                    ai.reason = "L1 未覆盖";
                    ai.lineOffset = -1;
                }
            }
            result.items.add(ai);
        }
    }

    private static void annotateRisks(String json, String[] sourceLines,
                                      Map<String, ValidationIssue> issueMap,
                                      AnnotatedResult result) {
        String arrayContent = extractJsonArray(json, "risks");
        if (arrayContent == null) return;

        List<Map<String, String>> items = parseJsonObjects(arrayContent);
        for (int i = 0; i < items.size(); i++) {
            Map<String, String> item = items.get(i);
            AnnotatedItem ai = new AnnotatedItem();
            ai.category = "risks";
            ai.index = i;
            ai.fields = item;

            String key = "risks:" + i;
            ValidationIssue issue = issueMap.get(key);

            if (issue != null) {
                ai.confidence = issue.confidence;
                ai.reason = issue.issue;
                ai.lineOffset = 0;
            } else {
                ai.confidence = Confidence.HIGH;
                ai.reason = "L1 校验通过";
                ai.lineOffset = 0;
            }
            result.items.add(ai);
        }
    }

    private static void annotateKeyMethods(String json, String[] sourceLines,
                                           Map<String, ValidationIssue> issueMap,
                                           AnnotatedResult result) {
        String arrayContent = extractJsonArray(json, "keyMethods");
        if (arrayContent == null) arrayContent = extractJsonArray(json, "methods");
        if (arrayContent == null) return;

        List<Map<String, String>> items = parseJsonObjects(arrayContent);
        for (int i = 0; i < items.size(); i++) {
            Map<String, String> item = items.get(i);
            AnnotatedItem ai = new AnnotatedItem();
            ai.category = "keyMethods";
            ai.index = i;
            ai.fields = item;

            String key = "keyMethods:" + i;
            ValidationIssue issue = issueMap.get(key);

            if (issue != null) {
                ai.confidence = issue.confidence;
                ai.reason = issue.issue;
                ai.lineOffset = 0;
            } else {
                ai.confidence = Confidence.MEDIUM;
                ai.reason = "L1 未覆盖";
                ai.lineOffset = -1;
            }
            result.items.add(ai);
        }
    }

    private static String extractJsonArray(String json, String arrayName) {
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

    private static List<Map<String, String>> parseJsonObjects(String arrayContent) {
        List<Map<String, String>> result = new ArrayList<>();
        if (arrayContent == null || arrayContent.trim().isEmpty()) return result;

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

    private static Map<String, String> parseJsonObject(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || !json.startsWith("{")) return result;

        java.util.regex.Pattern keyValue = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|\\d+)");
        java.util.regex.Matcher m = keyValue.matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String value = m.group(2);
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            result.put(key, value);
        }
        return result;
    }
}
