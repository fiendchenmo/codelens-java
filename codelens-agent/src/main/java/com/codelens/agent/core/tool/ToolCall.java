package com.codelens.agent.core.tool;

/**
 * LLM 返回的 tool_call。对�� OpenAI API tool_calls[].function 结构。
 * <p>
 * 由 {@link com.codelens.agent.core.llm.LlmResponse} 携带，
 * Agent 主循环根据 {@link #name} 从 {@link ToolRegistry} 查找对应 Tool 执行。
 * </p>
 */
public class ToolCall {

    private final String id;       // tool_call 唯一 ID（OpenAI 生成）
    private final String name;     // Tool 名称
    private final String arguments; // JSON String 参数

    public ToolCall(String id, String name, String arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getArguments() {
        return arguments;
    }
}
