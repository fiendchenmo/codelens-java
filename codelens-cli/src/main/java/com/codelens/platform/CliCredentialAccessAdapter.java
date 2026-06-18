// SYNC_VERSION: 2026-06-18-v1
// IMPACT: LOGIC_CHANGE
package com.codelens.platform;

/**
 * CLI 端凭证访问实现 — 基于环境变量
 * 适用于无 IDE 环境的纯命令行分析场景
 *
 * <p>环境变量映射：
 * <ul>
 *   <li>{@code CODELENS_API_KEY} — API Key</li>
 *   <li>{@code CODELENS_API_URL} — API 地址（可选，有默认值）</li>
 *   <li>{@code CODELENS_MODEL} — 模型名称（可选，有默认值）</li>
 * </ul>
 */
public class CliCredentialAccessAdapter implements CredentialAccessAdapter {

    static final String ENV_API_KEY = "CODELENS_API_KEY";
    static final String ENV_API_URL = "CODELENS_API_URL";
    static final String ENV_MODEL = "CODELENS_MODEL";

    static final String SAVE_NOT_SUPPORTED = "[WARN] CliCredentialAccessAdapter.saveApiKey() 在 CLI 模式下不支持持久化保存，"
            + "请通过环境变量 " + ENV_API_KEY + " 配置";

    static final String DELETE_NOT_SUPPORTED = "[WARN] CliCredentialAccessAdapter.deleteApiKey() 在 CLI 模式下不支持持久化删除，"
            + "请直接清除环境变量 " + ENV_API_KEY;

    @Override
    public String getApiKey(String service) {
        // 目前只有 codelens 服务，直接读 CODELENS_API_KEY
        String key = System.getenv(ENV_API_KEY);
        if (key != null && !key.isEmpty()) {
            return key;
        }
        // 按 service 名尝试：CODELENS_API_KEY_<SERVICE>
        String serviceKey = System.getenv(ENV_API_KEY + "_" + service.toUpperCase());
        return (serviceKey != null && !serviceKey.isEmpty()) ? serviceKey : null;
    }

    @Override
    public void saveApiKey(String service, String key) {
        System.out.println(SAVE_NOT_SUPPORTED);
    }

    @Override
    public void deleteApiKey(String service) {
        System.out.println(DELETE_NOT_SUPPORTED);
    }

    @Override
    public boolean hasApiKey(String service) {
        return getApiKey(service) != null;
    }
}
