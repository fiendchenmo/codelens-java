package com.codelens.agent.qa;

import com.codelens.agent.core.tool.ToolDefinition;
import com.codelens.agent.data.AnalysisDataProvider;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * CodeLens 专属 Tool 基类。
 * <p>
 * 实现 {@link ToolDefinition}，持有 {@link AnalysisDataProvider} 引用。
 * 子类只需实现 name/description/parameterSchema/execute，
 * 通过 dataProvider 访问分析数据。
 * </p>
 *
 * <p>提供 protected static JSON 辅助方法，子类共用，消除重复。</p>
 */
public abstract class CodeLensTool implements ToolDefinition {

    protected final AnalysisDataProvider dataProvider;

    protected CodeLensTool(AnalysisDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    /**
     * 将类名转换为文件路径（默认实现：com.example.Foo → com/example/Foo.java）。
     * 子类可覆盖以适配不同的路径映射规则。
     */
    protected String classNameToFilePath(String className) {
        if (className == null || className.isEmpty()) {
            return "";
        }
        return className.replace('.', '/') + ".java";
    }

    // ─── JSON 辅助方法（protected static，子类共享） ──────────

    /**
     * 安全获取 JSON 字符串字段，字段不存在或类型不匹配返回 null。
     */
    protected static String getStringSafe(JsonObject obj, String field) {
        JsonElement el = obj.get(field);
        if (el != null && el.isJsonPrimitive()) {
            return el.getAsString();
        }
        return null;
    }

    /**
     * 安全获取 JSON 整数字段，字段不存在或解析失败返回 0。
     */
    protected static int getIntSafe(JsonObject obj, String field) {
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
}
