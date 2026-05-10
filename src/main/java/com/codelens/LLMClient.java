package com.codelens;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * DeepSeek LLM 客户端（兼容 OpenAI API）
 * Java 8 兼容版本，使用 HttpURLConnection
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

        URL url = new URL("https://api.deepseek.com/v1/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.close();

        int statusCode = conn.getResponseCode();
        if (statusCode != 200) {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return "❌ API 调用失败，状态码: " + statusCode + "\n" + sb.toString();
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        String responseBody = sb.toString();

        // 提取 content 字段
        int idx = responseBody.indexOf("\"content\":");
        if (idx > 0) {
            int start = responseBody.indexOf("\"", idx + 10) + 1;
            int end = start;
            while (end < responseBody.length()) {
                if (responseBody.charAt(end) == '"' && responseBody.charAt(end - 1) != '\\') break;
                end++;
            }
            return responseBody.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }

        return responseBody;
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
