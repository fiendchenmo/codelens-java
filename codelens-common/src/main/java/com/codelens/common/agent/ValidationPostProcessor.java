package com.codelens.common.agent;

import com.codelens.common.validators.ConfidenceAnnotator;
import com.codelens.common.validators.EvidenceValidator;
import com.codelens.common.validators.EvidenceValidator.Confidence;
import com.codelens.common.validators.EvidenceValidator.ValidationResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashSet;
import java.util.Set;

// SYNC_VERSION: 2026-06-03-v0.6.7
// IMPACT: LOGIC_CHANGE
/**
 * METHOD_ANALYSIS 输出后处理器。
 * <p>
 * 对单个 METHOD_ANALYSIS Agent 的 LLM 输出运行源码证据校验（L1）和置信度重标注（L2），
 * 用校验后的结果覆盖 {@code l2Confidence} 字段。
 * 不修改 {@code l1Evidence}（保留 LLM 原始声称值，仅作展示用）。
 * </p>
 *
 * <p>校验失败或异常时返回原始 LLM 输出，不中断主流程。</p>
 */
public class ValidationPostProcessor {

    private ValidationPostProcessor() {
        // utility class
    }

    /**
     * 对 METHOD_ANALYSIS 输出做源码证据校验 + 置信度重标注。
     *
     * @param methodJson METHOD_ANALYSIS 的 LLM 输出 JSON 字符串
     * @param sourceCode 所属文件完整源码（即 cacheInput，可为 null）
     * @return 注入了校验后 L1/L2 的 method JSON；异常时返回原始 methodJson
     */
    public static String process(String methodJson, String sourceCode) {
        if (sourceCode == null) {
            return methodJson;
        }

        try {
            JsonObject methodObj = JsonParser.parseString(methodJson).getAsJsonObject();
            JsonObject l1Evidence = methodObj.getAsJsonObject("l1Evidence");
            JsonObject l2Confidence = methodObj.getAsJsonObject("l2Confidence");

            if (l1Evidence == null || l2Confidence == null) {
                return methodJson;
            }

            String[] sourceLines = sourceCode.split("\n");
            String methodName = methodObj.has("method") ? methodObj.get("method").getAsString() : "";
            int methodStartLine = findMethodStartLine(methodName, sourceLines);
            JsonArray methodRisks = methodObj.getAsJsonArray("risks");

            // 1. 包装为 EvidenceValidator 期望的格式
            JsonObject wrapped = buildWrappedJson(l1Evidence, l2Confidence, sourceLines);
            String wrappedStr = wrapped.toString();

            // 2. L1 证据校验
            ValidationResult vr = EvidenceValidator.validate(wrappedStr, sourceCode, sourceLines);

            // 3. L2 置信度重标注（调用以对齐逻辑，但主要用 VR 的结果）
            ConfidenceAnnotator.annotate(wrappedStr, vr, sourceLines);

            // 4. 用校验结果覆盖 l2Confidence
            Confidence overall = vr.overallConfidence();
            l2Confidence.addProperty("overallScore", toScore(overall));
            l2Confidence.addProperty("reasoningBasis", toBasis(overall));
            l2Confidence.add("riskIndicators", buildEnrichedRisks(l2Confidence, vr));

            // 5. 回写 l1Evidence.calls[].status — 标记校验通过的 call
            markCallStatusFromValidation(l1Evidence, vr);

            // 6. methodRisks 偏移行号 → 文件绝对行号转换（原地修改 methodObj.risks）
            if (methodRisks != null && methodStartLine > 0) {
                for (JsonElement riskEl : methodRisks) {
                    if (riskEl.isJsonObject()) {
                        JsonObject riskObj = riskEl.getAsJsonObject();
                        if (riskObj.has("line")) {
                            int offsetLine = riskObj.get("line").getAsInt();
                            if (offsetLine > 0) {
                                riskObj.addProperty("line", methodStartLine + offsetLine - 1);
                            }
                        }
                    }
                }
            }

            return methodObj.toString();

        } catch (Exception e) {
            // 校验失败不影响主流程，保留原始 LLM 输出
            return methodJson;
        }
    }

    /**
     * 将 method 级别的 l1Evidence 包装为 EvidenceValidator 期望的完整 JSON 格式。
     * <ul>
     *   <li>{@code l1Evidence.calls} + {@code l1Evidence.fieldsUsed} → {@code dependencies} 数组
     *       使用 LLM 原始声称行号，不自行修正</li>
     * </ul>
     */
    static JsonObject buildWrappedJson(JsonObject l1Evidence, JsonObject l2Confidence,
                                        String[] sourceLines) {
        JsonObject wrapped = new JsonObject();

        // calls + fieldsUsed → dependencies
        JsonArray deps = new JsonArray();
        addClaimsToDeps(deps, l1Evidence.getAsJsonArray("calls"), sourceLines);
        addClaimsToDeps(deps, l1Evidence.getAsJsonArray("fieldsUsed"), sourceLines);
        wrapped.add("dependencies", deps);

        return wrapped;
    }

    /**
     * 将 LLM 声明的 calls/fieldsUsed 映射到 dependencies 数组。
     * <p>
     * 混合策略：
     * <ul>
     *   <li>LLM 提供了 {@code line}（L1Call 对象格式）→ 用原始值（打破循环论证）</li>
     *   <li>LLM 未提供 line（字符串格式）→ {@code findLineInSource} 解析行号</li>
     * </ul>
     * </p>
     */
    private static void addClaimsToDeps(JsonArray deps, JsonArray claims, String[] sourceLines) {
        if (claims == null) return;
        for (int i = 0; i < claims.size(); i++) {
            JsonElement item = claims.get(i);
            String name;
            int claimedLine = 0;

            if (item.isJsonObject()) {
                JsonObject obj = item.getAsJsonObject();
                JsonElement target = obj.get("target");
                name = (target != null && target.isJsonPrimitive()) ? target.getAsString() : null;
                // LLM 提供了 line → 用原始值（打破循环论证）
                JsonElement lineEl = obj.get("line");
                if (lineEl != null && lineEl.isJsonPrimitive()) {
                    try { claimedLine = lineEl.getAsInt(); } catch (NumberFormatException e) { claimedLine = 0; }
                }
            } else if (item.isJsonPrimitive()) {
                name = item.getAsString();
            } else {
                continue;
            }
            if (name == null || name.isEmpty()) continue;

            // LLM 未声称行号 → findLineInSource 解析（验证 call 是否存在于当前文件）
            if (claimedLine == 0) {
                claimedLine = findLineInSource(name, sourceLines);
            }

            JsonObject dep = new JsonObject();
            dep.addProperty("name", name);
            dep.addProperty("line", claimedLine);
            deps.add(dep);
        }
    }

    /**
     * 构建 enriched riskIndicators：保留原始风险指示器 + 追加校验问题摘要。
     */
    private static JsonArray buildEnrichedRisks(JsonObject l2Confidence, ValidationResult vr) {
        JsonArray enriched = new JsonArray();
        JsonArray origRisks = l2Confidence.getAsJsonArray("riskIndicators");
        if (origRisks != null) {
            for (int i = 0; i < origRisks.size(); i++) {
                enriched.add(origRisks.get(i));
            }
        }
        // 追加校验问题摘要
        for (EvidenceValidator.ValidationIssue issue : vr.issues) {
            enriched.add(issue.toString());
        }
        return enriched;
    }

    /**
     * 根据 EvidenceValidator 的校验结果回写 l1Evidence.calls[].status。
     * <p>
     * 使用 LLM 原始 claimedLine 判断是否参与校验。
     * claimedLine > 0 且不在 failedDepIndices 中 → status=1（校验通过）。
     * 其他情况（无行号/校验失败）→ status=0。
     * </p>
     * <p>兼容两种输入格式：L1Call 对象格式和字符串格式。
     * 字符串格式自动转为对象格式。</p>
     */
    static void markCallStatusFromValidation(JsonObject l1Evidence, ValidationResult vr) {
        JsonArray callsArr = l1Evidence.getAsJsonArray("calls");
        if (callsArr == null || callsArr.size() == 0) return;

        // 收集 dependencies 分类下的失败索引（指向 deps 数组）
        Set<Integer> failedDepIndices = new HashSet<>();
        for (EvidenceValidator.ValidationIssue issue : vr.issues) {
            if ("dependencies".equals(issue.category) && issue.index >= 0) {
                failedDepIndices.add(issue.index);
            }
        }

        int depIdx = 0;
        for (int i = 0; i < callsArr.size(); i++) {
            JsonElement callEl = callsArr.get(i);

            if (callEl.isJsonObject()) {
                // L1Call 对象格式：用 LLM 原始 claimedLine
                JsonObject obj = callEl.getAsJsonObject();
                JsonElement targetEl = obj.get("target");
                String target = (targetEl != null && targetEl.isJsonPrimitive()) ? targetEl.getAsString() : null;
                if (target == null || target.isEmpty()) continue;

                JsonElement lineEl = obj.get("line");
                int claimedLine = 0;
                if (lineEl != null && lineEl.isJsonPrimitive()) {
                    try { claimedLine = lineEl.getAsInt(); } catch (NumberFormatException e) {}
                }
                if (claimedLine > 0 && !failedDepIndices.contains(depIdx)) {
                    obj.addProperty("status", 1);
                } else {
                    obj.addProperty("status", 0);
                }
                if (claimedLine > 0) depIdx++;
            } else if (callEl.isJsonPrimitive()) {
                // 字符串格式 → 转为对象，status=0（无 LLM 行号，无法验证）
                String target = callEl.getAsString();
                if (target == null || target.isEmpty()) continue;
                JsonObject obj = new JsonObject();
                obj.addProperty("target", target);
                obj.addProperty("line", 0);
                obj.addProperty("sourceLine", 0);
                obj.addProperty("status", 0);
                callsArr.set(i, obj);
                // 无行号不计入 depIdx
            } else {
                continue;
            }
        }
    }

    /**
     * 在源码行中搜索 name，返回 1-indexed 行号。
     * 委托给 {@link EvidenceValidator#matchesNameInLine} 做多级名称匹配。
     *
     * @param name        要搜索的字段名/方法名
     * @param sourceLines 源码行数组
     * @return 1-indexed 行号，未找到返回 0
     */
    static int findLineInSource(String name, String[] sourceLines) {
        for (int i = 0; i < sourceLines.length; i++) {
            if (EvidenceValidator.matchesNameInLine(name, sourceLines[i])) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * 在源码行中搜索 methodName 对应的方法签名，返回方法起始行号（1-indexed）。
     * <p>
     * 用于将 LLM 输出的方法体偏移行号转换为文件绝对行号。
     * 提取简单方法名（去掉类前缀和参数列表），匹配包含方法声明关键字的行。
     * </p>
     *
     * @param methodName  方法名（可含类前缀和参数）
     * @param sourceLines 源码行数组
     * @return 1-indexed 方法起始行号，未找到返回 0
     */
    private static int findMethodStartLine(String methodName, String[] sourceLines) {
        if (methodName == null || methodName.isEmpty()) return 0;
        // 提取简单方法名（去掉类前缀和参数）
        String simpleName = methodName.contains(".") ?
            methodName.substring(methodName.lastIndexOf('.') + 1) : methodName;
        // 去掉参数列表
        if (simpleName.contains("(")) {
            simpleName = simpleName.substring(0, simpleName.indexOf('('));
        }
        if (simpleName.isEmpty()) return 0;
        for (int i = 0; i < sourceLines.length; i++) {
            String line = sourceLines[i].trim();
            if (line.contains(simpleName) && (line.contains("void ") || line.contains("public ") ||
                line.contains("private ") || line.contains("protected ") || line.contains("static ") ||
                line.contains("def ") || line.contains("fun "))) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * 将 EvidenceValidator 的置信度等级映射为 0.0-1.0 分数。
     */
    static double toScore(Confidence c) {
        switch (c) {
            case CERTAIN: return 1.0;
            case HIGH:    return 0.8;
            case MEDIUM:  return 0.5;
            case LOW:     return 0.2;
            default:      return 0.0; // UNKNOWN
        }
    }

    /**
     * 将 EvidenceValidator 的置信度等级映射为 reasoningBasis 枚举值。
     */
    static String toBasis(Confidence c) {
        switch (c) {
            case CERTAIN:
            case HIGH:    return "SOLID_ANALYSIS";
            case MEDIUM:  return "HEURISTIC";
            case LOW:     return "PARTIAL";
            default:      return "UNKNOWN";
        }
    }
}
