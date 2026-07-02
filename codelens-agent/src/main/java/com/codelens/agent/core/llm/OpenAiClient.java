package com.codelens.agent.core.llm;

import com.codelens.agent.core.message.ChatMessage;
import com.codelens.agent.core.tool.ToolCall;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 LLM 客户端。JDK 1.8 HttpURLConnection + Gson，零外部 HTTP 依赖。
 * <p>
 * 支持所有 OpenAI API 兼容的 Provider：
 * <ul>
 *   <li>DeepSeek: https://api.deepseek.com</li>
 *   <li>智谱 GLM: https://open.bigmodel.cn/api/paas/v4</li>
 *   <li>OpenAI: https://api.openai.com/v1</li>
 *   <li>Ollama / vLLM 等自部署模型</li>
 * </ul>
 * </p>
 *
 * <p>Claude Code 模式：Client 只负责 HTTP 通信 + JSON 解析，
 * 不缓存、不重试（重试由 Agent 层处理），不参与 Tool 执行。</p>
 */
public class OpenAiClient implements LlmClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private static final Gson gson = new Gson();

    /**
     * 创建 OpenAI 兼容客户端。
     * @param baseUrl API endpoint（如 https://api.deepseek.com）
     * @param apiKey  API Key
     * @param model   模型名（如 deepseek-chat）
     */
    public OpenAiClient(String baseUrl, String apiKey, String model) {
        this(baseUrl, apiKey, model, 30000, 120000);
    }

    /**
     * 创建 OpenAI 兼容客户端（指定超时）。
     * @param baseUrl          API endpoint
     * @param apiKey           API Key
     * @param model            模型名
     * @param connectTimeoutMs 连接超时（毫秒）
     * @param readTimeoutMs    读取超时（毫秒）
     */
    public OpenAiClient(String baseUrl, String apiKey, String model,
                        int connectTimeoutMs, int readTimeoutMs) {
        // 确保 baseUrl 不以 / 结尾，后续统一拼接 /chat/completions
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public LlmResponse chat(List<ChatMessage> messages, String toolsJson) {
        try {
            String requestBody = buildRequestBody(messages, toolsJson);
            String responseBody = httpPost(requestBody);
            return parseResponse(responseBody);
        } catch (IOException e) {
            throw new RuntimeException("LLM API 调用失败: " + e.getMessage(), e);
        }
    }

    // ─── 请求构造 ──────────────────────────────────────

    private String buildRequestBody(List<ChatMessage> messages, String toolsJson) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("model", model);

        // messages: 逐个序列化（子类覆盖了 toJson()）
        List<Object> msgList = new ArrayList<Object>();
        for (ChatMessage msg : messages) {
            String msgJson = msg.toJson();
            msgList.add(gson.fromJson(msgJson, Object.class));
        }
        body.put("messages", msgList);

        // tools（可选）
        if (toolsJson != null && !toolsJson.isEmpty()) {
            Object tools = gson.fromJson(toolsJson, Object.class);
            body.put("tools", tools);
        }

        // temperature: 工具调用建议低温度以获得可预测行为
        body.put("temperature", 0.1);

        return gson.toJson(body);
    }

    // ─── HTTP 通信 ─────────────────────────────────────

    private String httpPost(String requestBody) throws IOException {
        String url = baseUrl + "/chat/completions";
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(connectTimeoutMs);
            conn.setReadTimeout(readTimeoutMs);

            // 写请求体
            OutputStream os = conn.getOutputStream();
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            // 读响应
            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                return sb.toString();
            } else {
                // 读错误响应体
                String errorBody = "";
                try {
                    BufferedReader errReader = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                    StringBuilder errSb = new StringBuilder();
                    String errLine;
                    while ((errLine = errReader.readLine()) != null) {
                        errSb.append(errLine);
                    }
                    errReader.close();
                    errorBody = errSb.toString();
                } catch (Exception ignored) {
                }
                throw new IOException("HTTP " + code + ": " + errorBody);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // ─── 响应解析 ──────────────────────────────────────

    private LlmResponse parseResponse(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();

        // choices[0].message
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.size() == 0) {
            return LlmResponse.text("");
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");

        // content
        String content = null;
        JsonElement contentEl = message.get("content");
        if (contentEl != null && !contentEl.isJsonNull()) {
            content = contentEl.getAsString();
        }

        // tool_calls
        List<ToolCall> toolCalls = null;
        JsonElement tcEl = message.get("tool_calls");
        if (tcEl != null && tcEl.isJsonArray()) {
            JsonArray tcArr = tcEl.getAsJsonArray();
            toolCalls = new ArrayList<ToolCall>();
            for (int i = 0; i < tcArr.size(); i++) {
                JsonObject tc = tcArr.get(i).getAsJsonObject();
                String id = getStringSafe(tc, "id");
                JsonObject func = tc.getAsJsonObject("function");
                String name = getStringSafe(func, "name");
                String arguments = getStringSafe(func, "arguments");
                toolCalls.add(new ToolCall(id, name, arguments));
            }
        }

        return new LlmResponse(content, toolCalls);
    }

    private static String getStringSafe(JsonObject obj, String field) {
        JsonElement el = obj.get(field);
        if (el != null && el.isJsonPrimitive()) {
            return el.getAsString();
        }
        return "";
    }
}
