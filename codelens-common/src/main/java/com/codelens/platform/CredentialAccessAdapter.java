// SYNC_VERSION: 2026-06-17-v1
// IMPACT: LOGIC_CHANGE
package com.codelens.platform;

/**
 * 凭证访问抽象层 — 替代 IDEA PasswordSafe
 * 插件端实现用 PasswordSafe，CLI 端实现用环境变量/配置文件
 */
public interface CredentialAccessAdapter {

    /**
     * 读取 API Key
     *
     * @param service 服务名称（如 "codelens", "openai"）
     * @return API Key，不存在返回 null
     */
    String getApiKey(String service);

    /**
     * 保存 API Key
     *
     * @param service 服务名称
     * @param key     API Key 值
     */
    void saveApiKey(String service, String key);

    /**
     * 删除 API Key
     */
    void deleteApiKey(String service);

    /**
     * 检查是否已配置 API Key
     */
    boolean hasApiKey(String service);
}
