package com.codelens.agent.core.message;

/**
 * User 消息。用户提问，以及 L1 上下文注入前缀。
 * <p>
 * OpenAI JSON: {"role":"user", "content":"..."}
 * </p>
 */
public class UserMessage extends ChatMessage {

    public UserMessage(String content) {
        super(Role.USER, content);
    }
}
