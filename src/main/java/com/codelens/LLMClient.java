package com.codelens;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * DeepSeek LLM 客户端（兼容 OpenAI API）
 */
public class LLMClient {

    public static String analyze(String apiKey, String systemPrompt, String userPrompt) throws Exception {
        String body = "{"
            + "\"model\": \"deepseek-chat\","
            + "\"messages\": ["
            + "  {\"role\": \"system\", \"content\": \"" + escapeJson(systemPrompt) + "\"},"
            + "  {\"role\": \"user\", \"content\": \"" + escapeJson(userPrompt) + "\"}"
            + "],"
            + "\"temperature\": 0.1"
            + "}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.deepseek.com/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return "❌ API 调用失败，状态码: " + response.statusCode() + "\n" + response.body();
        }

        // 简单提取 content 字段
        String body2 = response.body();
        int idx = body2.indexOf("\"content\":");
        if (idx > 0) {
            int start = body2.indexOf("\"", idx + 10) + 1;
            int end = start;
            while (end < body2.length()) {
                if (body2.charAt(end) == '"' && body2.charAt(end - 1) != '\\') break;
                end++;
            }
            return body2.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }

        return body2;
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 32) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
