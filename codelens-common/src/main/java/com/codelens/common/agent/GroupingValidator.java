package com.codelens.common.agent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 文件分组结果校验器。
 * <p>
 * 校验 LLM 返回的分组表 JSON 格式是否合法。
 * </p>
 */
public class GroupingValidator {

    private static final Gson GSON = new GsonBuilder().create();

    /**
     * 校验分组 JSON 格式。
     * <p>
     * 规则：每个 group 有 name 和 files 字段，至少 1 个分组。
     *
     * @param json LLM 返回的 JSON 字符串
     * @return 校验结果
     */
    public ValidationResult validate(String json) {
        if (json == null || json.trim().isEmpty()) {
            return ValidationResult.fail("json", "分组 JSON 为空");
        }

        JsonArray array;
        try {
            JsonElement element = GSON.fromJson(json, JsonElement.class);
            if (element == null || !element.isJsonArray()) {
                return ValidationResult.fail("json", "分组 JSON 必须为数组");
            }
            array = element.getAsJsonArray();
        } catch (Exception e) {
            return ValidationResult.fail("json", "分组 JSON 解析失败: " + e.getMessage());
        }

        if (array.size() == 0) {
            return ValidationResult.fail("groups", "至少需要 1 个分组");
        }

        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (item == null || !item.isJsonObject()) {
                return ValidationResult.fail("groups[" + i + "]", "分组项必须为 JSON 对象");
            }
            JsonObject group = item.getAsJsonObject();

            // 检查 group/name 字段
            JsonElement nameEl = group.get("group");
            if (nameEl == null) {
                nameEl = group.get("name");
            }
            if (nameEl == null || !nameEl.isJsonPrimitive()
                    || nameEl.getAsString().trim().isEmpty()) {
                return ValidationResult.fail("groups[" + i + "].name",
                        "分组名称不能为空");
            }

            // 检查 files 字段
            JsonElement filesEl = group.get("files");
            if (filesEl == null || !filesEl.isJsonArray()) {
                return ValidationResult.fail("groups[" + i + "].files",
                        "分组文件列表必须为数组");
            }
            JsonArray files = filesEl.getAsJsonArray();
            if (files.size() == 0) {
                return ValidationResult.fail("groups[" + i + "].files",
                        "分组至少包含 1 个文件");
            }
        }

        return ValidationResult.ok();
    }
}
