package com.codelens.agent.core.llm;

import com.codelens.agent.core.message.ChatMessage;
import com.codelens.agent.core.tool.ToolCall;

import java.util.List;

/**
 * LLM 响应。包含文本回复和可选的 tool_calls。
 * <p>
 * OpenAI 响应中 choices[0].message 的映射：
 * <ul>
 *   <li>纯文本回复 → content 有值，toolCalls == null</li>
 *   <li>tool_call 请求 → content 可能为 null，toolCalls 有值</li>
 * </ul>
 * </p>
 */
public class LlmResponse {

    private final String content;
    private final List<ToolCall> toolCalls;

    public LlmResponse(String content, List<ToolCall> toolCalls) {
        this.content = content;
        this.toolCalls = toolCalls;
    }

    /** 纯文本回复时使用 */
    public static LlmResponse text(String content) {
        return new LlmResponse(content, null);
    }

    public String getContent() {
        return content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
