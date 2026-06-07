package com.codelens.common.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 方法分析输出校验器。
 * <p>
 * 校验 LLM 输出的方法分析 JSON 是否符合 L1/L2 Schema 要求。
 */
public class MethodAnalysisValidator {

    private static final Set<String> VALID_REASONING_BASIS = new HashSet<>(
            Arrays.asList("SOLID_ANALYSIS", "HEURISTIC", "PARTIAL", "UNKNOWN"));

    /**
     * 校验 LLM 输出的方法分析 JSON。
     *
     * @param llmOutput       LLM 返回的 JSON 字符串
     * @param expectedMethod  预期的方法名（用于校验输出 method 字段是否匹配）
     * @return 校验结果
     */
    public ValidationResult validate(String llmOutput, String expectedMethod) {
        if (llmOutput == null || llmOutput.trim().isEmpty()) {
            return ValidationResult.fail("root", "输出为空");
        }

        // 解析 JSON
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(llmOutput);
            if (parsed == null || !parsed.isJsonObject()) {
                return ValidationResult.fail("root", "输出不是有效的 JSON 对象");
            }
            json = parsed.getAsJsonObject();
        } catch (Exception e) {
            return ValidationResult.fail("root", "JSON 解析失败: " + e.getMessage());
        }

        // 校验 method 字段匹配
        JsonElement methodEl = json.get("method");
        if (methodEl == null || methodEl.isJsonNull() || !methodEl.isJsonPrimitive()) {
            return ValidationResult.fail("method", "method 字段缺失");
        }
        String actualMethod = methodEl.getAsString();
        if (expectedMethod != null && !expectedMethod.isEmpty() && !expectedMethod.equals(actualMethod)) {
            return ValidationResult.fail("method", "方法名不匹配: 期望 " + expectedMethod + "，实际 " + actualMethod);
        }

        // 可选校验 description（新增 R2.2-3）
        JsonElement descEl = json.get("description");
        if (descEl != null && !descEl.isJsonNull() && descEl.isJsonPrimitive()
                && descEl.getAsString().trim().isEmpty()) {
            return ValidationResult.fail("description", "description 字段为空");
        }

        // 校验 L1 证据
        JsonElement l1El = json.get("l1Evidence");
        if (l1El == null || l1El.isJsonNull() || !l1El.isJsonObject()) {
            return ValidationResult.fail("l1Evidence", "l1Evidence 字段缺失或不是对象");
        }
        JsonObject l1 = l1El.getAsJsonObject();

        boolean hasL1Content = false;
        String[] l1Fields = {"calls", "calledBy", "fieldsUsed"};
        for (String field : l1Fields) {
            JsonElement el = l1.get(field);
            if (el != null && !el.isJsonNull() && el.isJsonArray() && el.getAsJsonArray().size() > 0) {
                hasL1Content = true;
                break;
            }
        }
        if (!hasL1Content) {
            return ValidationResult.fail("l1Evidence", "L1 证据中 calls/calledBy/fieldsUsed 至少需一个非空");
        }

        // 校验 L2 置信度
        JsonElement l2El = json.get("l2Confidence");
        if (l2El == null || l2El.isJsonNull() || !l2El.isJsonObject()) {
            return ValidationResult.fail("l2Confidence", "l2Confidence 字段缺失或不是对象");
        }
        JsonObject l2 = l2El.getAsJsonObject();

        // overallScore ∈ [0, 1]
        JsonElement scoreEl = l2.get("overallScore");
        if (scoreEl == null || scoreEl.isJsonNull() || !scoreEl.isJsonPrimitive()) {
            return ValidationResult.fail("overallScore", "overallScore 字段缺失");
        }
        try {
            double score = scoreEl.getAsDouble();
            if (score < 0.0 || score > 1.0) {
                return ValidationResult.fail("overallScore",
                        "overallScore 超出范围 [0, 1]: " + score);
            }
        } catch (NumberFormatException e) {
            return ValidationResult.fail("overallScore", "overallScore 不是有效数字");
        }

        // reasoningBasis 枚举值
        JsonElement basisEl = l2.get("reasoningBasis");
        if (basisEl == null || basisEl.isJsonNull() || !basisEl.isJsonPrimitive()) {
            return ValidationResult.fail("reasoningBasis", "reasoningBasis 字段缺失");
        }
        if (!VALID_REASONING_BASIS.contains(basisEl.getAsString())) {
            return ValidationResult.fail("reasoningBasis",
                    "无效 reasoningBasis: " + basisEl.getAsString() + "，有效值: " + VALID_REASONING_BASIS);
        }

        // riskIndicators 是数组（可为空）
        JsonElement riskEl = l2.get("riskIndicators");
        if (riskEl == null || riskEl.isJsonNull() || !riskEl.isJsonArray()) {
            return ValidationResult.fail("riskIndicators", "riskIndicators 字段缺失或不是数组");
        }

        return ValidationResult.ok();
    }
}
