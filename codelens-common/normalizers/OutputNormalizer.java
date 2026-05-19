// SYNC_VERSION: 2026-05-19-v1
// 维护方:喵呜(CLI端)
// 职责：LLM输出JSON归一化，将dependencies中的method_call迁移到keyMethods.calls

package com.codelens.common.normalizers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * LLM 输出归一化层
 * 在 L1 校验前，对 LLM 输出的 JSON 做结构归一化。
 *
 * 核心规则：
 * - dependencies 中 type 包含 "method_call"/"方法调用" 的条目，提取调用信息追加到对应 keyMethods[].calls 数组
 * - 按 dependencies[].line 与 keyMethods[].line 的行号范围匹配归属
 * - 提取后从 dependencies 中移除该条目
 * - 幂等：多次执行结果一致
 */
public class OutputNormalizer {

    private static final Gson GSON = new GsonBuilder().create();

    private OutputNormalizer() {}

    /**
     * 归一化 LLM 输出的 JSON 字符串
     *
     * @param rawJson LLM 返回的原始 JSON 字符串
     * @return 归一化后的 JSON 字符串
     */
    public static String normalize(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return rawJson;
        }

        try {
            JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();

            JsonArray dependencies = root.has("dependencies") ? root.getAsJsonArray("dependencies") : null;
            JsonArray keyMethods = root.has("keyMethods") ? root.getAsJsonArray("keyMethods") : null;

            // 没有 dependencies 或 keyMethods 时无需处理
            if (dependencies == null || dependencies.size() == 0) {
                return rawJson;
            }

            // 构建 keyMethod 行号范围: [startLine, endLine)
            // 按行号从小到大排序，相邻 method 之间形成范围
            java.util.List<KeyMethodRange> ranges = buildKeyMethodRanges(keyMethods);

            // 筛选需要迁移的 method_call 条目
            JsonArray remainingDeps = new JsonArray();
            for (int i = 0; i < dependencies.size(); i++) {
                JsonElement depElem = dependencies.get(i);
                if (!depElem.isJsonObject()) {
                    remainingDeps.add(depElem);
                    continue;
                }
                JsonObject dep = depElem.getAsJsonObject();

                if (isMethodCall(dep)) {
                    // 尝试匹配 keyMethod 行号范围
                    KeyMethodRange matched = findMatchingRange(dep, ranges);
                    if (matched != null) {
                        // 追加到对应 keyMethod 的 calls 数组
                        appendCall(matched.methodObj, dep);
                        // 不加入 remainingDeps（已迁移）
                    } else {
                        // 无匹配范围，在 dependencies 中保留
                        remainingDeps.add(dep);
                    }
                } else {
                    // 非 method_call 条目保留
                    remainingDeps.add(dep);
                }
            }

            // 更新 dependencies 数组
            root.add("dependencies", remainingDeps);

            return GSON.toJson(root);

        } catch (Exception e) {
            // 解析失败时返回原始 JSON，不破坏下游流程
            return rawJson;
        }
    }

    /**
     * 判断依赖项是否为方法调用类型
     */
    private static boolean isMethodCall(JsonObject dep) {
        if (!dep.has("type") || dep.get("type").isJsonNull()) return false;
        String type = dep.get("type").getAsString();
        return type.contains("method_call") || type.contains("方法调用");
    }

    /**
     * keyMethod 行号范围
     */
    private static class KeyMethodRange {
        final JsonObject methodObj;
        final int startLine;  // 包含
        final int endLine;    // 不包含

        KeyMethodRange(JsonObject methodObj, int startLine, int endLine) {
            this.methodObj = methodObj;
            this.startLine = startLine;
            this.endLine = endLine;
        }

        boolean contains(int line) {
            return line >= startLine && line < endLine;
        }
    }

    /**
     * 构建 keyMethod 行号范围列表，按 startLine 升序排列
     */
    private static java.util.List<KeyMethodRange> buildKeyMethodRanges(JsonArray keyMethods) {
        java.util.List<KeyMethodRange> ranges = new java.util.ArrayList<>();
        if (keyMethods == null || keyMethods.size() == 0) return ranges;

        // 提取行号并排序
        java.util.List<LineAndObj> sorted = new java.util.ArrayList<>();
        for (int i = 0; i < keyMethods.size(); i++) {
            JsonElement elem = keyMethods.get(i);
            if (!elem.isJsonObject()) continue;
            JsonObject obj = elem.getAsJsonObject();
            if (!obj.has("line") || obj.get("line").isJsonNull()) continue;
            try {
                int line = Integer.parseInt(obj.get("line").getAsString().trim());
                sorted.add(new LineAndObj(line, obj));
            } catch (NumberFormatException e) {
                // 跳过无效行号
            }
        }

        // 按行号排序
        java.util.Collections.sort(sorted, (a, b) -> Integer.compare(a.line, b.line));

        for (int i = 0; i < sorted.size(); i++) {
            LineAndObj curr = sorted.get(i);
            int endLine = (i + 1 < sorted.size()) ? sorted.get(i + 1).line : Integer.MAX_VALUE;
            ranges.add(new KeyMethodRange(curr.obj, curr.line, endLine));
        }

        return ranges;
    }

    private static class LineAndObj {
        final int line;
        final JsonObject obj;

        LineAndObj(int line, JsonObject obj) {
            this.line = line;
            this.obj = obj;
        }
    }

    /**
     * 查找 dependency 行号所属的 keyMethod 范围
     */
    private static KeyMethodRange findMatchingRange(JsonObject dep, java.util.List<KeyMethodRange> ranges) {
        if (!dep.has("line") || dep.get("line").isJsonNull()) return null;
        int depLine;
        try {
            depLine = Integer.parseInt(dep.get("line").getAsString().trim());
        } catch (NumberFormatException e) {
            return null;
        }

        for (KeyMethodRange range : ranges) {
            if (range.contains(depLine)) {
                return range;
            }
        }
        return null;
    }

    /**
     * 将 method_call 调用信息追加到 keyMethod 的 calls 数组
     * 幂等：如果调用信息已存在则不重复添加
     */
    private static void appendCall(JsonObject methodObj, JsonObject callDep) {
        // 构建调用信息对象（只取关键字段）
        JsonObject callInfo = new JsonObject();
        copyField(callDep, callInfo, "name");
        copyField(callDep, callInfo, "type");
        copyField(callDep, callInfo, "line");
        copyField(callDep, callInfo, "description");

        // 获取或创建 calls 数组
        JsonArray calls;
        if (methodObj.has("calls") && methodObj.get("calls").isJsonArray()) {
            calls = methodObj.getAsJsonArray("calls");
            // 幂等：检查是否已存在相同条目
            for (JsonElement existing : calls) {
                if (existing.isJsonObject() && callsEqual(existing.getAsJsonObject(), callInfo)) {
                    return; // 已存在，不重复添加
                }
            }
        } else {
            calls = new JsonArray();
            methodObj.add("calls", calls);
        }

        calls.add(callInfo);
    }

    /**
     * 复制字段
     */
    private static void copyField(JsonObject from, JsonObject to, String fieldName) {
        if (from.has(fieldName) && !from.get(fieldName).isJsonNull()) {
            to.add(fieldName, from.get(fieldName));
        }
    }

    /**
     * 判断两个调用信息是否相等（name + line 相同即视为相等）
     */
    private static boolean callsEqual(JsonObject a, JsonObject b) {
        String aName = a.has("name") ? a.get("name").getAsString() : "";
        String bName = b.has("name") ? b.get("name").getAsString() : "";
        String aLine = a.has("line") ? a.get("line").getAsString() : "";
        String bLine = b.has("line") ? b.get("line").getAsString() : "";
        return aName.equals(bName) && aLine.equals(bLine);
    }
}
