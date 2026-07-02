package com.codelens.agent.core.llm;

import com.codelens.agent.core.message.ChatMessage;

import java.util.List;

/**
 * LLM 客户端抽象接口。
 * <p>
 * Claude Code 模式：LLM 客户端只负责发送消息 + tools 定义，
 * 返���原始 LLM 响应（content + tool_calls），不参与工具执行。
 * 工具执行由 Agent 主循环负责。
 * </p>
 */
public interface LlmClient {

    /**
     * 发送聊天请求。
     * @param messages  对话消息列表（system + user + assistant + tool 交替）
     * @param toolsJson 工具定义 JSON（OpenAI tools 数组格式），可为 null 表示不带工具
     * @return LLM 响应，包含文本回复和/�� tool_calls
     */
    LlmResponse chat(List<ChatMessage> messages, String toolsJson);
}
