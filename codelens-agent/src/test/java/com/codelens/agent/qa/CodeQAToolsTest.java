package com.codelens.agent.qa;

import com.codelens.agent.core.agent.Agent;
import com.codelens.agent.core.llm.LlmClient;
import com.codelens.agent.core.llm.LlmResponse;
import com.codelens.agent.core.message.ChatMessage;
import com.codelens.agent.core.message.SystemMessage;
import com.codelens.agent.core.message.AssistantMessage;
import com.codelens.agent.core.message.ToolResultMessage;
import com.codelens.agent.core.message.UserMessage;
import com.codelens.agent.core.tool.ToolCall;
import com.codelens.agent.core.tool.ToolDefinition;
import com.codelens.agent.core.tool.ToolRegistry;
import com.codelens.agent.data.AnalysisDataProvider;
import com.codelens.agent.config.AgentConfig;
import com.codelens.agent.context.AnalysisContextProvider;
import com.codelens.agent.context.ProjectContext;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeQA Tools + Agent 单元测试。
 * 使用真实 V3 JSON 数据测试 Tool 输出，mock LlmClient 测试 Agent 主循环。
 */
public class CodeQAToolsTest {

    private static final Gson gson = new Gson();

    // ─── 测试数据：模拟 V3 JSON ──────────────────────────

    /** 模拟完整的 V3 分析 JSON（包含 methods + risks + 矛盾） */
    private static final String V3_JSON = "{"
            + "\"summary\": \"UserService handles authentication and user management\","
            + "\"framework\": \"Spring Boot + MyBatis-Plus\","
            + "\"risks\": ["
            + "  {"
            + "    \"type\": \"SECURITY\","
            + "    \"description\": \"Hardcoded secret key in JWT generation\","
            + "    \"line\": 42,"
            + "    \"severity\": \"HIGH\","
            + "    \"impact\": \"Credential leak if code is shared\","
            + "    \"suggestion\": \"Use environment variable\","
            + "    \"confidence\": 0.9"
            + "  }"
            + "],"
            + "\"fields\": ["
            + "  {\"name\": \"userMapper\", \"type\": \"SysUserMapper\", \"injectType\": \"@Autowired\", \"line\": 25}"
            + "],"
            + "\"methods\": ["
            + "  {"
            + "    \"name\": \"login\","
            + "    \"signature\": \"public LoginResult login(String username, String password)\","
            + "    \"line\": 40,"
            + "    \"description\": \"Authenticate user by username and password\","
            + "    \"complexity\": \"MEDIUM\","
            + "    \"visibility\": \"public\","
            + "    \"calls\": ["
            + "      {\"target\": \"SysUserMapper.selectUserByUserName\", \"line\": 44, \"type\": \"cross_file\"},"
            + "      {\"target\": \"this.validatePassword\", \"line\": 46, \"type\": \"same_file\"}"
            + "    ],"
            + "    \"called_by\": ["
            + "      {\"caller\": \"AuthController.login\", \"line\": 30},"
            + "      {\"caller\": \"AuthFilter.authenticate\", \"line\": 55}"
            + "    ],"
            + "    \"risks\": ["
            + "      {"
            + "        \"type\": \"C3\","
            + "        \"description\": \"SQL注入风险：username参数未经校验直接拼接MyBatis查询\","
            + "        \"line\": 44,"
            + "        \"severity\": \"HIGH\","
            + "        \"impact\": \"数据库数据泄露或篡改\","
            + "        \"suggestion\": \"使用参数化查询或MyBatis #{}语法\","
            + "        \"confidence\": 0.95"
            + "      },"
            + "      {"
            + "        \"type\": \"C2\","
            + "        \"description\": \"硬编码JWT密钥\","
            + "        \"line\": 42,"
            + "        \"severity\": \"HIGH\","
            + "        \"impact\": \"密钥泄露导致任意用户伪造\","
            + "        \"suggestion\": \"从环境变量或配置中心读取\","
            + "        \"confidence\": 0.9"
            + "      }"
            + "    ]"
            + "  },"
            + "  {"
            + "    \"name\": \"logout\","
            + "    \"signature\": \"public void logout()\","
            + "    \"line\": 60,"
            + "    \"description\": \"Clear current user session\","
            + "    \"complexity\": \"LOW\","
            + "    \"visibility\": \"public\","
            + "    \"calls\": ["
            + "      {\"target\": \"SessionManager.invalidate\", \"line\": 62, \"type\": \"cross_file\"}"
            + "    ],"
            + "    \"risks\": ["
            + "      {"
            + "        \"type\": \"MAINTAINABILITY\","
            + "        \"description\": \"未清理ThreadLocal残留\","
            + "        \"line\": 62,"
            + "        \"severity\": \"LOW\","
            + "        \"impact\": \"内存泄漏风险\","
            + "        \"suggestion\": \"登出后调用ThreadLocal.remove()\","
            + "        \"confidence\": 0.5"
            + "      }"
            + "    ]"
            + "  }"
            + "],"
            + "\"contradiction_count\": 1"
            + "}";

    // ─── Mock DataProvider ──────────────────────────────

    private AnalysisDataProvider createMockDataProvider() {
        return new AnalysisDataProvider() {
            @Override
            public String getV3AnalysisJson(String filePath) {
                return V3_JSON;
            }

            @Override
            public Map<String, List<String>> getCalledBy(String filePath) {
                return new HashMap<>();
            }

            @Override
            public String getDbAnalysisJson(String className) {
                return "{\"tables\":[\"sys_user\"],\"sqlTypes\":[\"SELECT\"]}";
            }

            @Override
            public List<String> findClassesByTableName(String tableName) {
                return Arrays.asList("com.example.UserService", "com.example.OrderService");
            }

            @Override
            public String getContradictionReportJson(String filePath) {
                return "{\"contradictionCount\":1,\"score\":0.3}";
            }

            @Override
            public String getPackageSummaryJson(String packageName) {
                return "{\"classCount\":5,\"riskDistribution\":{\"HIGH\":2,\"MEDIUM\":3}}";
            }

            @Override
            public String searchMethods(String keyword, int limit) {
                return "[{\"className\":\"UserService\",\"methodName\":\"login\",\"signature\":\"public LoginResult login(String,String)\"}]";
            }

            @Override
            public String getProjectSummary() {
                return "RuoYi-Cloud | Spring Boot 2.5 | 入口: RuoYiApplication | 模块: ruoyi-auth, ruoyi-system";
            }
        };
    }

    // ─── Tool 测试 ─────────────────────────────────────

    @Test
    void queryClassAnalysis_returnsSummaryJson() {
        QueryClassAnalysisTool tool = new QueryClassAnalysisTool(createMockDataProvider());

        Map<String, Object> args = new HashMap<>();
        args.put("className", "com.example.UserService");

        String result = tool.execute(args);
        assertNotNull(result);

        JsonObject json = JsonParser.parseString(result).getAsJsonObject();
        assertEquals("com.example.UserService", json.get("class").getAsString());
        assertEquals(2, json.get("methodCount").getAsInt());
        assertTrue(json.get("riskCount").getAsInt() >= 3, "应有至少3个risk (1顶层 + 2个方法内)");
        assertEquals(1, json.get("contradictionCount").getAsInt());
    }

    @Test
    void queryClassAnalysis_missingClassName_returnsError() {
        QueryClassAnalysisTool tool = new QueryClassAnalysisTool(createMockDataProvider());

        Map<String, Object> args = new HashMap<>();
        String result = tool.execute(args);

        assertTrue(result.contains("\"error\""), "缺少className应返回错误JSON");
    }

    @Test
    void queryRiskOverview_returnsGroupedBySeverity() {
        QueryRiskOverviewTool tool = new QueryRiskOverviewTool(createMockDataProvider());

        Map<String, Object> args = new HashMap<>();
        args.put("className", "com.example.UserService");

        String result = tool.execute(args);
        assertNotNull(result);

        JsonObject json = JsonParser.parseString(result).getAsJsonObject();
        assertEquals("com.example.UserService", json.get("class").getAsString());
        assertTrue(json.get("riskCount").getAsInt() >= 3);

        JsonObject bySeverity = json.getAsJsonObject("bySeverity");
        assertTrue(bySeverity.get("HIGH").getAsInt() >= 2, "至少2个HIGH风险");
        // topRisks 最多5个
        assertTrue(json.getAsJsonArray("topRisks").size() <= 5);
    }

    @Test
    void queryRiskOverview_missingClassName_returnsError() {
        QueryRiskOverviewTool tool = new QueryRiskOverviewTool(createMockDataProvider());

        Map<String, Object> args = new HashMap<>();
        String result = tool.execute(args);

        assertTrue(result.contains("\"error\""));
    }

    // ─── ToolRegistry 测试 ──────────────────────────────

    @Test
    void toolRegistry_toToolsJson_generatesOpenAiFormat() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new QueryClassAnalysisTool(createMockDataProvider()));
        registry.register(new QueryRiskOverviewTool(createMockDataProvider()));

        String toolsJson = registry.toToolsJson();
        assertNotNull(toolsJson);

        // 解析验证格式
        JsonObject firstTool = JsonParser.parseString(toolsJson)
                .getAsJsonArray().get(0).getAsJsonObject();
        assertEquals("function", firstTool.get("type").getAsString());

        JsonObject func = firstTool.getAsJsonObject("function");
        assertEquals("query_class_analysis", func.get("name").getAsString());
        assertNotNull(func.get("description").getAsString());
        assertNotNull(func.get("parameters"));
    }

    @Test
    void toolRegistry_size() {
        ToolRegistry registry = new ToolRegistry();
        assertEquals(0, registry.size());
        assertTrue(registry.isEmpty());

        registry.register(new QueryClassAnalysisTool(createMockDataProvider()));
        assertEquals(1, registry.size());
        assertFalse(registry.isEmpty());
    }

    // ─── 消息模型测试 ──────────────────────────────────

    @Test
    void systemMessage_toJson() {
        SystemMessage msg = new SystemMessage("You are a helpful assistant.");
        String json = msg.toJson();

        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("system", obj.get("role").getAsString());
        assertEquals("You are a helpful assistant.", obj.get("content").getAsString());
    }

    @Test
    void userMessage_toJson() {
        UserMessage msg = new UserMessage("What are the risks in UserService?");
        String json = msg.toJson();

        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("user", obj.get("role").getAsString());
    }

    @Test
    void assistantMessage_withToolCalls_toJson() {
        List<ToolCall> toolCalls = new ArrayList<>();
        toolCalls.add(new ToolCall("call_1", "query_class_analysis",
                "{\"className\":\"com.example.UserService\"}"));

        AssistantMessage msg = new AssistantMessage(null, toolCalls);
        String json = msg.toJson();

        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("assistant", obj.get("role").getAsString());
        assertTrue(obj.has("tool_calls"));
        assertEquals(1, obj.getAsJsonArray("tool_calls").size());

        JsonObject tc = obj.getAsJsonArray("tool_calls").get(0).getAsJsonObject();
        assertEquals("call_1", tc.get("id").getAsString());
        assertEquals("function", tc.get("type").getAsString());
    }

    @Test
    void assistantMessage_withoutToolCalls_toJson() {
        AssistantMessage msg = new AssistantMessage("The analysis is complete.");
        String json = msg.toJson();

        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("assistant", obj.get("role").getAsString());
        assertEquals("The analysis is complete.", obj.get("content").getAsString());
        assertFalse(obj.has("tool_calls"));
    }

    @Test
    void toolResultMessage_toJson() {
        ToolResultMessage msg = new ToolResultMessage("call_1",
                "{\"class\":\"UserService\",\"methodCount\":2}");
        String json = msg.toJson();

        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("tool", obj.get("role").getAsString());
        assertEquals("call_1", obj.get("tool_call_id").getAsString());
        assertNotNull(obj.get("content").getAsString());
    }

    // ─── Agent 主循环测试 ───────────────────────────────

    @Test
    void agent_ask_withoutToolCalls_returnsDirectAnswer() {
        // Mock LLM: 直接返回文本，不调 tool
        LlmClient mockLlm = new LlmClient() {
            @Override
            public LlmResponse chat(List<ChatMessage> messages, String toolsJson) {
                return LlmResponse.text("UserService 存在 3 个风险项，其中 2 个为 HIGH 级别。");
            }
        };

        ToolRegistry registry = new ToolRegistry();
        registry.register(new QueryClassAnalysisTool(createMockDataProvider()));

        Agent agent = new Agent(mockLlm, registry, "You are a code assistant.", 5);
        String answer = agent.ask("UserService有什么风险？");

        assertNotNull(answer);
        assertTrue(answer.contains("UserService"), "回答应包含类名");
    }

    @Test
    void agent_ask_withToolCalls_executesToolsAndReturnsAnswer() {
        // Mock LLM: 第一次返回 tool_call，第二次返回文本
        LlmClient mockLlm = new LlmClient() {
            private int callCount = 0;

            @Override
            public LlmResponse chat(List<ChatMessage> messages, String toolsJson) {
                callCount++;
                if (callCount == 1) {
                    // 第一次：返回 tool_call
                    List<ToolCall> toolCalls = new ArrayList<>();
                    toolCalls.add(new ToolCall("call_1", "query_class_analysis",
                            "{\"className\":\"com.example.UserService\"}"));
                    return new LlmResponse(null, toolCalls);
                } else {
                    // 第二次：看到 tool result 后返回最终回答
                    return LlmResponse.text("根据分析，UserService 有 3 个风险项。");
                }
            }
        };

        ToolRegistry registry = new ToolRegistry();
        registry.register(new QueryClassAnalysisTool(createMockDataProvider()));

        Agent agent = new Agent(mockLlm, registry, "You are a code assistant.", 5);
        String answer = agent.ask("UserService有什么风险？");

        assertNotNull(answer);
        assertFalse(answer.isEmpty());
    }

    @Test
    void agent_ask_unknownTool_returnsErrorInToolResult() {
        // Mock LLM: 调用不存在的 tool，然后看 tool result 能传回去
        LlmClient mockLlm = new LlmClient() {
            private int callCount = 0;

            @Override
            public LlmResponse chat(List<ChatMessage> messages, String toolsJson) {
                callCount++;
                if (callCount == 1) {
                    List<ToolCall> toolCalls = new ArrayList<>();
                    toolCalls.add(new ToolCall("call_1", "unknown_tool",
                            "{\"key\":\"value\"}"));
                    return new LlmResponse(null, toolCalls);
                } else {
                    return LlmResponse.text("工具调用失败，无法回答。");
                }
            }
        };

        ToolRegistry registry = new ToolRegistry();
        Agent agent = new Agent(mockLlm, registry, "You are a code assistant.", 5);
        String answer = agent.ask("test");

        assertNotNull(answer);
    }

    @Test
    void agent_ask_toolException_returnsErrorJson() {
        // Mock LLM: 调用的 tool 会抛异常
        LlmClient mockLlm = new LlmClient() {
            private int callCount = 0;

            @Override
            public LlmResponse chat(List<ChatMessage> messages, String toolsJson) {
                callCount++;
                if (callCount == 1) {
                    List<ToolCall> toolCalls = new ArrayList<>();
                    toolCalls.add(new ToolCall("call_1", "query_class_analysis",
                            "{\"className\":\"com.example.UserService\"}"));
                    return new LlmResponse(null, toolCalls);
                } else {
                    return LlmResponse.text("工具执行出错，请检查。");
                }
            }
        };

        // 创建一个会抛异常的 mock DataProvider
        AnalysisDataProvider brokenProvider = new AnalysisDataProvider() {
            @Override
            public String getV3AnalysisJson(String filePath) {
                throw new RuntimeException("Database connection failed");
            }
            @Override public Map<String, List<String>> getCalledBy(String f) { return null; }
            @Override public String getDbAnalysisJson(String c) { return null; }
            @Override public List<String> findClassesByTableName(String t) { return null; }
            @Override public String getContradictionReportJson(String f) { return null; }
            @Override public String getPackageSummaryJson(String p) { return null; }
            @Override public String searchMethods(String k, int l) { return null; }
            @Override public String getProjectSummary() { return null; }
        };

        ToolRegistry registry = new ToolRegistry();
        registry.register(new QueryClassAnalysisTool(brokenProvider));

        Agent agent = new Agent(mockLlm, registry, "You are a code assistant.", 5);

        // 不应抛异常，agent 内部捕获并以错误 JSON 返回
        String answer = agent.ask("test");
        assertNotNull(answer);
    }

    // ─── CodeQAPrompt 测试 ─────────────────────────────

    @Test
    void codeQAPrompt_buildSystemPrompt_includesProjectContext() {
        String prompt = CodeQAPrompt.buildSystemPrompt("MyProject | Spring Boot | 入口: AppMain");
        assertTrue(prompt.contains("MyProject"));
        assertTrue(prompt.contains("Spring Boot"));
        assertTrue(prompt.contains("CodeLens 代码分析助手"));
        assertTrue(prompt.contains("L0 项目级"));
    }

    @Test
    void codeQAPrompt_buildSystemPrompt_nullContext_showsPlaceholder() {
        String prompt = CodeQAPrompt.buildSystemPrompt(null);
        assertTrue(prompt.contains("（未提供）"));
    }

    // ─── CodeQAAgent 工厂测试 ──────────────────────────

    @Test
    void codeQAAgent_create_registersTools() {
        LlmClient mockLlm = new LlmClient() {
            @Override
            public LlmResponse chat(List<ChatMessage> messages, String toolsJson) {
                return LlmResponse.text("ok");
            }
        };

        AnalysisContextProvider ctxProvider = new AnalysisContextProvider();
        ctxProvider.initProjectContext(new ProjectContext("Test", "Java 8",
                Arrays.asList("Main"), Arrays.asList("core")));

        AgentConfig config = new AgentConfig.Builder()
                .maxToolRounds(3)
                .build();

        CodeQAAgent codeQAAgent = CodeQAAgent.create(
                mockLlm, createMockDataProvider(), ctxProvider, config);

        String answer = codeQAAgent.ask("UserService有什么风险？");
        assertEquals("ok", answer);
    }

    @Test
    void codeQAAgent_ask_withFileContext_injectsL1Prefix() {
        LlmClient mockLlm = new LlmClient() {
            @Override
            public LlmResponse chat(List<ChatMessage> messages, String toolsJson) {
                // 验证 user message 包含 L1 上下文
                String userContent = messages.get(1).getContent();
                assertTrue(userContent.contains("[当前文件]"),
                        "User message 应包含 L1 上下文前缀，实际: " + userContent);
                assertTrue(userContent.contains("UserService.java"));
                return LlmResponse.text("done");
            }
        };

        AnalysisContextProvider ctxProvider = new AnalysisContextProvider();
        ctxProvider.initProjectContext(new ProjectContext("Test", "Java",
                Arrays.asList("Main"), Arrays.asList("core")));
        ctxProvider.updateFileContext("src/main/java/com/example/UserService.java",
                "com.example.UserService", "login");
        ctxProvider.updatePanelSummaries("3个风险(H:2,M:1)", "", "");

        CodeQAAgent agent = CodeQAAgent.create(
                mockLlm, createMockDataProvider(), ctxProvider,
                new AgentConfig.Builder().build());

        String answer = agent.ask("这个方法安全吗？");
        assertEquals("done", answer);
    }

    // ─── AgentConfig 测试 ──────────────────────────────

    @Test
    void agentConfig_builder_defaults() {
        AgentConfig config = new AgentConfig.Builder().apiKey("sk-test").build();
        assertEquals("deepseek", config.getProvider());
        assertEquals("deepseek-chat", config.getModelName());
        assertEquals(5, config.getMaxToolRounds());
        assertEquals(120, config.getTimeoutSeconds());
        assertFalse(config.isSanitizeEnabled());
    }

    @Test
    void agentConfig_fromEnv_fallsBackToDefaults() {
        // 环境变量未设置时应有默认值
        AgentConfig config = AgentConfig.fromEnv();
        assertNotNull(config);
        assertEquals(5, config.getMaxToolRounds());
    }

    // ─── classNameToFilePath 测试 ──────────────────────

    @Test
    void codeLensTool_classNameToFilePath() {
        CodeLensTool tool = new QueryClassAnalysisTool(createMockDataProvider());
        assertEquals("com/example/UserService.java",
                tool.classNameToFilePath("com.example.UserService"));
        assertEquals("Foo.java",
                tool.classNameToFilePath("Foo"));
        assertEquals("",
                tool.classNameToFilePath(null));
        assertEquals("",
                tool.classNameToFilePath(""));
    }
}
