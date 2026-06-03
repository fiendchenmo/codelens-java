package com.codelens.common.agent;

import com.codelens.common.validators.ConfidenceAnnotator;
import com.codelens.common.validators.EvidenceValidator;
import com.codelens.common.validators.EvidenceValidator.Confidence;
import com.codelens.common.validators.EvidenceValidator.ValidationResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

            return methodObj.toString();

        } catch (Exception e) {
            // 校验失败不影响主流程，保留原始 LLM 输出
            return methodJson;
        }
    }

    /**
     * 将 method 级别的 l1Evidence / l2Confidence 包装为 EvidenceValidator 期望的完整 JSON 格式。
     * <ul>
     *   <li>{@code l1Evidence.calls} + {@code l1Evidence.fieldsUsed} → {@code dependencies} 数组
     *       每条含 {@code name} 和源码中找到的 {@code line}（找不到时 line=0）</li>
     *   <li>{@code l2Confidence.riskIndicators} → {@code risks} 数组（虚拟 line=1，保证通过范围检查）</li>
     *   <li>{@code keyMethods} = 空数组（方法级无需校验本方法的行号）</li>
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

        // riskIndicators → risks（虚拟 line=1 保证通过范围检查）
        JsonArray risks = new JsonArray();
        JsonArray riskIndicators = l2Confidence.getAsJsonArray("riskIndicators");
        if (riskIndicators != null) {
            for (int i = 0; i < riskIndicators.size(); i++) {
                JsonObject risk = new JsonObject();
                risk.addProperty("description", riskIndicators.get(i).getAsString());
                risk.addProperty("line", 1);
                risks.add(risk);
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
     * 在源码行中搜索 name，返回 1-indexed 行号。
     * 使用 {@code String.contains()} 做模糊匹配。
     *
     * @param name        要搜索的字段名/方法名
     * @param sourceLines 源码行数组
     * @return 1-indexed 行号，未找到返回 0
     */
    static int findLineInSource(String name, String[] sourceLines) {
        for (int i = 0; i < sourceLines.length; i++) {
            if (sourceLines[i].contains(name)) {
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
