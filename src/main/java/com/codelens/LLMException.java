package com.codelens;

/**
 * LLM 调用异常类
 * 统一封装所有 LLM 相关的错误
 */
public class LLMException extends RuntimeException {

    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        /** 网络连接错误 */
        NETWORK_ERROR("网络连接错误"),
        /** 请求超时 */
        TIMEOUT("请求超时"),
        /** API 返回错误状态码 */
        API_ERROR("API 错误"),
        /** JSON 解析错误 */
        PARSE_ERROR("解析错误"),
        /** 速率限制 */
        RATE_LIMIT("速率限制"),
        /** 认证错误 */
        AUTH_ERROR("认证错误"),
        /** 未知错误 */
        UNKNOWN("未知错误");

        private final String description;

        ErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private final ErrorType errorType;
    private final int statusCode;
    private final boolean retryable;

    /**
     * 创建 LLMException
     * @param errorType 错误类型
     * @param message 用户友好的错误消息
     */
    public LLMException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.statusCode = -1;
        this.retryable = isRetryableByType(errorType);
    }

    /**
     * 创建 LLMException（带原始异常）
     * @param errorType 错误类型
     * @param message 用户友好的错误消息
     * @param cause 原始异常
     */
    public LLMException(ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.statusCode = -1;
        this.retryable = isRetryableByType(errorType);
    }

    /**
     * 创建 LLMException（带状态码，用于 API 错误）
     * @param errorType 错误类型
     * @param message 用户友好的错误消息
     * @param statusCode HTTP 状态码
     * @param responseBody 响应体内容
     */
    public LLMException(ErrorType errorType, String message, int statusCode, String responseBody) {
        super(buildMessage(errorType, message, statusCode, responseBody));
        this.errorType = errorType;
        this.statusCode = statusCode;
        this.retryable = isRetryableByStatusCode(statusCode);
    }

    /**
     * 创建 LLMException（带状态码和原始异常）
     * @param errorType 错误类型
     * @param message 用户友好的错误消息
     * @param statusCode HTTP 状态码
     * @param responseBody 响应体内容
     * @param cause 原始异常
     */
    public LLMException(ErrorType errorType, String message, int statusCode, String responseBody, Throwable cause) {
        super(buildMessage(errorType, message, statusCode, responseBody), cause);
        this.errorType = errorType;
        this.statusCode = statusCode;
        this.retryable = isRetryableByStatusCode(statusCode);
    }

    private static String buildMessage(ErrorType errorType, String message, int statusCode, String responseBody) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorType.getDescription()).append("] ");
        sb.append(message);
        if (statusCode > 0) {
            sb.append(" (状态码: ").append(statusCode).append(")");
        }
        if (responseBody != null && !responseBody.isEmpty()) {
            // 截断过长的响应体
            String truncated = responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
            sb.append("\n响应内容: ").append(truncated);
        }
        return sb.toString();
    }

    private static boolean isRetryableByType(ErrorType type) {
        return type == ErrorType.NETWORK_ERROR || type == ErrorType.TIMEOUT || type == ErrorType.RATE_LIMIT;
    }

    private static boolean isRetryableByStatusCode(int statusCode) {
        // 5xx 错误、超时(IOException)、429 速率限制可重试
        // 4xx(非429) 不重试
        return statusCode == 429 || (statusCode >= 500 && statusCode < 600);
    }

    /**
     * 获取错误类型
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * 获取 HTTP 状态码（如果有）
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * 判断该错误是否可重试
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * 获取用户友好的简短消息（不含详情）
     */
    public String getUserFriendlyMessage() {
        switch (errorType) {
            case NETWORK_ERROR:
                return "无法连接到 LLM 服务，请检查网络连接";
            case TIMEOUT:
                return "LLM 请求超时，请稍后重试";
            case API_ERROR:
                if (statusCode == 401) {
                    return "API Key 无效或已过期，请检查配置";
                } else if (statusCode == 403) {
                    return "API 访问被拒绝，请检查 API Key 权限";
                } else if (statusCode >= 500) {
                    return "LLM 服务端错误，请稍后重试";
                }
                return "LLM API 调用失败";
            case RATE_LIMIT:
                return "请求过于频繁，请稍后重试";
            case AUTH_ERROR:
                return "API 认证失败，请检查 API Key";
            case PARSE_ERROR:
                return "LLM 返回数据解析失败";
            default:
                return "LLM 调用失败: " + getMessage();
        }
    }
}
