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
                case LOW:     sb.append("[XX] LOW"); break;
                case UNKNOWN: sb.append("[??] UNKNOWN"); break;
            }
            sb.append(" (验证 ").append(validatedItems).append("/").append(totalItems);
            if (totalItems > 0) {
                sb.append(", 通过率 ").append(String.format("%.0f%%", passRate * 100));
            }
            sb.append(")\n");

            for (AnnotatedItem item : items) {
                String prefix = "[XX]";
                switch (item.confidence) {
                    case CERTAIN: prefix = "[OK]"; break;
                    case HIGH:    prefix = "[!!]"; break;
                    case MEDIUM:  prefix = "[!]"; break;
                    case LOW:     prefix = "[XX]"; break;
                    case UNKNOWN: prefix = "[??]"; break;
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
                ? (double) validationResult.passedCount / validationResult.totalChecked : 0.0;

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
                if (issue.claimedLine > sourceLines.length) {
                    ai.lineOffset = issue.claimedLine - sourceLines.length;
                } else {
                    ai.lineOffset = calculateLineOffset(issue.claimedLine, issue.fieldName, sourceLines);
                }
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
                if (issue.claimedLine > sourceLines.length) {
                    ai.lineOffset = issue.claimedLine - sourceLines.length;
                } else {
                    ai.lineOffset = calculateLineOffset(issue.claimedLine, null, sourceLines);
                }
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
        // 即使从 "methods" 提取，category 仍统一为 "keyMethods"，与 L1 issue key 对齐
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
                if (issue.claimedLine > sourceLines.length) {
                    ai.lineOffset = issue.claimedLine - sourceLines.length;
                } else {
                    ai.lineOffset = calculateLineOffset(issue.claimedLine, issue.fieldName, sourceLines);
                }
            } else {
                ai.confidence = Confidence.MEDIUM;
                ai.reason = "L1 未覆盖";
                ai.lineOffset = -1;
            }
            result.items.add(ai);
        }
    }

    private static String extractJsonArray(String json, String arrayName) {
        return ValidatorUtils.extractJsonArray(json, arrayName);
    }

    private static List<Map<String, String>> parseJsonObjects(String arrayContent) {
        return ValidatorUtils.parseJsonObjects(arrayContent);
    }

    /**
     * 计算行号偏差：在 claimedLine 附近搜索实际匹配行，返回偏差值
     * 正数=LLM报的行号比实际偏后，负数=偏前，0=精确，-1=无法计算
     */
    private static int calculateLineOffset(int claimedLine, String name, String[] sourceLines) {
        if (name == null || sourceLines == null) return -1;
        int searchRange = 5;
        for (int offset = 0; offset <= searchRange; offset++) {
            if (offset == 0) {
                if (claimedLine >= 1 && claimedLine <= sourceLines.length
                        && sourceLines[claimedLine - 1].contains(name)) {
                    return 0;
                }
            } else {
                int before = claimedLine - offset;
                if (before >= 1 && before <= sourceLines.length
                        && sourceLines[before - 1].contains(name)) {
                    return -offset;
                }
                int after = claimedLine + offset;
                if (after >= 1 && after <= sourceLines.length
                        && sourceLines[after - 1].contains(name)) {
                    return offset;
                }
            }
        }
        return -1;
    }

    private static Map<String, String> parseJsonObject(String json) {
        return ValidatorUtils.parseJsonObject(json);
    }
}
