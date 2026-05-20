package com.codelens.common.cache;

/**
 * 缓存配置。
 * <p>
 * 控制缓存行为：是否启用、缓存有效期、最大条目数限制。
 * 两端共享同一配置结构，但实际值由各自初始化。
 */
public class CacheConfig {

    /** 缓存根目录 */
    private final String cacheRoot;

    /** TTL（毫秒），0 表示永不过期 */
    private final long ttlMillis;

    /** 最大缓存条目数，0 表示不限制 */
    private final int maxEntries;

    /** 是否启用缓存 */
    private final boolean enabled;

    public CacheConfig(String cacheRoot, long ttlMillis, int maxEntries) {
        this(cacheRoot, ttlMillis, maxEntries, true);
    }

    public CacheConfig(String cacheRoot, long ttlMillis, int maxEntries, boolean enabled) {
        this.cacheRoot = cacheRoot;
        this.ttlMillis = ttlMillis;
        this.maxEntries = maxEntries;
        this.enabled = enabled;
    }

    public String getCacheRoot() { return cacheRoot; }
    public long getTtlMillis() { return ttlMillis; }
    public int getMaxEntries() { return maxEntries; }
    public boolean isEnabled() { return enabled; }

    /** 永不过期、上限 1000 条、启用 */
    public static CacheConfig defaults(String cacheRoot) {
        return new CacheConfig(cacheRoot, 0, 1000, true);
    }

    /** 禁用缓存 */
    public static CacheConfig disabled() {
        return new CacheConfig(null, 0, 0, false);
    }
}
