package com.codelens.agent.core.message;

import com.codelens.agent.core.tool.ToolCall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assistant 消息。LLM 回复，可能携带 tool_calls。
 * <p>
 * OpenAI JSON（无 tool_calls）: {"role":"assistant", "content":"..."}<br>
 * OpenAI JSON（有 tool_calls）: {"role":"assistant", "content":null, "tool_calls":[...]}
 * </p>
 */
public class AssistantMessage extends ChatMessage {

    private final List<ToolCall> toolCalls;

    public AssistantMessage(String content) {
        super(Role.ASSISTANT, content);
        this.toolCalls = null;
    }

    public AssistantMessage(String content, List<ToolCall> toolCalls) {
        super(Role.ASSISTANT, content);
        this.toolCalls = toolCalls;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    @Override
    public String toJson() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("role", role.name().toLowerCase());
        map.put("content", content);
        if (hasToolCalls()) {
            List<Map<String, Object>> tcList = new ArrayList<Map<String, Object>>();
            for (ToolCall tc : toolCalls) {
                Map<String, Object> tcMap = new HashMap<String, Object>();
                tcMap.put("id", tc.getId());
                tcMap.put("type", "function");
                Map<String, String> funcMap = new HashMap<String, String>();
                funcMap.put("name", tc.getName());
                funcMap.put("arguments", tc.getArguments());
                tcMap.put("function", funcMap);
                tcList.add(tcMap);
            }
            map.put("tool_calls", tcList);
        }
        return gson.toJson(map);
    }
}
