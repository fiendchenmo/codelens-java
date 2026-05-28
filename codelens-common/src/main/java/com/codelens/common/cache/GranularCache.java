package com.codelens.common.cache;

import com.codelens.common.agent.CacheGranule;
import com.codelens.common.agent.TaskType;

import java.util.List;
import java.util.Optional;

/**
 * 粒度缓存接口，支持按 contentHash 和 taskType 进行缓存读写。
 * <p>
 * 相比 {@link Cache} 的整文件粒度，GranularCache 支持方法级粒度的缓存管理。
 */
public interface GranularCache {

    /**
     * 写入缓存条目。
     */
    void put(CacheGranule granule);

    /**
     * 按 contentHash 查找缓存。
     *
     * @return 命中返回 CacheGranule，否则返回 empty
     */
    Optional<CacheGranule> get(String contentHash);

    /**
     * 按 contentHash 失效单个缓存。
     */
    void invalidate(String contentHash);

    /**
     * 按文件路径失效缓存（子串匹配）。
     */
    void invalidateByFile(String filePath);

    /**
     * 按任务类型列出所有缓存。
     */
    List<CacheGranule> listByType(TaskType taskType);
}
