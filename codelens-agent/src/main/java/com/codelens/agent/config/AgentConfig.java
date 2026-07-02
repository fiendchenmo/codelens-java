package com.codelens.agent.config;

/**
 * Agent 配置。
 * <p>
 * LLM Provider / API Key / Model 配置，以及 Tool 调用轮数限制、超时等参数。
 * </p>
 */
public class AgentConfig {

    /** LLM Provider 标识（"deepseek" | "openai" | "zhipu" | "ollama"） */
    private final String provider;

    /** API Key */
    private final String apiKey;

    /** OpenAI 兼容 endpoint */
    private final String baseUrl;

    /** 模型名（如 "deepseek-chat", "glm-4-flash"） */
    private final String modelName;

    /** HTTP 连接超时（秒） */
    private final int timeoutSeconds;

    /** 最大 Tool 调用轮数，防止死循环 */
    private final int maxToolRounds;

    /** 是否启用脱敏（Stage 3 填充） */
    private final boolean sanitizeEnabled;

    private AgentConfig(Builder builder) {
        this.provider = builder.provider;
        this.apiKey = builder.apiKey;
        this.baseUrl = builder.baseUrl;
        this.modelName = builder.modelName;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.maxToolRounds = builder.maxToolRounds;
        this.sanitizeEnabled = builder.sanitizeEnabled;
    }

    public String getProvider() {
        return provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getMaxToolRounds() {
        return maxToolRounds;
    }

    public boolean isSanitizeEnabled() {
        return sanitizeEnabled;
    }

    /** Builder 模式 */
    public static class Builder {
        private String provider = "deepseek";
        private String apiKey;
        private String baseUrl = "https://api.deepseek.com";
        private String modelName = "deepseek-chat";
        private int timeoutSeconds = 120;
        private int maxToolRounds = 5;
        private boolean sanitizeEnabled = false;

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder maxToolRounds(int maxToolRounds) {
            this.maxToolRounds = maxToolRounds;
            return this;
        }

        public Builder sanitizeEnabled(boolean sanitizeEnabled) {
            this.sanitizeEnabled = sanitizeEnabled;
            return this;
        }

        public AgentConfig build() {
            return new AgentConfig(this);
        }
    }

    /** 快捷工厂：从环境变量创建 */
    public static AgentConfig fromEnv() {
        String apiKey = System.getenv("CODELENS_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }

        String baseUrl = System.getenv("CODELENS_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.deepseek.com";
        }

        String model = System.getenv("CODELENS_MODEL");
        if (model == null || model.isEmpty()) {
            model = "deepseek-chat";
        }

        return new Builder()
                .provider("deepseek")
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(model)
                .build();
    }
}
