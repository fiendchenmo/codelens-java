// SYNC_VERSION: 2026-06-26-v1
// IMPACT: LOGIC_CHANGE
// 维护方:喵呜(CLI端)
// 职责：V3 JSON 字符串 → AnalysisReport 转换，桥接 SINGLE 模式到矛盾检测等 MULTI 模式后处理组件

package com.codelens.common.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * V3 JSON → AnalysisReport 转换器。
 * <p>
 * SINGLE 模式直接产出 V3 JSON 字符串，不走 ReportMerger，不生成 AnalysisReport。
 * 本转换器将 V3 JSON 解析为 AnalysisReport，使得 SINGLE 模式可以复用
 * MULTI 模式的矛盾检测（ContradictionDetector）、矛盾注入（ContradictionInjector）
 * 等后处理组件。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * String v3Json = AnalysisService.analyzeFile(...);  // SINGLE 模式产出
 * AnalysisReport report = V3ToReportConverter.convert(v3Json, className);
 * // 之后可复用 ContradictionDetector 等组件
 * }</pre>
 *
 * <h3>字段映射</h3>
 * <table>
 *   <tr><th>V3 JSON</th><th>AnalysisReport</th></tr>
 *   <tr><td>summary</td><td>summary</td></tr>
 *   <tr><td>framework</td><td>frameworkDesc</td></tr>
 *   <tr><td>fields[]</td><td>fields (List&lt;FieldReport&gt;)</td></tr>
 *   <tr><td>methods[]</td><td>methods (List&lt;MethodReport&gt;)</td></tr>
 *   <tr><td>methods[].name</td><td>methods[].methodName</td></tr>
 *   <tr><td>methods[].calls[]</td><td>methods[].l1Evidence.calls (List&lt;L1Call&gt;)</td></tr>
 *   <tr><td>methods[].called_by[]</td><td>methods[].l1Evidence.calledBy (List&lt;String&gt;)</td></tr>
 *   <tr><td>methods[].risks[]</td><td>methods[].risks (List&lt;RiskItem&gt;)</td></tr>
 *   <tr><td>risks[]</td><td>risks (顶层跨方法风险，List&lt;RiskItem&gt;)</td></tr>
 *   <tr><td>（外部传入）</td><td>className</td></tr>
 * </table>
 *
 * <p>注意事项：</p>
 * <ul>
 *   <li>V3 JSON 不含 className、stereotype、dependencies 顶层字段，
 *       需通过构造参数传入或从 methods.calls 推导</li>
 *   <li>called_by 在 V3 中为对象数组 {@code [{"caller": "...", "line": N}]}，
 *       提取 caller 字符串存入 calledBy</li>
 *   <li>calls 中 type 字段（same_file/cross_file/static）不映射到 L1Call（L1Call 无此字段）</li>
 * </ul>
 */
public class V3ToReportConverter {

    private V3ToReportConverter() {
        // 工具类，禁止实例化
    }

    /**
     * 将 V3 JSON 字符串转换为 AnalysisReport。
     *
     * @param v3Json    V3 格式 JSON 字符串（LLM 单次调用产出，已经过 OutputNormalizer 归一化）
     * @param className 类名（V3 JSON 不含此字段）
     * @return AnalysisReport，解析失败时返回仅含 className 的空报告
     */
    public static AnalysisReport convert(String v3Json, String className) {
        AnalysisReport report = new AnalysisReport();
        report.setClassName(className);

        if (v3Json == null || v3Json.trim().isEmpty()) {
            return report;
        }

        try {
            JsonObject root = JsonParser.parseString(v3Json).getAsJsonObject();

            // ── 类级字段 ──
            report.setSummary(getStringSafe(root, "summary"));
            report.setFrameworkDesc(getStringSafe(root, "framework"));
            report.setFields(extractFields(root));

            // ── 顶层 risks（跨方法/跨字段风险） ──
            report.setRisks(extractTopLevelRisks(root));

            // ── 方法级字段 ──
            report.setMethods(extractMethods(root));

            // ── 推导字段 ──
            // dependencies: 从所有方法调用中收集跨文件调用的唯一目标
            report.setDependencies(extractDependencies(report.getMethods()));

        } catch (Exception e) {
            // 解析失败返回空报告（调用方可检查 methods 是否为空）
        }

        return report;
    }

    // ─── 方法列表解析 ────────────────────────────────

    private static List<MethodReport> extractMethods(JsonObject root) {
        List<MethodReport> methods = new ArrayList<>();
        JsonElement methodsEl = root.get("methods");
        if (methodsEl == null || !methodsEl.isJsonArray()) {
            return methods;
        }
        JsonArray methodsArr = methodsEl.getAsJsonArray();
        for (int i = 0; i < methodsArr.size(); i++) {
            JsonElement item = methodsArr.get(i);
            if (item.isJsonObject()) {
                try {
                    methods.add(parseMethod(item.getAsJsonObject()));
                } catch (Exception e) {
                    // 跳过解析失败的方法
                }
            }
        }
        return methods;
    }

    private static MethodReport parseMethod(JsonObject json) {
        MethodReport method = new MethodReport();

        // 基本信息
        method.setMethodName(getStringSafe(json, "name"));
        method.setSignature(getStringSafe(json, "signature"));
        method.setLine(getIntSafe(json, "line"));
        method.setDescription(getStringSafe(json, "description"));
        method.setLogicSummary(getStringSafe(json, "logic_summary"));
        method.setComplexity(getStringSafe(json, "complexity"));
        method.setComplexityValue(getIntSafe(json, "complexity_value"));
        method.setVisibility(getStringSafe(json, "visibility"));
        method.setAnnotations(extractStringArray(json, "annotations"));

        // 参数、返回值、异常
        method.setParams(extractParams(json));
        method.setReturnInfo(extractReturnInfo(json));
        method.setExceptions(extractExceptions(json));

        // L1 证据：从 calls[] + called_by[] 构建
        method.setL1Evidence(extractL1Evidence(json));

        // 风险项
        method.setRisks(extractRisks(json));

        return method;
    }

    // ─── L1 证据 ──────────────────────────────────────

    private static L1Evidence extractL1Evidence(JsonObject json) {
        L1Evidence l1 = new L1Evidence();

        // calls: 对象数组 → List<L1Call>
        l1.setCalls(extractCalls(json));

        // calledBy: 对象数组 → List<String>（提取 caller 字段）
        l1.setCalledBy(extractCalledBy(json));

        return l1;
    }

    /**
     * 解析 methods[].calls[] 为 List&lt;L1Call&gt;。
     * V3 格式：{@code [{"target": "method", "line": 42, "type": "same_file"}]}
     */
    private static List<L1Call> extractCalls(JsonObject json) {
        List<L1Call> result = new ArrayList<>();
        JsonElement el = json.get("calls");
        if (el == null || !el.isJsonArray()) {
            return result;
        }
        JsonArray arr = el.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement item = arr.get(i);
            if (item.isJsonObject()) {
                JsonObject callObj = item.getAsJsonObject();
                L1Call call = new L1Call();
                call.setTarget(getStringSafe(callObj, "target"));
                call.setLine(getIntSafe(callObj, "line"));
                // V3 type 字段（same_file/cross_file/static）不映射到 L1Call
                result.add(call);
            } else if (item.isJsonPrimitive()) {
                // 字符串降级格式
                result.add(new L1Call(item.getAsString()));
            }
        }
        return result;
    }

    /**
     * 解析 methods[].called_by[] 为 List&lt;String&gt;。
     * V3 格式：{@code [{"caller": "Class.method", "line": 42}]}
     * 提取 caller 字符串。
     */
    private static List<String> extractCalledBy(JsonObject json) {
        List<String> result = new ArrayList<>();
        JsonElement el = json.get("called_by");
        if (el == null || !el.isJsonArray()) {
            return result;
        }
        JsonArray arr = el.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement item = arr.get(i);
            if (item.isJsonObject()) {
                String caller = getStringSafe(item.getAsJsonObject(), "caller");
                if (caller != null && !caller.isEmpty()) {
                    result.add(caller);
                }
            } else if (item.isJsonPrimitive()) {
                // 字符串降级格式
                result.add(item.getAsString());
            }
        }
        return result;
    }

    // ─── 字段列表 ──────────────────────────────────────

    private static List<FieldReport> extractFields(JsonObject root) {
        List<FieldReport> result = new ArrayList<>();
        JsonElement el = root.get("fields");
        if (el == null || !el.isJsonArray()) {
            return result;
        }
        JsonArray arr = el.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement item = arr.get(i);
            if (item.isJsonObject()) {
                JsonObject fieldObj = item.getAsJsonObject();
                FieldReport field = new FieldReport();
                field.setName(getStringSafe(fieldObj, "name"));
                field.setType(getStringSafe(fieldObj, "type"));
                field.setInjectType(getStringSafe(fieldObj, "injectType"));
                field.setDescription(getStringSafe(fieldObj, "description"));
                field.setLine(getIntSafe(fieldObj, "line"));
                result.add(field);
            }
        }
        return result;
    }

    // ─── 风险项 ────────────────────────────────────────

    /**
     * 解析顶层 risks[] 为 List&lt;RiskItem&gt;。
     * V3 格式与 methods[].risks[] 完全相同：
     * {@code [{type, description, line, severity, impact, suggestion, confidence}]}
     * 直接复用 extractRisks() 解析逻辑。
     */
    private static List<RiskItem> extractTopLevelRisks(JsonObject root) {
        return extractRisks(root);
    }

    private static List<RiskItem> extractRisks(JsonObject json) {
        List<RiskItem> result = new ArrayList<>();
        JsonElement el = json.get("risks");
        if (el == null || !el.isJsonArray()) {
            return result;
        }
        JsonArray arr = el.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement item = arr.get(i);
            if (item.isJsonObject()) {
                JsonObject riskObj = item.getAsJsonObject();
                RiskItem ri = new RiskItem();
                ri.setType(getStringSafe(riskObj, "type"));
                ri.setDescription(getStringSafe(riskObj, "description"));
                ri.setLine(getIntSafe(riskObj, "line"));
                ri.setSeverity(getStringSafe(riskObj, "severity"));
                ri.setImpact(getStringSafe(riskObj, "impact"));
                ri.setSuggestion(getStringSafe(riskObj, "suggestion"));
                JsonElement confEl = riskObj.get("confidence");
                if (confEl != null && confEl.isJsonPrimitive()) {
                    try {
                        ri.setConfidence(confEl.getAsDouble());
                    } catch (NumberFormatException ignored) {
                        // 保留默认值 0.0
                    }
                }
                result.add(ri);
            }
        }
        return result;
    }

    // ─── 参数 ──────────────────────────────────────────

    private static List<ParamReport> extractParams(JsonObject json) {
        List<ParamReport> result = new ArrayList<>();
        JsonElement el = json.get("params");
        if (el == null || !el.isJsonArray()) {
            return result;
        }
        JsonArray arr = el.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement item = arr.get(i);
            if (item.isJsonObject()) {
                JsonObject paramObj = item.getAsJsonObject();
                ParamReport param = new ParamReport();
                param.setName(getStringSafe(paramObj, "name"));
                param.setType(getStringSafe(paramObj, "type"));
                param.setUsage(getStringSafe(paramObj, "usage"));
                param.setSample(getStringSafe(paramObj, "sample"));
                result.add(param);
            }
        }
        return result;
    }

    // ─── 返回值 ────────────────────────────────────────

    private static ReturnReport extractReturnInfo(JsonObject json) {
        JsonElement el = json.get("return");
        if (el == null || !el.isJsonObject()) {
            return null;
        }
        JsonObject retObj = el.getAsJsonObject();
        ReturnReport ret = new ReturnReport();
        ret.setType(getStringSafe(retObj, "type"));
        // V3 使用 business_meaning，ReturnReport 使用 businessMeaning
        ret.setBusinessMeaning(getStringSafe(retObj, "business_meaning"));
        return ret;
    }

    // ─── 异常 ──────────────────────────────────────────

    private static List<ExceptionReport> extractExceptions(JsonObject json) {
        List<ExceptionReport> result = new ArrayList<>();
        JsonElement el = json.get("exceptions");
        if (el == null || !el.isJsonArray()) {
            return result;
        }
        JsonArray arr = el.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement item = arr.get(i);
            if (item.isJsonObject()) {
                JsonObject exObj = item.getAsJsonObject();
                ExceptionReport ex = new ExceptionReport();
                ex.setType(getStringSafe(exObj, "type"));
                ex.setHandling(getStringSafe(exObj, "handling"));
                ex.setLine(getIntSafe(exObj, "line"));
                result.add(ex);
            }
        }
        return result;
    }

    // ─── 依赖推导 ──────────────────────────────────────

    /**
     * 从所有方法的 L1Evidence.calls 中提取跨文件调用的唯一目标列表。
     * 跨文件判定：target 包含 "." 且非 "this." 前缀。
     */
    private static List<String> extractDependencies(List<MethodReport> methods) {
        List<String> deps = new ArrayList<>();
        if (methods == null) {
            return deps;
        }
        for (MethodReport m : methods) {
            L1Evidence l1 = m.getL1Evidence();
            if (l1 == null || l1.getCalls() == null) {
                continue;
            }
            for (L1Call call : l1.getCalls()) {
                String target = call.getTarget();
                if (target == null || target.isEmpty()) {
                    continue;
                }
                // 跨文件：包含 "." 且非 "this."
                if (target.contains(".") && !target.startsWith("this.")) {
                    if (!deps.contains(target)) {
                        deps.add(target);
                    }
                }
            }
        }
        return deps;
    }

    // ─── JSON 工具方法 ─────────────────────────────────

    private static String getStringSafe(JsonObject obj, String field) {
        JsonElement el = obj.get(field);
        if (el != null && el.isJsonPrimitive()) {
            return el.getAsString();
        }
        return null;
    }

    private static int getIntSafe(JsonObject obj, String field) {
        JsonElement el = obj.get(field);
        if (el != null && el.isJsonPrimitive()) {
            try {
                return el.getAsInt();
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static List<String> extractStringArray(JsonObject obj, String field) {
        List<String> result = new ArrayList<>();
        JsonElement el = obj.get(field);
        if (el != null && el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonElement item = arr.get(i);
                if (item.isJsonPrimitive()) {
                    result.add(item.getAsString());
                } else {
                    result.add(item.toString());
                }
            }
        }
        return result;
    }
}
