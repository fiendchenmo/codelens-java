package com.codelens.common.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * 结果合并器。
 * <p>
 * 将 SUMMARY JSON 与一组 METHOD_ANALYSIS JSON 合并为 AnalysisReport。
 * 类级信息取摘要，方法级信息取方法分析，冲突时方法分析优先。
 */
public class ReportMerger {

    /**
     * 合并摘要和方法分析结果。
     *
     * @param summaryJson   SUMMARY 输出的 JSON
     * @param methodJsons   每个方法的 METHOD_ANALYSIS 输出的 JSON 列表
     * @return 合并后的 AnalysisReport
     */
    public AnalysisReport merge(String summaryJson, List<String> methodJsons) {
        JsonObject summary = JsonParser.parseString(summaryJson).getAsJsonObject();

        AnalysisReport report = new AnalysisReport();
        report.setClassName(getStringSafe(summary, "className"));
        report.setStereotype(getStringSafe(summary, "stereotype"));
        report.setOverallComplexity(getStringSafe(summary, "complexity"));
        report.setDependencies(extractStringArray(summary, "dependencies"));

        // 合并方法级信息
        List<MethodReport> methods = new ArrayList<>();
        for (String methodJson : methodJsons) {
            try {
                JsonObject methodObj = JsonParser.parseString(methodJson).getAsJsonObject();
                methods.add(parseMethodReport(methodObj));
            } catch (Exception e) {
                // 跳过解析失败的方法
            }
        }
        report.setMethods(methods);

        return report;
    }

    private MethodReport parseMethodReport(JsonObject json) {
        MethodReport method = new MethodReport();
        method.setMethodName(getStringSafe(json, "method"));
        method.setSignature(getStringSafe(json, "method"));

        // L1 证据
        JsonElement l1El = json.get("l1Evidence");
        if (l1El != null && l1El.isJsonObject()) {
            JsonObject l1Obj = l1El.getAsJsonObject();
            L1Evidence l1 = new L1Evidence();
            l1.setCalls(extractStringArray(l1Obj, "calls"));
            l1.setCalledBy(extractStringArray(l1Obj, "calledBy"));
            l1.setFieldsUsed(extractStringArray(l1Obj, "fieldsUsed"));
            method.setL1Evidence(l1);
        }

        // L2 置信度
        JsonElement l2El = json.get("l2Confidence");
        if (l2El != null && l2El.isJsonObject()) {
            JsonObject l2Obj = l2El.getAsJsonObject();
            L2Confidence l2 = new L2Confidence();
            JsonElement scoreEl = l2Obj.get("overallScore");
            if (scoreEl != null && scoreEl.isJsonPrimitive()) {
                l2.setOverallScore(scoreEl.getAsDouble());
            }
            l2.setReasoningBasis(getStringSafe(l2Obj, "reasoningBasis"));
            l2.setRiskIndicators(extractStringArray(l2Obj, "riskIndicators"));
            method.setL2Confidence(l2);
        }

        return method;
    }

    private String getStringSafe(JsonObject obj, String field) {
        JsonElement el = obj.get(field);
        if (el != null && el.isJsonPrimitive()) {
            return el.getAsString();
        }
        return null;
    }

    private List<String> extractStringArray(JsonObject obj, String field) {
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
