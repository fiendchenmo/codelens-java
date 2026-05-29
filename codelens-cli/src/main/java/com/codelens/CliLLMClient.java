package com.codelens;

import com.codelens.common.llm.LLMClient;

/**
 * CLI 端 LLMClient 实现。
 * <p>
 * 委托给 {@link LLMClient#analyze(String, String, String, String, String, double)} 执行 HTTP 调用。
 */
public class CliLLMClient implements LLMClient {

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final double temperature;

    public CliLLMClient(String apiKey, String apiUrl, String model, double temperature) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.temperature = temperature;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        try {
            return com.codelens.LLMClient.analyze(apiKey, systemPrompt, userPrompt,
                    apiUrl, model, temperature);
        } catch (LLMException e) {
            throw new RuntimeException("LLM 调用失败: " + e.getUserFriendlyMessage(), e);
        }
    }
}
