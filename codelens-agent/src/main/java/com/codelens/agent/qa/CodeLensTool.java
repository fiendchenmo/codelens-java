package com.codelens.agent.qa;

import com.codelens.agent.core.tool.ToolDefinition;
import com.codelens.agent.data.AnalysisDataProvider;

/**
 * CodeLens 专属 Tool 基类。
 * <p>
 * 实现 {@link ToolDefinition}，持有 {@link AnalysisDataProvider} 引用。
 * 子类只需实现 name/description/parameterSchema/execute，
 * 通过 dataProvider 访问分析数据。
 * </p>
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
}
