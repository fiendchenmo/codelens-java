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
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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

    private static final String[] TOOL_CLASS_PATTERNS = {
        "StringUtil", "BeanUtil", "BigDecimalUtil", "DateUtils",
        "JSONUtil", "ListUtil", "MapUtil", "CollectionUtils",
        "Arrays", "Collections", "IBaseService", "BaseMapper",
        "ServiceImpl", "MapperImpl"
    };

    private static final Set<String> VALID_DEP_TYPES = new HashSet<>(Arrays.asList("field", "method_call"));

    private static final Set<String> VALID_RISK_TYPES = new HashSet<>(
        Arrays.asList("SECURITY", "PERFORMANCE", "MAINTAINABILITY")
    );

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

            // 归一化 dependencies（method_call 迁移 + type + name + 工具类过滤）
            if (dependencies != null && dependencies.size() > 0) {
                // 构建 keyMethod 行号范围
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
                        KeyMethodRange matched = findMatchingRange(dep, ranges);
                        if (matched != null) {
                            appendCall(matched.methodObj, dep);
                        } else {
                            remainingDeps.add(dep);
                        }
                    } else {
                        remainingDeps.add(dep);
                    }
                }

                root.add("dependencies", remainingDeps);

                // 归一化 type + name + 工具类过滤
                normalizeDepTypes(remainingDeps);
                normalizeDepNames(remainingDeps);
                filterToolClassDeps(remainingDeps);
                sortJsonArrayByLine(remainingDeps);
            }

            // 归一化 keyMethods[].calls（字符串→对象）
            if (keyMethods != null) {
                normalizeAllCalls(keyMethods);
                sortJsonArrayByLine(keyMethods);
            }

            // 归一化 risks（type 枚举校验）
            JsonArray risks = root.has("risks") ? root.getAsJsonArray("risks") : null;
            if (risks != null) {
                normalizeRiskTypes(risks);
                sortJsonArrayByLine(risks);
            }

            // 移除 architecture_issues（不在 Schema v2 中）
            if (root.has("architecture_issues")) {
                root.remove("architecture_issues");
            }

            return GSON.toJson(root);

        } catch (Exception e) {
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

    /**
     * 归一化 dependencies[].name：全限定类名/方法调用 → 字段名
     */
    static void normalizeDepNames(JsonArray deps) {
        if (deps == null || deps.size() == 0) return;
        for (int i = 0; i < deps.size(); i++) {
            JsonElement elem = deps.get(i);
            if (!elem.isJsonObject()) continue;
            JsonObject dep = elem.getAsJsonObject();
            if (!dep.has("name") || dep.get("name").isJsonNull()) continue;
            String original = dep.get("name").getAsString();
            String normalized = normalizeDepName(original);
            if (!normalized.equals(original)) {
                dep.addProperty("name", normalized);
            }
        }
    }

    /**
     * 过滤工具类 / JDK 标准库 / 框架基类 dependencies
     */
    static void filterToolClassDeps(JsonArray deps) {
        if (deps == null || deps.size() == 0) return;
        for (int i = deps.size() - 1; i >= 0; i--) {
            JsonElement elem = deps.get(i);
            if (!elem.isJsonObject()) continue;
            JsonObject dep = elem.getAsJsonObject();
            if (!dep.has("name") || dep.get("name").isJsonNull()) continue;
            String name = dep.get("name").getAsString();
            if (isToolClassDep(name)) {
                deps.remove(i);
            }
        }
    }

    /**
     * 全限定类名/方法调用 → 简化为字段名
     *
     * 示例：
     *   "com.stream.ecs.bill.service.IEcsBillMainService" → "ecsBillMainService"
     *   "IEcsBillMainService" → "ecsBillMainService"
     *   "com.stream.bill.api.IBillInfoCmd.queryBillInfoById()" → "billInfoCmd"
     *   "queryRefBillService" → "queryRefBillService"（不变）
     */
    static String normalizeDepName(String name) {
        if (name == null || name.isEmpty()) return name;

        // 1. 去掉方法调用部分（如 "xxx.queryBillInfoById()" → "xxx"），并去除方法名段
        int parenIdx = name.indexOf('(');
        if (parenIdx >= 0) {
            name = name.substring(0, parenIdx);
            int lastDot = name.lastIndexOf('.');
            if (lastDot >= 0) {
                name = name.substring(0, lastDot);
            }
        }

        // 2. 取最后一段（全限定类名 → 短名）
        if (name.contains(".")) {
            name = name.substring(name.lastIndexOf('.') + 1);
        }

        // 3. 接口名去 "I" 前缀
        if (name.length() >= 2 && name.charAt(0) == 'I' && Character.isUpperCase(name.charAt(1))) {
            name = name.substring(1);
        }

        // 4. 首字母小写
        if (name.length() >= 1) {
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }

        return name;
    }

    /**
     * 判断是否为工具类/JDK标准库/框架基类依赖
     */
    static boolean isToolClassDep(String name) {
        if (name == null) return false;
        for (String pattern : TOOL_CLASS_PATTERNS) {
            if (name.contains(pattern)) return true;
        }
        // 工具包全限定名
        if (name.startsWith("com.stream.core.util.")) return true;
        if (name.startsWith("org.apache.commons.")) return true;
        return false;
    }

    /**
     * 归一化 dependencies[].type：非标值 → "field" / "method_call"
     */
    static void normalizeDepTypes(JsonArray deps) {
        if (deps == null) return;
        for (int i = 0; i < deps.size(); i++) {
            JsonElement elem = deps.get(i);
            if (!elem.isJsonObject()) continue;
            JsonObject dep = elem.getAsJsonObject();
            if (!dep.has("type") || dep.get("type").isJsonNull()) {
                dep.addProperty("type", "field");
                continue;
            }
            String type = dep.get("type").getAsString();
            String normalized = normalizeDepType(type);
            if (!normalized.equals(type)) {
                dep.addProperty("type", normalized);
            }
        }
    }

    /**
     * dependencies[].type 枚举值归一化
     * 已知映射：injection/dependency/service/autowired → field
     *          cross_file/internal/external/same_file → 默认为 field
     *          field/method_call → 不变
     */
    static String normalizeDepType(String type) {
        if (type == null || !VALID_DEP_TYPES.contains(type)) {
            return "field";
        }
        return type;
    }

    /**
     * 归一化 risks[].type：非标值 → "MAINTAINABILITY"
     */
    static void normalizeRiskTypes(JsonArray risks) {
        if (risks == null) return;
        for (int i = 0; i < risks.size(); i++) {
            JsonElement elem = risks.get(i);
            if (!elem.isJsonObject()) continue;
            JsonObject risk = elem.getAsJsonObject();
            if (!risk.has("type") || risk.get("type").isJsonNull()) {
                risk.addProperty("type", "MAINTAINABILITY");
                continue;
            }
            String type = risk.get("type").getAsString();
            String normalized = normalizeRiskType(type);
            if (!normalized.equals(type)) {
                risk.addProperty("type", normalized);
            }
        }
    }

    /**
     * risks[].type 枚举值归一化
     * 已知映射：非 SECURITY/PERFORMANCE/MAINTAINABILITY 的值统一降级为 MAINTAINABILITY
     */
    static String normalizeRiskType(String type) {
        if (type == null || !VALID_RISK_TYPES.contains(type)) {
            return "MAINTAINABILITY";
        }
        return type;
    }

    /**
     * 遍历所有 keyMethod，归一化其 calls 数组
     */
    static void normalizeAllCalls(JsonArray keyMethods) {
        if (keyMethods == null) return;
        for (int i = 0; i < keyMethods.size(); i++) {
            JsonElement elem = keyMethods.get(i);
            if (!elem.isJsonObject()) continue;
            JsonObject method = elem.getAsJsonObject();
            if (!method.has("calls") || !method.get("calls").isJsonArray()) continue;
            JsonArray calls = method.getAsJsonArray("calls");
            normalizeCalls(calls);
        }
    }

    /**
     * 将 keyMethods[].calls 中的字符串元素归一化为对象
     *
     * 输入：["selectById()", "mergeBillMain()"]
     * 输出：[{"method":"selectById","line":-1,"type":"unknown"}, ...]
     */
    static void normalizeCalls(JsonArray calls) {
        if (calls == null || calls.size() == 0) return;
        for (int i = 0; i < calls.size(); i++) {
            JsonElement elem = calls.get(i);
            if (elem.isJsonPrimitive()) {
                String callStr = elem.getAsString();
                // 去掉括号及参数
                int parenIdx = callStr.indexOf('(');
                if (parenIdx >= 0) {
                    callStr = callStr.substring(0, parenIdx);
                }
                JsonObject obj = new JsonObject();
                obj.addProperty("method", callStr);
                obj.addProperty("line", -1);
                obj.addProperty("type", "unknown");
                calls.set(i, obj);
            } else if (elem.isJsonObject()) {
                JsonObject obj = elem.getAsJsonObject();
                // 确保 method 不含括号
                if (obj.has("method") && !obj.get("method").isJsonNull()) {
                    String m = obj.get("method").getAsString();
                    int parenIdx = m.indexOf('(');
                    if (parenIdx >= 0) {
                        obj.addProperty("method", m.substring(0, parenIdx));
                    }
                }
                // 确保 line 字段存在
                if (!obj.has("line") || obj.get("line").isJsonNull()) {
                    obj.addProperty("line", -1);
                }
                // 确保 type 字段存在
                if (!obj.has("type") || obj.get("type").isJsonNull()) {
                    obj.addProperty("type", "unknown");
                }
            }
        }
    }

    /**
     * 按 line 字段升序排序 JsonArray（原地修改）
     */
    private static void sortJsonArrayByLine(JsonArray arr) {
        if (arr == null || arr.size() <= 1) return;
        java.util.List<JsonElement> list = new java.util.ArrayList<>();
        for (JsonElement e : arr) list.add(e);
        java.util.Collections.sort(list, (a, b) -> {
            int lineA = extractLine(a);
            int lineB = extractLine(b);
            return Integer.compare(lineA, lineB);
        });
        for (int i = arr.size() - 1; i >= 0; i--) {
            arr.remove(i);
        }
        for (JsonElement e : list) arr.add(e);
    }

    private static int extractLine(JsonElement elem) {
        if (!elem.isJsonObject()) return Integer.MAX_VALUE;
        JsonObject obj = elem.getAsJsonObject();
        if (!obj.has("line") || obj.get("line").isJsonNull()) return Integer.MAX_VALUE;
        try {
            return Integer.parseInt(obj.get("line").getAsString().trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
