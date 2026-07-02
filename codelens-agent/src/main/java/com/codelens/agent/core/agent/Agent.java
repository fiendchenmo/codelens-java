package com.codelens.agent.core.agent;

import com.codelens.agent.core.llm.LlmClient;
import com.codelens.agent.core.llm.LlmResponse;
import com.codelens.agent.core.message.AssistantMessage;
import com.codelens.agent.core.message.ChatMessage;
import com.codelens.agent.core.message.SystemMessage;
import com.codelens.agent.core.message.ToolResultMessage;
import com.codelens.agent.core.message.UserMessage;
import com.codelens.agent.core.tool.ToolCall;
import com.codelens.agent.core.tool.ToolDefinition;
import com.codelens.agent.core.tool.ToolRegistry;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 主循环 — ReAct 模式。
 * <p>
 * Claude Code 的核心循环：
 * </p>
 * <pre>
 *   User asks → think → tool_call → observe → think → ... → final answer
 * </pre>
 * <p>
 * 实现细节：
 * <ol>
 *   <li>SystemMessage 初始化对话，注入角色 + 能力 + 约束</li>
 *   <li>UserMessage 加入 history</li>
 *   <li>调 LLM，如果返回 tool_calls 则逐个执行</li>
 *   <li>Tool 结果以 ToolResultMessage 回传 history（带 toolCallId）</li>
 *   <li>循环直到 LLM 不再调 Tool 或达到 maxRounds</li>
 *   <li>Tool 执行异常 → {"error":"..."} 作为 tool result，不抛异常</li>
 * </ol>
 */
public class Agent {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final String systemPrompt;
    private final int maxToolRounds;

    private static final Gson gson = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    /**
     * 创建 Agent。
     * @param llmClient     LLM 客户端
     * @param toolRegistry  Tool 注册表
     * @param systemPrompt  System Prompt（角色 + 能力 + L0 上下文）
     * @param maxToolRounds 最大 Tool 调用轮数（防止死循环）
     */
    public Agent(LlmClient llmClient, ToolRegistry toolRegistry,
                 String systemPrompt, int maxToolRounds) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.systemPrompt = systemPrompt;
        this.maxToolRounds = maxToolRounds;
    }

    /**
     * 提问并返回回答。每次调用创建独立对话（非会话模式）。
     * @param userQuestion 用户问题
     * @return LLM 最终回答
     */
    public String ask(String userQuestion) {
        // 1. 构建对话 history：System Prompt 始终在第一条
        List<ChatMessage> history = new ArrayList<ChatMessage>();
        history.add(new SystemMessage(systemPrompt));

        // 2. 用户问题
        history.add(new UserMessage(userQuestion));

        // 3. ReAct 循环
        int rounds = 0;
        String toolsJson = toolRegistry.isEmpty() ? null : toolRegistry.toToolsJson();

        while (rounds < maxToolRounds) {
            rounds++;

            // 调 LLM
            LlmResponse response = llmClient.chat(history, toolsJson);

            // 无 tool_calls → 返回文本回答
            if (!response.hasToolCalls()) {
                String answer = response.getContent();
                return answer != null ? answer : "";
            }

            // 有 tool_calls → 记录 assistant 消息（含 tool_calls）
            history.add(new AssistantMessage(response.getContent(), response.getToolCalls()));

            // 逐个执行 tool
            for (ToolCall call : response.getToolCalls()) {
                String result = executeTool(call);
                history.add(new ToolResultMessage(call.getId(), result));
            }

            // 下一轮循环，LLM 看到 tool 结果后会决定继续调 tool 还是给出最终回答
        }

        // 达到最大轮数 → 最后调一次 LLM（不带 tools）让它总结
        LlmResponse finalResponse = llmClient.chat(history, null);
        String answer = finalResponse.getContent();
        return (answer != null ? answer : "Agent 达到最大 Tool 调用轮数（" + maxToolRounds + "），请精简问题或分步提问。");
    }

    /**
     * 执行单个 Tool 调用。异常不抛，作为错误 JSON 返回。
     */
    private String executeTool(ToolCall call) {
        ToolDefinition tool = toolRegistry.get(call.getName());
        if (tool == null) {
            return "{\"error\": \"未知工具: " + call.getName() + "\"}";
        }
        try {
            Map<String, Object> args = parseArguments(call.getArguments());
            return tool.execute(args);
        } catch (Exception e) {
            return "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}";
        }
    }

    /**
     * 将 LLM 返回的 arguments JSON String 解析为 Map。
     */
    Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            return new java.util.HashMap<String, Object>();
        }
        return gson.fromJson(argumentsJson, MAP_TYPE);
    }

    /**
     * 简单 JSON 字符串转义。
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
