package com.codelens.agent.qa;

import com.codelens.agent.config.AgentConfig;
import com.codelens.agent.context.AnalysisContextProvider;
import com.codelens.agent.core.agent.Agent;
import com.codelens.agent.core.llm.LlmClient;
import com.codelens.agent.core.tool.ToolRegistry;
import com.codelens.agent.data.AnalysisDataProvider;

/**
 * CodeQA Agent：代码质量问答。
 * <p>
 * 封装 {@link Agent} + {@link ToolRegistry} + {@link AnalysisContextProvider}，
 * 对外暴露简洁的 {@link #ask(String)} 接口。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 1. 创建 LLM 客户端
 * LlmClient client = new OpenAiClient("https://api.deepseek.com", apiKey, "deepseek-chat");
 *
 * // 2. 创建 DataProvider（插件端实现）
 * AnalysisDataProvider dataProvider = new MyDataProvider();
 *
 * // 3. 创建 ContextProvider
 * AnalysisContextProvider contextProvider = new AnalysisContextProvider();
 * contextProvider.initProjectContext(projectContext);
 *
 * // 4. 创建 Agent
 * CodeQAAgent agent = CodeQAAgent.create(client, dataProvider, contextProvider, config);
 *
 * // 5. 提问
 * String answer = agent.ask("UserService.login 有什么风险？");
 * }</pre>
 *
 * <p>Claude Code 模式：CodeQAAgent 对应 Claude Code 的 Agent Info
 * （name + description + prompt + tools），封装了完整的 Agent 生命周期。</p>
 */
public class CodeQAAgent {

    private final Agent agent;
    private final AnalysisContextProvider contextProvider;

    private CodeQAAgent(Agent agent, AnalysisContextProvider contextProvider) {
        this.agent = agent;
        this.contextProvider = contextProvider;
    }

    /**
     * 工厂方法：构建 CodeQAAgent 实例。
     * <p>
     * 完成以下初始化：
     * <ol>
     *   <li>L0 项目上下文注入 System Prompt</li>
     *   <li>注册 Stage 1 的 2 个 Tool（Stage 2 扩展到 8 个）</li>
     *   <li>创建 Agent（ReAct 循环 + Tool 注册表 + System Prompt）</li>
     * </ol>
     *
     * @param client          LLM 客户端
     * @param dataProvider    数据访问桥接（插件端实现 AnalysisDataProvider）
     * @param contextProvider 三级上下文提供者
     * @param config          Agent 配置
     * @return 可用的 CodeQAAgent 实例
     */
    public static CodeQAAgent create(LlmClient client,
                                      AnalysisDataProvider dataProvider,
                                      AnalysisContextProvider contextProvider,
                                      AgentConfig config) {
        // 1. L0 项目级上下文拼入 System Prompt
        String systemPrompt = CodeQAPrompt.buildSystemPrompt(contextProvider.getProjectSummary());

        // 2. 注册 Tool（Stage 1: 2 个；Stage 2 扩展到 8 个）
        ToolRegistry registry = new ToolRegistry();
        registry.register(new QueryClassAnalysisTool(dataProvider));
        registry.register(new QueryRiskOverviewTool(dataProvider));

        // 3. 创建 Agent
        Agent agent = new Agent(client, registry, systemPrompt, config.getMaxToolRounds());

        return new CodeQAAgent(agent, contextProvider);
    }

    /**
     * 提问并返回回答。
     * <p>
     * L1 当前文件上下文自动注入 UserMessage 前缀。
     * </p>
     *
     * @param question 用户问题
     * @return LLM 回答
     */
    public String ask(String question) {
        // L1 当前文件上下文拼入 UserMessage 前缀
        String contextPrefix = contextProvider.getFileContextSummary();
        String fullQuestion = contextPrefix.isEmpty()
                ? question
                : contextPrefix + "\n\n" + question;
        return agent.ask(fullQuestion);
    }
}
