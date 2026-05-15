package com.codelens;

import java.util.*;

/**
 * L2 置信度标注器
 * 基于 L1 证据校验结果，为 LLM 分析的每条结果打上置信度标签。
 *
 * 置信度等级：
 * - CERTAIN：L1 校验通过 + 低风险/无风险
 * - HIGH：L1 校验通过 + 中风险，或行号精确匹配
 * - MEDIUM：L1 校验通过但行号偏移 >1，或 L1 未覆盖的条目
 * - LOW：L1 校验失败（行号超出/名称不匹配）
 */
public class ConfidenceAnnotator {

    public static class AnnotatedItem {
        public String category;
        public int index;
        public Map<String, String> fields;
        public EvidenceValidator.Confidence confidence;
        public String reason;

        @Override
        public String toString() {
            return category + "[" + index + "]: " + confidence + " (" + reason + ")";
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
                sb.append(" — ").append(item.reason).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * 为 LLM JSON 结果添加置信度标注
     * @param llmJson LLM 输出的原始 JSON
     * @param validationResult L1 校验结果
     * @param sourceLines 源码行数组
     * @return 带置信度标注的结果
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

        // 标注其他无法校验的条目（summary, design_intent 等）为 MEDIUM
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

            String key = "dependencies:" + i;
            EvidenceValidator.ValidationIssue issue = issueMap.get(key);

            if (issue != null) {
                // L1 校验发现问题的条目
                if (issue.confidence == EvidenceValidator.Confidence.LOW) {
                    ai.confidence = EvidenceValidator.Confidence.LOW;
                    ai.reason = "行号超出范围或名称不匹配";
                } else {
                    ai.confidence = EvidenceValidator.Confidence.MEDIUM;
                    ai.reason = "行号偏移，名称在附近找到";
                }
            } else {
                // L1 校验通过的条目
                String lineStr = items.get(i).get("line");
                String name = items.get(i).get("name");
                if (lineStr != null && name != null && sourceLines != null) {
                    int line = Integer.parseInt(lineStr.trim());
                    // 精确匹配=行号完全对应
                    if (line >= 1 && line <= sourceLines.length && sourceLines[line - 1].contains(extractSimpleName(name))) {
                        ai.confidence = EvidenceValidator.Confidence.CERTAIN;
                        ai.reason = "行号精确匹配";
                    } else {
                        ai.confidence = EvidenceValidator.Confidence.HIGH;
                        ai.reason = "行号附近匹配";
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

            String key = "risks:" + i;
            EvidenceValidator.ValidationIssue issue = issueMap.get(key);

            String severity = items.get(i).get("severity");

            if (issue != null) {
                if (issue.confidence == EvidenceValidator.Confidence.LOW) {
                    ai.confidence = EvidenceValidator.Confidence.LOW;
                    ai.reason = "行号超出范围";
                } else {
                    ai.confidence = EvidenceValidator.Confidence.MEDIUM;
                    ai.reason = "行号偏移，风险描述存在";
                }
            } else {
                // L1 通过 — 根据 severity 加权
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

            String key = "key_methods:" + i;
            EvidenceValidator.ValidationIssue issue = issueMap.get(key);

            if (issue != null) {
                if (issue.confidence == EvidenceValidator.Confidence.LOW) {
                    ai.confidence = EvidenceValidator.Confidence.LOW;
                    ai.reason = "行号超出范围或方法名未找到";
                } else {
                    ai.confidence = EvidenceValidator.Confidence.MEDIUM;
                    ai.reason = "行号偏移，方法名在附近找到";
                }
            } else {
                String lineStr = items.get(i).get("line");
                String name = items.get(i).get("name");
                if (lineStr != null && name != null && sourceLines != null) {
                    int line = Integer.parseInt(lineStr.trim());
                    String simpleName = extractSimpleName(name);
                    if (line >= 1 && line <= sourceLines.length && sourceLines[line - 1].contains(simpleName)) {
                        ai.confidence = EvidenceValidator.Confidence.CERTAIN;
                        ai.reason = "行号精确匹配方法定义";
                    } else {
                        ai.confidence = EvidenceValidator.Confidence.HIGH;
                        ai.reason = "行号附近匹配方法定义";
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
        // summary, design_intent, class_analysis, architecture_issues 无法通过 L1 校验
        // 标注为 MEDIUM（需人工复核）
        String[] textFields = {"summary", "design_intent", "class_analysis"};
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
                result.items.add(ai);
            }
        }
    }

    /**
     * 在 LLM JSON 结果中注入 confidence 字段和 validation 摘要
     * 返回修改后的 JSON 字符串
     */
    public static String injectConfidenceIntoJson(String llmJson, AnnotatedResult annotatedResult,
                                                   EvidenceValidator.ValidationResult validationResult) {
        // 为每个数组条目注入 confidence 字段
        String result = llmJson;

        result = injectConfidenceToArray(result, "dependencies", annotatedResult);
        result = injectConfidenceToArray(result, "risks", annotatedResult);
        result = injectConfidenceToArray(result, "key_methods", annotatedResult);

        // 在 JSON 顶层添加 validation 摘要
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
        // 找到对应 category 的标注项
        List<AnnotatedItem> items = new ArrayList<>();
        for (AnnotatedItem ai : result.items) {
            if (ai.category.equals(arrayName)) {
                items.add(ai);
            }
        }
        if (items.isEmpty()) return json;

        // 简单策略：在 JSON 数组中每个 } 后插入 confidence 字段
        // 找到数组内容
        String arrayContent = EvidenceValidator.extractJsonArray(json, arrayName);
        if (arrayContent == null) return json;

        String modified = arrayContent;
        for (int i = items.size() - 1; i >= 0; i--) {
            AnnotatedItem ai = items.get(i);
            // 找到第 i 个对象，在最后一个 } 前插入
            String confidenceField = ", \"confidence\": \"" + ai.confidence.name() + "\"";
            // 从后向前找第 i+1 个 }
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

        // 替换原 JSON 中的数组
        int arrayStart = json.indexOf(arrayContent);
        if (arrayStart >= 0) {
            return json.substring(0, arrayStart) + modified + json.substring(arrayStart + arrayContent.length());
        }
        return json;
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
        // 读取到下一个非转义引号
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
        // 去掉参数签名
        int paren = name.indexOf('(');
        if (paren > 0) name = name.substring(0, paren);
        // 去掉包名
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(dot + 1);
        return name.trim();
    }
}
