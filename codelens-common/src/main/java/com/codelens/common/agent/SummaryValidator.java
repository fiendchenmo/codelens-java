package com.codelens.common.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 摘要输出校验器。
 * <p>
 * 校验 LLM 输出的 JSON 摘要是否符合 Schema 要求。
 * 不抛异常，所有校验失败都包装为 {@link ValidationResult}。
 */
public class SummaryValidator {

    private static final int MAX_TOKEN_ESTIMATE = 550;

    /**
     * 校验 LLM 输出的 JSON 摘要。
     *
     * @param llmOutput LLM 返回的 JSON 字符串
     * @return 校验结果
     */
    public ValidationResult validate(String llmOutput) {
        if (llmOutput == null || llmOutput.trim().isEmpty()) {
            return ValidationResult.fail("root", "输出为空");
        }

        // 估算 token 数（按空格分词）
        int estimatedTokens = llmOutput.split("\\s+").length;
        if (estimatedTokens > MAX_TOKEN_ESTIMATE) {
            return ValidationResult.fail("root",
                    "摘要超过 token 限制: 估算 " + estimatedTokens + " token，上限 " + MAX_TOKEN_ESTIMATE);
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

        // 校验 className
        JsonElement className = json.get("className");
        if (className == null || className.isJsonNull() || !className.isJsonPrimitive()
                || className.getAsString().trim().isEmpty()) {
            return ValidationResult.fail("className", "className 字段缺失或为空");
        }

        // 校验 stereotype
        JsonElement stereotype = json.get("stereotype");
        if (stereotype == null || stereotype.isJsonNull() || !stereotype.isJsonPrimitive()
                || stereotype.getAsString().trim().isEmpty()) {
            return ValidationResult.fail("stereotype", "stereotype 字段缺失或为空");
        }

        // 校验 keyMethods
        JsonElement keyMethods = json.get("keyMethods");
        if (keyMethods == null || keyMethods.isJsonNull() || !keyMethods.isJsonArray()) {
            return ValidationResult.fail("keyMethods", "keyMethods 字段缺失或不是数组");
        }
        JsonArray keyMethodsArray = keyMethods.getAsJsonArray();
        if (keyMethodsArray.size() == 0) {
            return ValidationResult.fail("keyMethods", "keyMethods 至少需要 1 个方法");
        }

        // 校验 dependencies
        JsonElement dependencies = json.get("dependencies");
        if (dependencies == null || dependencies.isJsonNull() || !dependencies.isJsonArray()) {
            return ValidationResult.fail("dependencies", "dependencies 字段缺失或不是数组");
        }

        // 校验 complexity
        JsonElement complexity = json.get("complexity");
        if (complexity == null || complexity.isJsonNull() || !complexity.isJsonPrimitive()
                || complexity.getAsString().trim().isEmpty()) {
            return ValidationResult.fail("complexity", "complexity 字段缺失或为空");
        }

        // 校验 summary（新增 R2.2-1）
        JsonElement summary = json.get("summary");
        if (summary == null || summary.isJsonNull() || !summary.isJsonPrimitive()
                || summary.getAsString().trim().isEmpty()) {
            return ValidationResult.fail("summary", "summary 字段缺失或为空");
        }

        // 校验 frameworkDesc（新增 R2.2-1b）
        JsonElement frameworkDesc = json.get("frameworkDesc");
        if (frameworkDesc == null || frameworkDesc.isJsonNull() || !frameworkDesc.isJsonPrimitive()
                || frameworkDesc.getAsString().trim().isEmpty()) {
            return ValidationResult.fail("frameworkDesc", "frameworkDesc 字段缺失或为空");
        }

        // 校验 fields（新增 R2.2-2）
        JsonElement fields = json.get("fields");
        if (fields == null || fields.isJsonNull() || !fields.isJsonArray()) {
            return ValidationResult.fail("fields", "fields 字段缺失或不是数组");
        }

        return ValidationResult.ok();
    }
}
