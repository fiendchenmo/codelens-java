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
            + "\"model\": \"deepseek-v4-flash\","
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

        // 使用健壮的状态机解析 JSON content 字段
        return extractContentField(responseBody);
    }

    /**
     * 使用状态机解析 JSON 中的 "content" 字段
     * 正确处理所有转义序列：\\", \\\\, \\n, \\r, \\t, \\uXXXX
     */
    static String extractContentField(String json) {
        // 找到 "content" key
        int idx = json.indexOf("\"content\"");
        if (idx < 0) return json;

        // 跳到冒号后面的值
        int colon = json.indexOf(':', idx);
        if (colon < 0) return json;

        // 找到值的起始引号
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return json;
        start++; // 跳过起始引号

        // 状态机解析字符串内容
        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\') {
                // 转义序列
                if (i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    switch (next) {
                        case '"':  sb.append('"');  i += 2; break;
                        case '\\': sb.append('\\'); i += 2; break;
                        case 'n':  sb.append('\n'); i += 2; break;
                        case 'r':  sb.append('\r'); i += 2; break;
                        case 't':  sb.append('\t'); i += 2; break;
                        case 'u':  // Unicode 转义
                            if (i + 5 < json.length()) {
                                String hex = json.substring(i + 2, i + 6);
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 6;
                            } else { i += 2; }
                            break;
                        default: sb.append(next); i += 2; break;
                    }
                } else { i++; }
            } else if (c == '"') {
                // 字符串结束
                break;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    static String escapeJson(String s) {
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
