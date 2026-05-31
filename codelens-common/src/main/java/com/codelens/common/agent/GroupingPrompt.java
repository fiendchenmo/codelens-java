package com.codelens.common.agent;

import com.codelens.common.agent.AggregateSummaryInput.FileSummaryEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

/**
 * 文件分组 Agent 的 Prompt 模板。
 * <p>
 * 根据文件名和摘要，对包内文件进行逻辑分组，输出分组表 JSON。
 * </p>
 */
public class GroupingPrompt {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String SYSTEM_PROMPT =
            "你是一位 Java 代码分析专家。请根据输入的文件列表及其摘要，对文件进行逻辑分组。\n" +
            "\n" +
            "输出格式必须为 JSON 数组，每个元素包含：\n" +
            "1. group: 分组名称 (String)，如 \"业务逻辑\"、\"数据访问\"、\"配置管理\"\n" +
            "2. files: 属于该组的文件名列表 (Array of String)\n" +
            "\n" +
            "约束：\n" +
            "- 至少输出 1 个分组\n" +
            "- 每个文件只能属于一个分组\n" +
            "- 分组名称应能清晰反映文件的功能归属\n" +
            "- 仅输出 JSON 数组，不要包含 ```json 标记";

    private static final String USER_TEMPLATE =
            "请对以下文件进行逻辑分组：\n" +
            "\n" +
            "=== 文件列表 ===\n" +
            "{{fileEntries}}";

    /**
     * 生成 System Prompt。
     */
    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * 根据文件摘要条目列表生成 User Prompt。
     *
     * @param entries 文件摘要条目列表
     * @return 完整的 User Prompt 字符串
     */
    public String buildUserPrompt(List<FileSummaryEntry> entries) {
        String fileEntries = entries != null ? GSON.toJson(entries) : "";
        return USER_TEMPLATE.replace("{{fileEntries}}", fileEntries);
    }

    /**
     * 生成完整的分组 Prompt（System + User）。
     *
     * @param entries 文件摘要条目列表
     * @return 完整的 Prompt 字符串
     */
    public String buildGroupingPrompt(List<FileSummaryEntry> entries) {
        return buildSystemPrompt() + "\n\n" + buildUserPrompt(entries);
    }
}
