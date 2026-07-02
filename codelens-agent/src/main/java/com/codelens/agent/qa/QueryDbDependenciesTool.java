package com.codelens.agent.qa;

import com.codelens.agent.data.AnalysisDataProvider;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;

/**
 * Tool: 查询类的数据库操作依赖。
 * <p>
 * 从 AnalysisDataProvider.getDbAnalysisJson() 获取 DB 依赖数据，
 * 提取表名、SQL 类型、Mapper 类等关键信息返回精简 JSON。
 * </p>
 */
public class QueryDbDependenciesTool extends CodeLensTool {

    private static final Gson gson = new Gson();

    public QueryDbDependenciesTool(AnalysisDataProvider dataProvider) {
        super(dataProvider);
    }

    @Override
    public String name() {
        return "query_db_dependencies";
    }

    @Override
    public String description() {
        return "查询指定类的数据库操作依赖，包括操作的表、SQL 类型（SELECT/INSERT/UPDATE/DELETE）等。"
                + "适用于分析数据库层隐式依赖、评估字段变更影响范围。"
                + "参数 className 为完全限定类名。";
    }

    @Override
    public String parameterSchema() {
        return "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "\"className\":{\"type\":\"string\",\"description\":\"完全限定类名，如 com.example.UserService\"}"
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

        String json = dataProvider.getDbAnalysisJson(className);
        if (json == null || json.isEmpty()) {
            return "{\"error\": \"类 " + className + " 尚无数据库依赖分析数据\"}";
        }

        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("class", className);

            // 提取表名列表（防御性解析）
            List<String> tableNames = new ArrayList<String>();
            if (root.has("tables") && root.get("tables").isJsonArray()) {
                JsonArray tablesArr = root.getAsJsonArray("tables");
                for (int i = 0; i < tablesArr.size(); i++) {
                    if (tablesArr.get(i).isJsonObject()) {
                        JsonObject table = tablesArr.get(i).getAsJsonObject();
                        String tableName = getStringSafe(table, "name");
                        if (tableName != null) {
                            tableNames.add(tableName);
                        }
                    } else if (tablesArr.get(i).isJsonPrimitive()) {
                        tableNames.add(tablesArr.get(i).getAsString());
                    }
                }
            }
            result.put("tableCount", tableNames.size());
            result.put("tables", tableNames);

            // SQL 类型（防御性解析）
            List<String> sqlTypes = new ArrayList<String>();
            if (root.has("sqlTypes") && root.get("sqlTypes").isJsonArray()) {
                JsonArray typesArr = root.getAsJsonArray("sqlTypes");
                for (int i = 0; i < typesArr.size(); i++) {
                    if (typesArr.get(i).isJsonPrimitive()) {
                        sqlTypes.add(typesArr.get(i).getAsString());
                    }
                }
            }
            result.put("sqlTypes", sqlTypes);

            // Mapper 类
            String mapperClass = getStringSafe(root, "mapperClass");
            if (mapperClass != null) {
                result.put("mapperClass", mapperClass);
            }

            return gson.toJson(result);

        } catch (Exception e) {
            return "{\"error\": \"DB 依赖 JSON 解析失败: " + e.getMessage() + "\"}";
        }
    }
}
