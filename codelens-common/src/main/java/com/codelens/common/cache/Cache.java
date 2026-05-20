package com.codelens.common.cache;

/**
 * LLM 分析结果缓存接口。
 * <p>
 * 两端共享同一接口定义，存储实现统一在 {@link FileSystemCache} 中。
 * 缓存 key = {@code {sourceHash}_{fileName}}，基于源文件内容 MD5 hash。
 * <p>
 * 默认行为：
 * <ul>
 *   <li>lookup()：命中 + TTL 有效 + model 一致 → 返回结果
 *   <li>save()：写入文件系统，超过 maxEntries 时淘汰最旧条目
 *   <li>lookup() 时自动清理过期条目
 * </ul>
 */
public interface Cache {

    /**
     * 查找缓存。
     *
     * @param filePath   源文件完整路径
     * @param sourceCode 源文件当前内容
     * @param model      使用的模型名（null 表示不校验模型）
     * @return 缓存的条目，未命中或已过期返回 null
     */
    CacheEntry lookup(String filePath, String sourceCode, String model);

    /**
     * 保存缓存。
     *
     * @param filePath   源文件完整路径
     * @param sourceCode 源文件内容
     * @param model      使用的模型名
     * @param result     LLM 返回的 JSON 结果
     */
    void save(String filePath, String sourceCode, String model, String result);

    /**
     * 驱逐指定文件的缓存。
     *
     * @param filePath   源文件路径
     * @param sourceCode 源文件内容（用于生成 hash key）
     */
    void evict(String filePath, String sourceCode);

    /** 清空所有缓存 */
    void clear();

    /** 获取缓存配置 */
    CacheConfig getConfig();
}
