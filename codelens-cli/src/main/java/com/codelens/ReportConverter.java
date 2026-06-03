package com.codelens;

import com.codelens.common.agent.AnalysisReport;
import com.codelens.common.agent.ExecutionTrace;
import com.codelens.common.agent.MethodReport;
import com.codelens.common.agent.L1Call;
import com.codelens.common.agent.L1Evidence;
import com.codelens.common.agent.L2Confidence;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * AnalysisReport → V3 兼容 JSON 转换器。
 * <p>
 * 将多 Agent 合并后的 AnalysisReport 转为与现有 V3 基准测试兼容的 JSON 格式。
 */
public class ReportConverter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ReportConverter() {}

    /**
     * 转换 AnalysisReport 为 V3 兼容 JSON。
     *
     * @param report  合并后的分析报告
     * @param traces  执行轨迹列表（用于 executionTrace 字段）
     * @return V3 兼容 JSON 字符串
     */
    public static String convert(AnalysisReport report, List<ExecutionTrace> traces) {
        JsonObject root = new JsonObject();

        // summary
        String summary = "Class: " + nullSafe(report.getClassName());
        if (report.getStereotype() != null && !report.getStereotype().isEmpty()) {
            summary += ", Stereotype: " + report.getStereotype();
        }
        if (report.getOverallComplexity() != null && !report.getOverallComplexity().isEmpty()) {
            summary += ", Complexity: " + report.getOverallComplexity();
        }
        root.addProperty("summary", summary);

        // framework (placeholder for V3 compatibility)
        root.addProperty("framework", "");

        // fields
        root.add("fields", new JsonArray());

        // methods
        JsonArray methodsArray = new JsonArray();
        if (report.getMethods() != null) {
            for (MethodReport mr : report.getMethods()) {
                methodsArray.add(convertMethod(mr));
            }
        }
        root.add("methods", methodsArray);

        // file-level risks
        root.add("risks", new JsonArray());

        // dependencies
        JsonArray depsArray = new JsonArray();
        if (report.getDependencies() != null) {
            for (String dep : report.getDependencies()) {
                depsArray.add(dep);
            }
        }
        root.add("dependencies", depsArray);

        // execution trace metadata
        if (traces != null && !traces.isEmpty()) {
            JsonObject traceObj = new JsonObject();
            for (ExecutionTrace t : traces) {
                JsonObject entry = new JsonObject();
                entry.addProperty("status", t.getStatus().name());
                entry.addProperty("cacheHit", t.isCacheHit());
                entry.addProperty("retryCount", t.getRetryCount());
                entry.addProperty("latencyMs", t.getLatencyMs());
                traceObj.add(t.getTaskId(), entry);
            }
            root.add("executionTrace", traceObj);
        }

        return GSON.toJson(root);
    }

    private static JsonObject convertMethod(MethodReport mr) {
        JsonObject m = new JsonObject();
        m.addProperty("name", nullSafe(mr.getMethodName()));
        m.addProperty("signature", nullSafe(mr.getSignature()));
        m.addProperty("line", mr.getLine());
        m.addProperty("complexity", "");
        m.addProperty("visibility", "");
        m.addProperty("description", "");

        // calls from L1 evidence
        JsonArray callsArray = new JsonArray();
        if (mr.getL1Evidence() != null && mr.getL1Evidence().getCalls() != null) {
            for (L1Call call : mr.getL1Evidence().getCalls()) {
                JsonObject callObj = new JsonObject();
                callObj.addProperty("target", call.getTarget());
                callObj.addProperty("line", call.getLine());
                callObj.addProperty("type", "same_file");
                callsArray.add(callObj);
            }
        }
        m.add("calls", callsArray);

        // risks (empty for now — risks come from L2 riskIndicators)
        m.add("risks", new JsonArray());

        // l1 evidence (multi-agent specific)
        if (mr.getL1Evidence() != null) {
            JsonObject l1 = new JsonObject();
            l1.add("calls", toL1CallJsonArray(mr.getL1Evidence().getCalls()));
            l1.add("calledBy", toJsonArray(mr.getL1Evidence().getCalledBy()));
            l1.add("fieldsUsed", toJsonArray(mr.getL1Evidence().getFieldsUsed()));
            m.add("l1", l1);
        }

        // l2 confidence (multi-agent specific)
        if (mr.getL2Confidence() != null) {
            JsonObject l2 = new JsonObject();
            l2.addProperty("overallScore", mr.getL2Confidence().getOverallScore());
            l2.addProperty("reasoningBasis", nullSafe(mr.getL2Confidence().getReasoningBasis()));
            l2.add("riskIndicators", toJsonArray(mr.getL2Confidence().getRiskIndicators()));
            m.add("l2", l2);
        }

        return m;
    }

    private static JsonArray toJsonArray(List<String> list) {
        JsonArray arr = new JsonArray();
        if (list != null) {
            for (String s : list) {
                arr.add(s);
            }
        }
        return arr;
    }

    private static JsonArray toL1CallJsonArray(List<L1Call> calls) {
        JsonArray arr = new JsonArray();
        if (calls != null) {
            for (L1Call call : calls) {
                JsonObject obj = new JsonObject();
                obj.addProperty("target", call.getTarget());
                obj.addProperty("line", call.getLine());
                obj.addProperty("sourceLine", call.getSourceLine());
                obj.addProperty("status", call.getStatus());
                arr.add(obj);
            }
        }
        return arr;
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}
