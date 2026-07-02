package com.codelens.agent.core.message;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool 执行结果消息。工具执行后回传给 LLM，带 tool_call_id 关联。
 * <p>
 * OpenAI JSON: {"role":"tool", "tool_call_id":"...", "content":"..."}
 * </p>
 *
 * <p>Claude Code 模式：tool error 也作为 ToolResultMessage 返回，
 * 不抛异常，让 LLM 自行处理错误结果。</p>
 */
public class ToolResultMessage extends ChatMessage {

    private final String toolCallId;

    public ToolResultMessage(String toolCallId, String result) {
        super(Role.TOOL, result);
        this.toolCallId = toolCallId;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    @Override
    public String toJson() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("role", "tool");
        map.put("tool_call_id", toolCallId);
        map.put("content", content);
        return gson.toJson(map);
    }
}
