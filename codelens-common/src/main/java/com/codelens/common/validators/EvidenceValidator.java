// SYNC_SOURCE: codelens-java/src/main/java/com/codelens/EvidenceValidator.java
// SYNC_VERSION: 2026-05-16-v2
// 维护方：喵呜（CLI端），prompt/校验器相关由喵呜拍板
// 同步说明：零 IntelliJ SDK 依赖，纯文本处理，CLI 单测可覆盖

package com.codelens.common.validators;

import java.util.*;
import java.util.regex.Pattern;

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

    public enum Confidence {
        CERTAIN, HIGH, MEDIUM, LOW, UNKNOWN
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
            if (totalChecked == 0) return Confidence.UNKNOWN;
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
                case LOW:     label = "[XX] LOW";     break;
                default:      label = "[??] UNKNOWN"; break;
            }
            sb.append("校验结果: ").append(label)
              .append(" (").append(passedCount).append("/").append(totalChecked).append(" 通过)\n");
            if (!issues.isEmpty()) {
                for (ValidationIssue issue : issues) {
                    String prefix;
                    switch (issue.confidence) {
                        case LOW:     prefix = "[XX]"; break;
                        case UNKNOWN: prefix = "[??]"; break;
                        case MEDIUM:  prefix = "[!]"; break;
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

    /**
     * 多级名称匹配：判断 name 是否在 lineContent 中出现。
     * <p>
     * 匹配策略（按优先级）：
     * <ol>
     *   <li>全名 contains — name 直接出现在行中</li>
     *   <li>末2段 contains — 取最后两个 . 分段匹配（如 {@code "StringUtil.equals"}）</li>
     *   <li>末1段 word boundary — 取最后一个 . 后的部分，用 {@code \b} 包裹匹配，
     *       防止 {@code "get"} 误匹配 {@code "getCode"} 等</li>
     * </ol>
     *
     * @return true 表示匹配成功
     */
    public static boolean matchesNameInLine(String name, String lineContent) {
        // 1. 全名 contains
        if (lineContent.contains(name)) return true;

        // 2. 末2段 contains
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            int secondDot = name.lastIndexOf('.', lastDot - 1);
            String lastTwo = (secondDot >= 0) ? name.substring(secondDot + 1) : name.substring(lastDot + 1);
            if (!lastTwo.isEmpty() && lineContent.contains(lastTwo)) return true;
        }

        // 3. 末1段 word boundary
        if (lastDot >= 0) {
            String shortName = name.substring(lastDot + 1);
            if (!shortName.isEmpty()) {
                try {
                    if (Pattern.compile("\\b" + Pattern.quote(shortName) + "\\b")
                            .matcher(lineContent).find()) {
                        return true;
                    }
                } catch (Exception e) {
                    // Pattern 出错时降级为普通 contains
                    if (lineContent.contains(shortName)) return true;
                }
            }
        }

        return false;
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
                    if (matchesNameInLine(name, actualLine) || (name.endsWith("Mapper") && actualLine.contains("@Mapper"))) {
                        result.passedCount++;
                    } else {
                        // @Autowired 容错：向前查找最多 2 行，跳过注解行
                        boolean foundNearby = false;
                        for (int offset = 1; offset <= 2; offset++) {
                            int checkLine = claimedLine + offset;
                            if (checkLine > sourceLines.length) break;
                            String lineContent = sourceLines[checkLine - 1].trim();
                            if (lineContent.startsWith("@Autowired") || lineContent.startsWith("@Resource") || lineContent.startsWith("@Inject")) {
                                continue;
                            }
                            if (matchesNameInLine(name, lineContent)) {
                                foundNearby = true;
                                break;
                            }
                        }
                        if (foundNearby) {
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

    /**
     * validateRisks 重载 — 支持方法级行号校验。
     *
     * @param json         LLM 生成的 JSON 分析结果
     * @param methodRanges 方法范围列表（null 或空列表时降级为整文件校验）
     * @param totalLines   源码总行数（降级模式使用）
     * @return 校验结果
     */
    public static ValidationResult validateRisks(String json, List<MethodRange> methodRanges, int totalLines) {
        ValidationResult result = new ValidationResult();
        String arrayContent = extractJsonArray(json, "risks");
        if (arrayContent == null) return result;
        List<Map<String, String>> items = parseJsonObjects(arrayContent);

        boolean useMethodRanges = methodRanges != null && !methodRanges.isEmpty();

        for (int i = 0; i < items.size(); i++) {
            Map<String, String> item = items.get(i);
            String lineStr = item.get("line");
            if (lineStr == null) continue;
            try {
                int claimedLine = Integer.parseInt(lineStr.trim());
                result.totalChecked++;

                if (useMethodRanges) {
                    // 方法级校验：line 必须落在某个方法范围内
                    boolean inAnyMethod = false;
                    for (MethodRange range : methodRanges) {
                        if (range.contains(claimedLine)) {
                            inAnyMethod = true;
                            break;
                        }
                    }
                    if (inAnyMethod) {
                        result.passedCount++;
                    } else {
                        addIssue(result, "risks", i, claimedLine, "line", lineStr, null,
                                "行号不在任何方法范围内", Confidence.LOW);
                    }
                } else {
                    // 降级为整文件范围校验
                    if (claimedLine >= 1 && claimedLine <= totalLines) {
                        result.passedCount++;
                    } else {
                        addIssue(result, "risks", i, claimedLine, "line", lineStr, null,
                                "行号超出源码范围", Confidence.LOW);
                    }
                }
            } catch (NumberFormatException e) {
                addIssue(result, "risks", i, 0, "line", lineStr, null,
                        "无效的行号格式", Confidence.LOW);
            }
        }
        return result;
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
        return ValidatorUtils.extractJsonArray(json, arrayName);
    }

    public static List<Map<String, String>> parseJsonObjects(String arrayContent) {
        return ValidatorUtils.parseJsonObjects(arrayContent);
    }
}
