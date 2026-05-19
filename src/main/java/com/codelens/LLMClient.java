package com.codelens;

import com.codelens.common.utils.StringUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Logger;

/**
 * DeepSeek LLM 客户端（兼容 OpenAI API）
 * Java 8 兼容版本，使用 HttpURLConnection
 * 
 * 特性：
 * - 指数退避重试机制（最多2次重试，共3次尝试）
 * - 统一异常处理（LLMException）
 * - 支持可重试错误自动重试
 */
public class LLMClient {

    private static final Logger LOGGER = Logger.getLogger(LLMClient.class.getName());

    // 默认配置常量
    private static final String DEFAULT_API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final double DEFAULT_TEMPERATURE = 0.1;

    // 重试配置
    private static final int MAX_RETRIES = 2;  // 最多重试2次
    private static final int BASE_DELAY_MS = 2000;  // 基础延迟2秒

    /**
     * 分析接口（使用默认配置）
     * @param apiKey API Key
     * @param systemPrompt 系统提示
     * @param userPrompt 用户提示
     * @return LLM 响应内容
     * @throws LLMException 网络或API错误
     */
    public static String analyze(String apiKey, String systemPrompt, String userPrompt) {
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
     * @throws LLMException 网络或API错误
     */
    public static String analyze(String apiKey, String systemPrompt, String userPrompt,
                                 String apiUrl, String model, double temperature) {
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
            + "\"model\": \"" + StringUtil.escapeJson(model) + "\","
            + "\"messages\": ["
            + "  {\"role\": \"system\", \"content\": \"" + StringUtil.escapeJson(systemPrompt) + "\"},"
            + "  {\"role\": \"user\", \"content\": \"" + StringUtil.escapeJson(userPrompt) + "\"}\n"
            + "],"
            + "\"temperature\": " + temperature + ","
            + "\"max_tokens\": 8192"
            + "}";

        // 带重试的请求
        return executeWithRetry(apiUrl, apiKey, body);
    }

    /**
     * 带指数退避重试的执行方法
     * @param apiUrl API 地址
     * @param apiKey API Key
     * @param body 请求体
     * @return LLM 响应内容
     * @throws LLMException 所有失败情况
     */
    private static String executeWithRetry(String apiUrl, String apiKey, String body) {
        int attempt = 0;
        LLMException lastException = null;

        while (attempt <= MAX_RETRIES) {
            try {
                return doRequest(apiUrl, apiKey, body);
            } catch (LLMException e) {
                lastException = e;

                // 检查是否可重试
                if (!e.isRetryable() || attempt >= MAX_RETRIES) {
                    // 不可重试的错误或已达最大重试次数，直接抛出
                    throw e;
                }

                // 计算延迟时间（指数退避）
                int delayMs = BASE_DELAY_MS * (int) Math.pow(2, attempt);
                attempt++;

                LOGGER.warning(String.format(
                    "LLM request failed (attempt %d/%d), retrying in %d seconds... Error: %s",
                    attempt, MAX_RETRIES + 1, delayMs / 1000, e.getUserFriendlyMessage()
                ));

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LLMException(LLMException.ErrorType.UNKNOWN, "请求被中断", ie);
                }
            } catch (Exception e) {
                // 未知异常，包装后抛出
                throw new LLMException(LLMException.ErrorType.UNKNOWN, "LLM 调用失败", e);
            }
        }

        // 理论上不会走到这里，但为了安全抛出最后一个异常
        throw lastException != null ? lastException 
            : new LLMException(LLMException.ErrorType.UNKNOWN, "LLM 调用失败");
    }

    /**
     * 执行 HTTP 请求
     * @param apiUrl API 地址
     * @param apiKey API Key
     * @param body 请求体
     * @return LLM 响应内容
     * @throws LLMException 失败时
     */
    private static String doRequest(String apiUrl, String apiKey, String body) throws LLMException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(apiUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);

            // 发送请求
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();

            // 读取响应
            int statusCode = conn.getResponseCode();
            String responseBody = readResponseBody(conn, statusCode);

            // 检查状态码
            if (statusCode != 200) {
                handleErrorStatusCode(statusCode, responseBody);
            }

            // 解析响应
            return extractContentField(responseBody);

        } catch (java.net.SocketTimeoutException e) {
            throw new LLMException(LLMException.ErrorType.TIMEOUT, 
                "LLM 请求超时（连接30秒/读取120秒）", e);
        } catch (java.net.UnknownHostException e) {
            throw new LLMException(LLMException.ErrorType.NETWORK_ERROR, 
                "无法解析 LLM 服务域名，请检查网络连接", e);
        } catch (java.net.ConnectException e) {
            throw new LLMException(LLMException.ErrorType.NETWORK_ERROR, 
                "无法连接到 LLM 服务，请检查网络或代理设置", e);
        } catch (IOException e) {
            // 其他 IO 错误，可能是超时或其他网络问题
            throw new LLMException(LLMException.ErrorType.NETWORK_ERROR, 
                "网络通信错误: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 读取响应体
     * @param conn HTTP 连接
     * @param statusCode 状态码
     * @return 响应体字符串
     */
    private static String readResponseBody(HttpURLConnection conn, int statusCode) throws LLMException {
        try {
            BufferedReader br;
            if (statusCode >= 400) {
                br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
            } else {
                br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            }
            
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
            return sb.toString();
        } catch (IOException e) {
            throw new LLMException(LLMException.ErrorType.NETWORK_ERROR, 
                "读取响应失败", e);
        }
    }

    /**
     * 处理错误状态码
     * @param statusCode HTTP 状态码
     * @param responseBody 响应体
     * @throws LLMException 根据状态码抛出对应异常
     */
    private static void handleErrorStatusCode(int statusCode, String responseBody) throws LLMException {
        LLMException.ErrorType errorType;
        String message;

        switch (statusCode) {
            case 401:
                throw new LLMException(LLMException.ErrorType.AUTH_ERROR, 
                    "API Key 无效或已过期", statusCode, responseBody);
            case 403:
                throw new LLMException(LLMException.ErrorType.AUTH_ERROR, 
                    "API 访问被拒绝，请检查 API Key 权限", statusCode, responseBody);
            case 429:
                throw new LLMException(LLMException.ErrorType.RATE_LIMIT, 
                    "请求过于频繁（速率限制）", statusCode, responseBody);
            case 400:
                throw new LLMException(LLMException.ErrorType.API_ERROR, 
                    "请求参数错误", statusCode, responseBody);
            default:
                if (statusCode >= 500) {
                    errorType = LLMException.ErrorType.API_ERROR;
                    message = "LLM 服务端错误";
                } else {
                    errorType = LLMException.ErrorType.API_ERROR;
                    message = "API 返回错误状态码";
                }
                throw new LLMException(errorType, message, statusCode, responseBody);
        }
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
        try {
            // 找到 "content" key
            int idx = json.indexOf("\"content\"");
            if (idx < 0) {
                throw new LLMException(LLMException.ErrorType.PARSE_ERROR, 
                    "响应中未找到 content 字段", -1, json);
            }

            // 跳到冒号后面的值
            int colon = json.indexOf(':', idx);
            if (colon < 0) {
                throw new LLMException(LLMException.ErrorType.PARSE_ERROR, 
                    "响应 JSON 格式错误：未找到冒号", -1, json);
            }

            // 找到值的起始引号
            int start = json.indexOf('"', colon + 1);
            if (start < 0) {
                throw new LLMException(LLMException.ErrorType.PARSE_ERROR, 
                    "响应 JSON 格式错误：未找到内容起始引号", -1, json);
            }
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
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMException(LLMException.ErrorType.PARSE_ERROR, 
                "解析 LLM 响应失败", e);
        }
    }
}
