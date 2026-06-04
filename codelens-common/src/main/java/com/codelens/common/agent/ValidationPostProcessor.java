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
            JsonArray methodRisks = methodObj.getAsJsonArray("risks");

            // 1. 包装为 EvidenceValidator 期望的格式
            JsonObject wrapped = buildWrappedJson(l1Evidence, l2Confidence, methodRisks, sourceLines);
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
            markCallStatusFromValidation(l1Evidence, vr, sourceLines);

            return methodObj.toString();

        } catch (Exception e) {
            // 校验失败不影响主流程，保留原始 LLM 输出
            return methodJson;
        }
    }

    /**
     * 将 method 级别的 l1Evidence / l2Confidence / methodRisks 包装为 EvidenceValidator
     * 期望的完整 JSON 格式。
     * <ul>
     *   <li>{@code l1Evidence.calls} + {@code l1Evidence.fieldsUsed} → {@code dependencies} 数组
     *       每条含 {@code name} 和源码中找到的 {@code line}（找不到时 line=0）</li>
     *   <li>{@code methodJson.risks}（LLM 原始输出，含真实行号）优先 → {@code risks} 数组；
     *       回退 {@code l2Confidence.riskIndicators}（纯字符串，虚拟 line=1）</li>
     *   <li>{@code keyMethods} = 空数组（方法级无需校验本方法的行号）</li>
     * </ul>
     */
    static JsonObject buildWrappedJson(JsonObject l1Evidence, JsonObject l2Confidence,
                                        JsonArray methodRisks, String[] sourceLines) {
        JsonObject wrapped = new JsonObject();

        // calls + fieldsUsed → dependencies
        JsonArray deps = new JsonArray();
        addClaimsToDeps(deps, l1Evidence.getAsJsonArray("calls"), sourceLines);
        addClaimsToDeps(deps, l1Evidence.getAsJsonArray("fieldsUsed"), sourceLines);
        wrapped.add("dependencies", deps);

        // 优先使用 methodJson.risks（LLM 原始输出，含真实行号）
        JsonArray risks = new JsonArray();
        if (methodRisks != null && methodRisks.size() > 0) {
            for (int i = 0; i < methodRisks.size(); i++) {
                JsonElement riskEl = methodRisks.get(i);
                if (riskEl.isJsonObject()) {
                    risks.add(riskEl.getAsJsonObject());
                }
            }
        }
        // 回退：l2Confidence.riskIndicators（纯字符串，虚拟行号）
        if (risks.size() == 0) {
            JsonArray riskIndicators = l2Confidence.getAsJsonArray("riskIndicators");
            if (riskIndicators != null) {
                for (int i = 0; i < riskIndicators.size(); i++) {
                    JsonObject risk = new JsonObject();
                    risk.addProperty("description", riskIndicators.get(i).getAsString());
                    risk.addProperty("line", 1);
                    risks.add(risk);
                }
            }
        }
        wrapped.add("risks", risks);

        // 空 keyMethods
        wrapped.add("keyMethods", new JsonArray());

        return wrapped;
    }

    /**
     * 将 LLM 声明的 calls/fieldsUsed 映射到 dependencies 数组。
     * 每项在源码中查找真实行号，找不到时 line=0（触发 EvidenceValidator 的越界检测）。
     * <p>
     * 兼容两种输入格式：
     * <ul>
     *   <li>JsonObject（calls 新格式）：取 {@code target} 字段作为方法名</li>
     *   <li>JsonPrimitive（fieldsUsed / 旧版 calls）：直接取字符串</li>
     * </ul>
     * </p>
     */
    private static void addClaimsToDeps(JsonArray deps, JsonArray claims, String[] sourceLines) {
        if (claims == null) return;
        for (int i = 0; i < claims.size(); i++) {
            JsonElement item = claims.get(i);
            String name;
            if (item.isJsonObject()) {
                // L1Call 对象格式：取 target 字段
                JsonObject obj = item.getAsJsonObject();
                JsonElement target = obj.get("target");
                name = (target != null && target.isJsonPrimitive()) ? target.getAsString() : null;
            } else if (item.isJsonPrimitive()) {
                // 字符串格式：直接使用
                name = item.getAsString();
            } else {
                continue;
            }
            if (name == null || name.isEmpty()) continue;

            int line = findLineInSource(name, sourceLines);
            if (line == 0) {
                // 跨文件引用或名称不匹配，当前文件无法验证 → 跳过
                // 不计入 totalChecked，不产生 L0 行号越界风险
                continue;
            }
            JsonObject dep = new JsonObject();
            dep.addProperty("name", name);
            dep.addProperty("line", line);
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
     * vr.issues 只记录失败项，不在 issues 中的 call 即为校验通过 → status=1。
     * buildWrappedJson 中 calls 排在 fieldsUsed 前面拼接成 dependencies 数组，
     * 但跨文件调用（line==0）已被跳过不加入 deps，因此需要跟踪实际的 deps 索引。
     * </p>
     * <p>兼容两种输入格式：L1Call 对象格式和字符串格式。
     * 字符串格式自动转为对象格式并写入 status/line/sourceLine。</p>
     */
    static void markCallStatusFromValidation(JsonObject l1Evidence, ValidationResult vr,
                                              String[] sourceLines) {
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
            String target;
            int line;

            if (callEl.isJsonObject()) {
                // L1Call 对象格式
                JsonObject obj = callEl.getAsJsonObject();
                JsonElement targetEl = obj.get("target");
                target = (targetEl != null && targetEl.isJsonPrimitive()) ? targetEl.getAsString() : null;
                if (target == null || target.isEmpty()) continue;
                line = findLineInSource(target, sourceLines);
                if (line == 0) continue;
                obj.addProperty("status", failedDepIndices.contains(depIdx) ? 0 : 1);
            } else if (callEl.isJsonPrimitive()) {
                // 字符串格式 → 转为对象格式，写入 status/line/sourceLine
                target = callEl.getAsString();
                if (target == null || target.isEmpty()) continue;
                line = findLineInSource(target, sourceLines);
                if (line == 0) continue;  // 跨文件调用，不加入 deps
                JsonObject obj = new JsonObject();
                obj.addProperty("target", target);
                obj.addProperty("line", line);
                obj.addProperty("sourceLine", line);
                obj.addProperty("status", failedDepIndices.contains(depIdx) ? 0 : 1);
                callsArr.set(i, obj);  // 替换字符串为对象
            } else {
                continue;
            }
            depIdx++;
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
