package com.codelens.agent.qa;

import com.codelens.agent.data.AnalysisDataProvider;
import com.google.gson.Gson;

import java.util.*;

/**
 * Tool: 查询共享同一张数据库表的所有类。
 * <p>
 * 从 AnalysisDataProvider.findClassesByTableName() 获取类列表，
 * 用于发现隐式耦合、评估表结构变更的波及范围。
 * </p>
 */
public class QueryTableSharingTool extends CodeLensTool {

    private static final Gson gson = new Gson();

    public QueryTableSharingTool(AnalysisDataProvider dataProvider) {
        super(dataProvider);
    }

    @Override
    public String name() {
        return "query_table_sharing";
    }

    @Override
    public String description() {
        return "查询共享同一张数据库表的所有 Java 类。"
                + "适用于发现隐式耦合、评估表结构变更的波及范围。"
                + "参数 tableName 为数据库表名，如 sys_user。";
    }

    @Override
    public String parameterSchema() {
        return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "\"tableName\":{\"type\":\"string\",\"description\":\"数据库表名，如 sys_user\"}"
                + "},"
                + "\"required\":[\"tableName\"]"
                + "}";
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String tableName = (String) arguments.get("tableName");
        if (tableName == null || tableName.isEmpty()) {
            return "{\"error\": \"参数 tableName 不能为空\"}";
        }

        List<String> classes = dataProvider.findClassesByTableName(tableName);
        if (classes == null || classes.isEmpty()) {
            return "{\"error\": \"没有类共享表 " + tableName + "，或尚未进行数据库依赖分析\"}";
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("table", tableName);
        result.put("classCount", classes.size());
        result.put("classes", classes);

        return gson.toJson(result);
    }
}
