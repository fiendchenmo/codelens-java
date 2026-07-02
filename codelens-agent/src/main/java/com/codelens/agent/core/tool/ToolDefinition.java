package com.codelens.agent.core.tool;

import java.util.Map;

/**
 * Tool 定义接口。对应 Claude Code 中的 Tool 抽象：
 * name + description + JSON Schema parameters + execute。
 * <p>
 * 实现类通过 {@link ToolRegistry} 注册，Agent 自动生成 OpenAI tools JSON。
 * JDK 1.8 兼容：纯接口，无注解，零外部依赖。
 * </p>
 *
 * <h3>实现规范</h3>
 * <ul>
 *   <li>{@link #name()} — 唯一标识，LLM 通过此名称调用</li>
 *   <li>{@link #description()} — LLM 据此判断何时调用，需描述清楚用途和参数</li>
 *   <li>{@link #parameterSchema()} — JSON Schema 字符串，OpenAI function.parameters 格式</li>
 *   <li>{@link #execute(Map)} — 执行逻辑，返回精简 JSON 字符串；异常返回 {"error":"..."}</li>
 * </ul>
 */
public interface ToolDefinition {

    /** Tool 唯一名称，LLM 通过此名称调用 */
    String name();

    /** Tool 描述，LLM 据此判断何时调用 */
    String description();

    /** 参数 JSON Schema（OpenAI function.parameters 格式） */
    String parameterSchema();

    /**
     * 执行 Tool。
     * @param arguments 参数 map（从 LLM tool_call.arguments JSON 解析）
     * @return 执行结果（精简 JSON 字符串）
     */
    String execute(Map<String, Object> arguments);
}
