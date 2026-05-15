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

    // 默认配置常量
    private static final String DEFAULT_API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final double DEFAULT_TEMPERATURE = 0.1;

    /**
     * 分析接口（使用默认配置）
     * @param apiKey API Key
     * @param systemPrompt 系统提示
     * @param userPrompt 用户提示
     * @return LLM 响应内容
     * @throws Exception 网络或API错误
     */
    public static String analyze(String apiKey, String systemPrompt, String userPrompt) throws Exception {
        return analyze(apiKey, systemPrompt, userPrompt, null, null, Double.NaN);
    }

    /**
     * 分析接口（支持自定义API配置）
     * @param apiKey API Key
     * @param systemPrompt 系统提示
     * @param userPrompt 用户提示
     * @param apiUrl API地址（null或空使用默认值）
     * @param model 模型名（null或空使用默认值）
     * @param temperature 温度参数（Double.NaN表示使用默认值）
     * @return LLM 响应内容
     * @throws Exception 网络或API错误
     */
    public static String analyze(String apiKey, String systemPrompt, String userPrompt,
                                 String apiUrl, String model, double temperature) throws Exception {
        // 参数回退到默认值
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            apiUrl = DEFAULT_API_URL;
        }
        if (model == null || model.trim().isEmpty()) {
            model = DEFAULT_MODEL;
        }
        if (Double.isNaN(temperature)) {
            temperature = DEFAULT_TEMPERATURE;
        }

        String body = "{"
            + "\"model\": \"" + escapeJson(model) + "\","
            + "\"messages\": ["
            + "  {\"role\": \"system\", \"content\": \"" + escapeJson(systemPrompt) + "\"},"
            + "  {\"role\": \"user\", \"content\": \"" + escapeJson(userPrompt) + "\"}"
            + "],"
            + "\"temperature\": " + temperature + ","
            + "\"max_tokens\": 8192"
            + "}";

        URL url = new URL(apiUrl);
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
     * 获取默认API URL
     */
    public static String getDefaultApiUrl() {
        return DEFAULT_API_URL;
    }

    /**
     * 获取默认模型名
     */
    public static String getDefaultModel() {
        return DEFAULT_MODEL;
    }

    /**
     * 获取默认温度参数
     */
    public static double getDefaultTemperature() {
        return DEFAULT_TEMPERATURE;
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
