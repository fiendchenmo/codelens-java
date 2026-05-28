package com.codelens.common.llm;

/**
 * LLM 调用抽象接口。
 * <p>
 * CLI 端和插件端各自实现此接口。
 */
public interface LLMClient {

    /**
     * 调用 LLM。
     *
     * @param systemPrompt system prompt
     * @param userPrompt   user prompt
     * @return LLM 返回的文本
     */
    String chat(String systemPrompt, String userPrompt);
}
