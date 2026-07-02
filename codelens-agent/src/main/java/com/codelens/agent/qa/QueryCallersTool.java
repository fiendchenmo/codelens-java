package com.codelens.agent.qa;

import com.codelens.agent.data.AnalysisDataProvider;
import com.google.gson.Gson;

import java.util.*;

/**
 * Tool: 查询类的调用方（谁调用了这些方法）。
 * <p>
 * 从 AnalysisDataProvider.getCalledBy() 获取调用方数据，
 * 支持按 className 查全部方法或按 methodName 过滤单个方法。
 * </p>
 */
public class QueryCallersTool extends CodeLensTool {

    private static final Gson gson = new Gson();

    public QueryCallersTool(AnalysisDataProvider dataProvider) {
        super(dataProvider);
    }

    @Override
    public String name() {
        return "query_callers";
    }

    @Override
    public String description() {
        return "查询指定类中各个方法的调用方（谁调用了这些方法）。"
                + "适用于分析修改影响范围、追踪调用链上游。"
                + "参数 className 为完全限定类名。可选参数 methodName 用于只查某个特定方法的调用方。";
    }

    @Override
    public String parameterSchema() {
        return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "\"className\":{\"type\":\"string\",\"description\":\"完全限定类名，如 com.example.UserService\"},"
                + "\"methodName\":{\"type\":\"string\",\"description\":\"可选，只查指定方法的调用方\"}"
                + "},"
                + "\"required\":[\"className\"]"
                + "}";
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String className = (String) arguments.get("className");
        if (className == null || className.isEmpty()) {
            return "{\"error\": \"参数 className 不能为空\"}";
        }

        String filePath = classNameToFilePath(className);
        Map<String, List<String>> calledBy = dataProvider.getCalledBy(filePath);
        if (calledBy == null || calledBy.isEmpty()) {
            return "{\"error\": \"类 " + className + " 尚无调用方数据\"}";
        }

        String methodName = (String) arguments.get("methodName");

        if (methodName != null && !methodName.isEmpty()) {
            // 单方法模式
            List<String> callers = calledBy.get(methodName);
            if (callers == null || callers.isEmpty()) {
                return "{\"error\": \"方法 " + methodName + " 尚无调用方数据\"}";
            }
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("class", className);
            result.put("method", methodName);
            result.put("callerCount", callers.size());
            result.put("callers", callers);
            return gson.toJson(result);
        } else {
            // 全方法模式
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("class", className);
            result.put("methodCount", calledBy.size());
            result.put("callers", calledBy);
            return gson.toJson(result);
        }
    }
}
