package com.codelens.common.cache;

import com.codelens.common.agent.CacheGranule;
import com.codelens.common.agent.TaskType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * GranularCache 适配器，内部委托给 FileSystemCache 的配置和存储目录。
 * <p>
 * 缓存文件存储在 {@code {cacheRoot}/.codelens/granular/{contentHash}.json}，
 * 与 FileSystemCache 的 {@code .codelens/cache/} 目录互不干扰。
 * <p>
 * 不修改 FileSystemCache 的代码和目录结构。
 */
public class GranularCacheAdapter implements GranularCache {

    private static final Logger LOG = Logger.getLogger(GranularCacheAdapter.class.getName());
    private static final String GRANULAR_SUBDIR = ".codelens/granular";

    private final FileSystemCache delegate;
    private final Path granularRoot;
    private final Gson gson;

    public GranularCacheAdapter(FileSystemCache delegate) {
        this.delegate = delegate;
        this.gson = new Gson();
        if (delegate.getConfig().isEnabled() && delegate.getConfig().getCacheRoot() != null) {
            this.granularRoot = Paths.get(delegate.getConfig().getCacheRoot(), GRANULAR_SUBDIR);
        } else {
            this.granularRoot = null;
        }
    }

    @Override
    public void put(CacheGranule granule) {
        if (granularRoot == null) return;
        try {
            Files.createDirectories(granularRoot);
            Path file = granularRoot.resolve(granule.getContentHash() + ".json");
            Files.write(file, gson.toJson(granule).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warning("Failed to write granule cache: " + e.getMessage());
        }
    }

    @Override
    public Optional<CacheGranule> get(String contentHash) {
        if (granularRoot == null) return Optional.empty();
        Path file = granularRoot.resolve(contentHash + ".json");
        if (!Files.exists(file)) return Optional.empty();
        try {
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            CacheGranule granule = gson.fromJson(json, CacheGranule.class);
            return Optional.ofNullable(granule);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public void invalidate(String contentHash) {
        if (granularRoot == null) return;
        try {
            Path file = granularRoot.resolve(contentHash + ".json");
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.warning("Failed to invalidate granule: " + e.getMessage());
        }
    }

    @Override
    public void invalidateByFile(String filePath) {
        if (granularRoot == null || !Files.exists(granularRoot)) return;
        try {
            List<Path> toDelete = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(granularRoot, "*.json")) {
                for (Path p : stream) {
                    try {
                        String json = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                        CacheGranule granule = gson.fromJson(json, CacheGranule.class);
                        if (granule != null && granule.getInvalidatedBy() != null
                                && granule.getInvalidatedBy().stream().anyMatch(filePath::contains)) {
                            toDelete.add(p);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            for (Path p : toDelete) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            LOG.warning("Failed to invalidate by file: " + e.getMessage());
        }
    }

    @Override
    public List<CacheGranule> listByType(TaskType taskType) {
        if (granularRoot == null || !Files.exists(granularRoot)) return Collections.emptyList();
        List<CacheGranule> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(granularRoot, "*.json")) {
            for (Path p : stream) {
                try {
                    String json = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                    CacheGranule granule = gson.fromJson(json, CacheGranule.class);
                    if (granule != null && granule.getTaskType() == taskType) {
                        result.add(granule);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to list by type: " + e.getMessage());
        }
        return result;
    }

    /**
     * 同步到旧版缓存（空实现，后续补充）。
     */
    public void syncToLegacyCache() {
        // Phase 1 空实现
    }

    /**
     * 获取底层 FileSystemCache 委托对象（测试用）。
     */
    FileSystemCache getDelegate() {
        return delegate;
    }
}
