package com.codelens.agent.core.message;

/**
 * System 消息。Agent 角色定义、能力声明、约束规则，注入对话开头。
 * <p>
 * OpenAI JSON: {"role":"system", "content":"..."}
 * </p>
 */
public class SystemMessage extends ChatMessage {

    public SystemMessage(String content) {
        super(Role.SYSTEM, content);
    }
}
