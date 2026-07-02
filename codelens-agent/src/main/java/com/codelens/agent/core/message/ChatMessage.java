package com.codelens.agent.core.message;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/**
 * 聊天消息基类。对应 OpenAI Chat Completion API 的 message 对象。
 * <p>
 * 所有消息类型（system/user/assistant/tool）继承此类，
 * 子类覆盖 {@link #toJson()} 以适配各自的 OpenAI JSON 格式。
 * </p>
 *
 * <p>Claude Code 模式映射：Claude Code 的消息模型同样是分层继承，
 * tool result 以独立消息类型回传 history，带 tool_call_id 关联。</p>
 */
public class ChatMessage {

    /** 消息角色，对应 OpenAI API role 字段 */
    public enum Role {
        @SerializedName("system")
        SYSTEM,
        @SerializedName("user")
        USER,
        @SerializedName("assistant")
        ASSISTANT,
        @SerializedName("tool")
        TOOL
    }

    protected final Role role;
    protected final String content;

    protected static final Gson gson = new Gson();

    public ChatMessage(Role role, String content) {
        this.role = role;
        this.content = content;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    /**
     * 序列化为 OpenAI API 兼容的 JSON。
     * 基类生成 {"role":"...", "content":"..."}，子类可覆盖以添加额外字段。
     */
    public String toJson() {
        return gson.toJson(this);
    }
}
