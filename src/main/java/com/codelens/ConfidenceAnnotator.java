package com.codelens;

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
        public EvidenceValidator.Confidence confidence;
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
        public EvidenceValidator.Confidence overallConfidence;
        public int totalItems;
        public int validatedItems;
        public double passRate;

        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("L2 置信度标注: ");
            switch (overallConfidence) {
                case CERTAIN: sb.append(ColorUtil.certain("[OK] CERTAIN")); break;
                case HIGH:    sb.append(ColorUtil.high("[!!] HIGH")); break;
                case MEDIUM:  sb.append(ColorUtil.medium("[!] MEDIUM")); break;
                default:      sb.append(ColorUtil.low("[XX] LOW")); break;
            }
            sb.append(" (验证 ").append(validatedItems).append("/").append(totalItems)
              .append(", 通过率 ").append(String.format("%.0f%%", passRate * 100)).append(")\n");

            for (AnnotatedItem item : items) {
                String prefix;
                switch (item.confidence) {
                    case CERTAIN: prefix = ColorUtil.certain("[OK]"); break;
                    case HIGH:    prefix = ColorUtil.high("[!!]"); break;
                    case MEDIUM:  prefix = ColorUtil.medium("[!]"); break;
                    default:      prefix = ColorUtil.low("[XX]"); break;
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
                                           EvidenceValidator.ValidationResult validationResult,
                                           String[] sourceLines) {
        AnnotatedResult result = new AnnotatedResult();
        result.overallConfidence = validationResult.overallConfidence();
        result.validatedItems = validationResult.totalChecked;
        result.passRate = validationResult.totalChecked > 0
                ? (double) validationResult.passedCount / validationResult.totalChecked : 1.0;

        // 构建 L1 issues 索引：category+index -> issue
        Map<String, EvidenceValidator.ValidationIssue> issueMap = new LinkedHashMap<>();
        for (EvidenceValidator.ValidationIssue issue : validationResult.issues) {
            String key = issue.category + ":" + issue.index;
            issueMap.put(key, issue);
        }

        // 标注 dependencies
        annotateDependencies(llmJson, sourceLines, issueMap, result);

        // 标注 risks
        annotateRisks(llmJson, sourceLines, issueMap, result);

        // 标注 key_methods
        annotateKeyMethods(llmJson, sourceLines, issueMap, result);

        // 标注其他无法校验的条目
        annotateUnvalidated(llmJson, result);

        result.totalItems = result.items.size();
        return result;
    }

    private static void annotateDependencies(String json, String[] sourceLines,
                                              Map<String, EvidenceValidator.ValidationIssue> issueMap,
                                              AnnotatedResult result) {
        String arrayContent = EvidenceValidator.extractJsonArray(json, "dependencies");
        if (arrayContent == null) return;
        List<Map<String, String>> items = EvidenceValidator.parseJsonObjects(arrayContent);
        for (int i = 0; i < items.size(); i++) {
            AnnotatedItem ai = new AnnotatedItem();
            ai.category = "dependencies";
            ai.index = i;
            ai.fields = items.get(i);
            ai.lineOffset = -1;

            String key = "dependencies:" + i;
            EvidenceValidator.ValidationIssue issue = issueMap.get(key);

            if (issue != null) {
                if (issue.confidence == EvidenceValidator.Confidence.LOW) {
                    ai.confidence = EvidenceValidator.Confidence.LOW;
                    ai.reason = "行号超出范围或名称不匹配";
                } else {
                    int offset = computeLineOffset(sourceLines, issue.claimedLine, items.get(i).get("name"));
                    ai.lineOffset = offset;
                    if (offset <= 2) {
                        ai.confidence = EvidenceValidator.Confidence.MEDIUM;
                        ai.reason = "行号偏移" + offset + "行，名称在附近找到";
                    } else {
                        ai.confidence = EvidenceValidator.Confidence.LOW;
                        ai.reason = "行号偏移" + offset + "行，偏差过大";
                    }
                }
            } else {
                String lineStr = items.get(i).get("line");
                String name = items.get(i).get("name");
                if (lineStr != null && name != null && sourceLines != null) {
                    int line = Integer.parseInt(lineStr.trim());
                    String simpleName = extractSimpleName(name);
                    if (line >= 1 && line <= sourceLines.length && sourceLines[line - 1].contains(simpleName)) {
                        ai.confidence = EvidenceValidator.Confidence.CERTAIN;
                        ai.lineOffset = 0;
                        ai.reason = "行号精确匹配";
                    } else {
                        int offset = computeLineOffset(sourceLines, line, name);
                        ai.lineOffset = offset;
                        if (offset <= 2) {
                            ai.confidence = EvidenceValidator.Confidence.HIGH;
                            ai.reason = "行号偏差" + offset + "行，附近匹配";
                        } else {
                            ai.confidence = EvidenceValidator.Confidence.MEDIUM;
                            ai.reason = "行号偏差" + offset + "行";
                        }
                    }
                } else {
                    ai.confidence = EvidenceValidator.Confidence.MEDIUM;
                    ai.reason = "无法校验行号";
                }
            }
            result.items.add(ai);
        }
    }

    private static void annotateRisks(String json, String[] sourceLines,
                                       Map<String, EvidenceValidator.ValidationIssue> issueMap,
                                       AnnotatedResult result) {
        String arrayContent = EvidenceValidator.extractJsonArray(json, "risks");
        if (arrayContent == null) return;
        List<Map<String, String>> items = EvidenceValidator.parseJsonObjects(arrayContent);
        for (int i = 0; i < items.size(); i++) {
            AnnotatedItem ai = new AnnotatedItem();
            ai.category = "risks";
            ai.index = i;
            ai.fields = items.get(i);
            ai.lineOffset = -1;

            String key = "risks:" + i;
            EvidenceValidator.ValidationIssue issue = issueMap.get(key);

            String severity = items.get(i).get("severity");
            String lineStr = items.get(i).get("line");

            if (issue != null) {
                if (issue.confidence == EvidenceValidator.Confidence.LOW) {
                    ai.confidence = EvidenceValidator.Confidence.LOW;
                    ai.reason = "行号超出范围";
                } else {
                    int offset = computeLineOffsetForRisk(sourceLines, issue.claimedLine, items.get(i).get("description"));
                    ai.lineOffset = offset;
                    ai.confidence = EvidenceValidator.Confidence.MEDIUM;
                    ai.reason = "行号偏差" + offset + "行，风险描述存在";
                }
            } else {
                // L1 通过 — 根据 severity 加权 + 计算行号偏差
                int offset = -1;
                if (lineStr != null && sourceLines != null) {
                    try {
                        int line = Integer.parseInt(lineStr.trim());
                        if (line >= 1 && line <= sourceLines.length) {
                            offset = 0;  // L1通过说明行号有效
                        }
                    } catch (NumberFormatException e) { /* ignore */ }
                }
                ai.lineOffset = offset >= 0 ? 0 : -1;

                if ("高".equals(severity)) {
                    ai.confidence = EvidenceValidator.Confidence.HIGH;
                    ai.reason = "高风险，行号校验通过";
                } else if ("中".equals(severity)) {
                    ai.confidence = EvidenceValidator.Confidence.HIGH;
                    ai.reason = "中风险，行号校验通过";
                } else {
                    ai.confidence = EvidenceValidator.Confidence.CERTAIN;
                    ai.reason = "低风险，行号校验通过";
                }
            }
            result.items.add(ai);
        }
    }

    private static void annotateKeyMethods(String json, String[] sourceLines,
                                            Map<String, EvidenceValidator.ValidationIssue> issueMap,
                                            AnnotatedResult result) {
        String arrayContent = EvidenceValidator.extractJsonArray(json, "key_methods");
        if (arrayContent == null) return;
        List<Map<String, String>> items = EvidenceValidator.parseJsonObjects(arrayContent);
        for (int i = 0; i < items.size(); i++) {
            AnnotatedItem ai = new AnnotatedItem();
            ai.category = "key_methods";
            ai.index = i;
            ai.fields = items.get(i);
            ai.lineOffset = -1;

            String key = "key_methods:" + i;
            EvidenceValidator.ValidationIssue issue = issueMap.get(key);

            if (issue != null) {
                if (issue.confidence == EvidenceValidator.Confidence.LOW) {
                    ai.confidence = EvidenceValidator.Confidence.LOW;
                    ai.reason = "行号超出范围或方法名未找到";
                } else {
                    int offset = computeMethodLineOffset(sourceLines, issue.claimedLine, items.get(i).get("name"));
                    ai.lineOffset = offset;
                    if (offset <= 2) {
                        ai.confidence = EvidenceValidator.Confidence.MEDIUM;
                        ai.reason = "行号偏差" + offset + "行，方法名在附近找到";
                    } else {
                        ai.confidence = EvidenceValidator.Confidence.LOW;
                        ai.reason = "行号偏差" + offset + "行，偏差过大";
                    }
                }
            } else {
                String lineStr = items.get(i).get("line");
                String name = items.get(i).get("name");
                if (lineStr != null && name != null && sourceLines != null) {
                    int line = Integer.parseInt(lineStr.trim());
                    String simpleName = extractSimpleName(name);
                    if (line >= 1 && line <= sourceLines.length && sourceLines[line - 1].contains(simpleName)) {
                        ai.confidence = EvidenceValidator.Confidence.CERTAIN;
                        ai.lineOffset = 0;
                        ai.reason = "行号精确匹配方法定义";
                    } else {
                        int offset = computeMethodLineOffset(sourceLines, line, name);
                        ai.lineOffset = offset;
                        if (offset <= 2) {
                            ai.confidence = EvidenceValidator.Confidence.HIGH;
                            ai.reason = "行号偏差" + offset + "行，附近匹配方法定义";
                        } else {
                            ai.confidence = EvidenceValidator.Confidence.MEDIUM;
                            ai.reason = "行号偏差" + offset + "行";
                        }
                    }
                } else {
                    ai.confidence = EvidenceValidator.Confidence.MEDIUM;
                    ai.reason = "无法校验行号";
                }
            }
            result.items.add(ai);
        }
    }

    private static void annotateUnvalidated(String json, AnnotatedResult result) {
        String[] textFields = {"summary", "design_intent", "class_analysis", "framework_integration"};
        for (String field : textFields) {
            String value = extractStringValue(json, field);
            if (value != null && !value.isEmpty()) {
                AnnotatedItem ai = new AnnotatedItem();
                ai.category = field;
                ai.index = 0;
                ai.fields = new LinkedHashMap<>();
                ai.fields.put("value", value.length() > 60 ? value.substring(0, 60) + "..." : value);
                ai.confidence = EvidenceValidator.Confidence.MEDIUM;
                ai.reason = "文本性结论，需人工复核";
                ai.lineOffset = -1;
                result.items.add(ai);
            }
        }

        // architecture_issues
        String arrayContent = EvidenceValidator.extractJsonArray(json, "architecture_issues");
        if (arrayContent != null) {
            List<Map<String, String>> items = EvidenceValidator.parseJsonObjects(arrayContent);
            for (int i = 0; i < items.size(); i++) {
                AnnotatedItem ai = new AnnotatedItem();
                ai.category = "architecture_issues";
                ai.index = i;
                ai.fields = items.get(i);
                ai.confidence = EvidenceValidator.Confidence.MEDIUM;
                ai.reason = "架构级问题，需人工复核";
                ai.lineOffset = -1;
                result.items.add(ai);
            }
        }
    }

    /**
     * 在 LLM JSON 结果中注入 confidence 字段和 validation 摘要
     */
    public static String injectConfidenceIntoJson(String llmJson, AnnotatedResult annotatedResult,
                                                   EvidenceValidator.ValidationResult validationResult) {
        String result = llmJson;

        result = injectConfidenceToArray(result, "dependencies", annotatedResult);
        result = injectConfidenceToArray(result, "risks", annotatedResult);
        result = injectConfidenceToArray(result, "key_methods", annotatedResult);

        String validationJson = String.format(
            "\"validation\": {\"overall_confidence\": \"%s\", \"pass_rate\": %.2f, \"checked\": %d, \"passed\": %d}",
            validationResult.overallConfidence().name(),
            annotatedResult.passRate,
            validationResult.totalChecked,
            validationResult.passedCount
        );

        int lastBrace = result.lastIndexOf('}');
        if (lastBrace > 0) {
            result = result.substring(0, lastBrace) + ",\n  " + validationJson + "\n}";
        }

        return result;
    }

    private static String injectConfidenceToArray(String json, String arrayName, AnnotatedResult result) {
        List<AnnotatedItem> items = new ArrayList<>();
        for (AnnotatedItem ai : result.items) {
            if (ai.category.equals(arrayName)) {
                items.add(ai);
            }
        }
        if (items.isEmpty()) return json;

        String arrayContent = EvidenceValidator.extractJsonArray(json, arrayName);
        if (arrayContent == null) return json;

        String modified = arrayContent;
        for (int i = items.size() - 1; i >= 0; i--) {
            AnnotatedItem ai = items.get(i);
            String confidenceField = ", \"confidence\": \"" + ai.confidence.name() + "\""
                + (ai.lineOffset >= 0 ? ", \"line_offset\": " + ai.lineOffset : "");
            int objCount = 0;
            for (int j = modified.length() - 1; j >= 0; j--) {
                if (modified.charAt(j) == '}') {
                    objCount++;
                    if (objCount == i + 1) {
                        modified = modified.substring(0, j) + confidenceField + modified.substring(j);
                        break;
                    }
                }
            }
        }

        int arrayStart = json.indexOf(arrayContent);
        if (arrayStart >= 0) {
            return json.substring(0, arrayStart) + modified + json.substring(arrayStart + arrayContent.length());
        }
        return json;
    }

    // ========== 行号偏差计算 ==========

    /**
     * 计算依赖名称在源码中的最小行号偏差
     */
    static int computeLineOffset(String[] sourceLines, int claimedLine, String keyword) {
        if (sourceLines == null || keyword == null) return -1;
        String searchTerm = extractSimpleName(keyword);
        if (searchTerm.isEmpty()) return -1;
        int bestOffset = Integer.MAX_VALUE;
        int searchRange = Math.min(10, sourceLines.length);
        for (int offset = 0; offset <= searchRange; offset++) {
            int upLine = claimedLine - 1 - offset;
            int downLine = claimedLine - 1 + offset;
            if (upLine >= 0 && upLine < sourceLines.length && sourceLines[upLine].contains(searchTerm)) {
                return offset;
            }
            if (offset > 0 && downLine >= 0 && downLine < sourceLines.length && sourceLines[downLine].contains(searchTerm)) {
                return offset;
            }
        }
        return -1;
    }

    /**
     * 计算风险描述在源码中的最小行号偏差
     */
    static int computeLineOffsetForRisk(String[] sourceLines, int claimedLine, String description) {
        if (sourceLines == null || description == null) return 0;
        // risks 不做名称匹配，L1只校验行号范围，偏差=0表示行号在范围内
        return 0;
    }

    /**
     * 计算方法名在源码中的最小行号偏差
     */
    static int computeMethodLineOffset(String[] sourceLines, int claimedLine, String methodName) {
        if (sourceLines == null || methodName == null) return -1;
        String simpleName = extractSimpleName(methodName);
        if (simpleName.isEmpty()) return -1;
        int bestOffset = -1;
        int searchRange = Math.min(10, sourceLines.length);
        for (int offset = 0; offset <= searchRange; offset++) {
            int upLine = claimedLine - 1 - offset;
            int downLine = claimedLine - 1 + offset;
            if (upLine >= 0 && upLine < sourceLines.length && sourceLines[upLine].contains(simpleName)) {
                return offset;
            }
            if (offset > 0 && downLine >= 0 && downLine < sourceLines.length && sourceLines[downLine].contains(simpleName)) {
                return offset;
            }
        }
        return -1;
    }

    /**
     * 从 JSON 中提取字符串字段值
     */
    static String extractStringValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int idx = json.indexOf(searchKey);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + searchKey.length());
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        start++;
        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append(json.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '"') break;
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * 提取简单名称（去掉包名和参数签名）
     */
    static String extractSimpleName(String name) {
        if (name == null) return "";
        int paren = name.indexOf('(');
        if (paren > 0) name = name.substring(0, paren);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(dot + 1);
        return name.trim();
    }
}
