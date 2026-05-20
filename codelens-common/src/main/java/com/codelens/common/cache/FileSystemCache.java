package com.codelens.common.cache;

import com.codelens.common.prompts.SystemPrompt;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 文件系统缓存实现。
 * <p>
 * 缓存存储在 {@code {cacheRoot}/.codelens/cache/{key}.json}。
 * <p>
 * 特性：
 * <ul>
 *   <li>promptHash：prompt 规则变更后缓存自动失效
 *   <li>model 维度：不同模型不会命中同一缓存
 *   <li>TTL：lookup 时检查，过期自动驱逐
 *   <li>maxEntries：save 时检查，超出按最后访问时间淘汰最旧条目
 *   <li>基于 Gson 序列化，日志友好的 JSON 格式
 *   <li>线程安全（synchronized 方法，IDE 插件单线程调用场景）
 * </ul>
 */
public class FileSystemCache implements Cache {

    private static final Logger LOG = Logger.getLogger(FileSystemCache.class.getName());
    private static final String CACHE_SUBDIR = ".codelens/cache";
    private static final String HASH_ALGO = "MD5";

    private final Path cacheRoot;
    private final CacheConfig config;
    private final Gson gson;

    /** 缓存的 prompt hash（延迟计算） */
    private String promptHash;

    /**
     * @param config 缓存配置（包含 root、TTL、maxEntries、enabled）
     */
    public FileSystemCache(CacheConfig config) {
        this.config = config;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        if (config.isEnabled() && config.getCacheRoot() != null) {
            this.cacheRoot = Paths.get(config.getCacheRoot(), CACHE_SUBDIR);
        } else {
            this.cacheRoot = null;
        }
    }

    /**
     * 获取 SystemPrompt.build() 的 MD5 hash。
     * prompt 规则变更后 hash 会变，缓存的 key 随之改变 → 自然失效。
     */
    private String getPromptHash() {
        if (promptHash == null) {
            try {
                MessageDigest md = MessageDigest.getInstance(HASH_ALGO);
                byte[] digest = md.digest(SystemPrompt.build().getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(32);
                for (byte b : digest) {
                    sb.append(String.format("%02x", b & 0xff));
                }
                promptHash = sb.toString();
            } catch (NoSuchAlgorithmException e) {
                promptHash = "";
            }
        }
        return promptHash;
    }

    @Override
    public synchronized CacheEntry lookup(String filePath, String sourceCode, String model) {
        if (!config.isEnabled() || cacheRoot == null) return null;
        if (sourceCode == null) return null;

        try {
            String sourceHash = CacheKeyGenerator.generateHash(sourceCode);
            String ph = getPromptHash();
            String fileName = CacheKeyGenerator.generateFileName(sourceCode, ph, filePath, model);
            Path cacheFile = cacheRoot.resolve(fileName);
            if (!Files.exists(cacheFile)) return null;

            // 解析缓存文件
            String content = new String(Files.readAllBytes(cacheFile), StandardCharsets.UTF_8);
            CacheEntry entry = parseEntry(content, filePath);
            if (entry == null) return null;

            // hash 校验 — 内容未变
            if (!sourceHash.equals(entry.getSourceHash())) return null;

            // prompt hash 校验 — prompt 规则未变
            if (!ph.equals(entry.getPromptHash())) return null;

            // 模型校验 — 模型未变
            if (model != null && !model.equals(entry.getModel())) return null;

            // TTL 校验 — 未过期
            if (isExpired(entry)) {
                Files.deleteIfExists(cacheFile);
                return null;
            }

            // 命中：更新文件最后修改时间（LRU 淘汰策略用）
            Files.setLastModifiedTime(cacheFile, FileTime.fromMillis(System.currentTimeMillis()));

            return entry;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public synchronized void save(String filePath, String sourceCode, String model, String result) {
        if (!config.isEnabled() || cacheRoot == null) return;
        if (sourceCode == null || result == null) return;

        try {
            Files.createDirectories(cacheRoot);

            String sourceHash = CacheKeyGenerator.generateHash(sourceCode);
            String ph = getPromptHash();
            String fileName = CacheKeyGenerator.generateFileName(sourceCode, ph, filePath, model);
            Path cacheFile = cacheRoot.resolve(fileName);
            long timestamp = System.currentTimeMillis();

            // 构建 JSON 存储
            JsonObject obj = new JsonObject();
            obj.addProperty("source_hash", sourceHash);
            obj.addProperty("prompt_hash", ph);
            obj.addProperty("file", filePath);
            obj.addProperty("model", model != null ? model : "unknown");
            obj.addProperty("timestamp", timestamp);
            obj.addProperty("result", result);

            Files.write(cacheFile, gson.toJson(obj).getBytes(StandardCharsets.UTF_8));

            // maxEntries 检查 — 淘汰最旧的
            if (config.getMaxEntries() > 0) {
                evictStaleEntries();
            }
        } catch (Exception e) {
            // 缓存写入失败不影响主流程
        }
    }

    @Override
    public synchronized void evict(String filePath, String sourceCode) {
        if (cacheRoot == null || sourceCode == null) return;
        try {
            // evict 时 model 未知，遍历匹配
            String ph = getPromptHash();
            String combined = CacheKeyGenerator.generateCombinedHash(sourceCode, ph);
            String baseName = extractFileName(filePath);
            String prefix = combined + "_" + baseName + "_";
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(cacheRoot, prefix + "*.json")) {
                for (Path p : stream) {
                    Files.deleteIfExists(p);
                }
            }
        } catch (Exception e) {
            LOG.warning("Failed to evict cache file: " + e.getMessage());
        }
    }

    @Override
    public synchronized void clear() {
        if (cacheRoot == null) return;
        try {
            if (Files.exists(cacheRoot)) {
                Files.walkFileTree(cacheRoot, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        if (!dir.equals(cacheRoot)) Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (Exception e) {
            LOG.warning("Failed to clear cache: " + e.getMessage());
        }
    }

    @Override
    public CacheConfig getConfig() {
        return config;
    }

    // ========== 内部方法 ==========

    /** 检查条目是否过期 */
    private boolean isExpired(CacheEntry entry) {
        if (config.getTtlMillis() <= 0) return false;
        return System.currentTimeMillis() - entry.getTimestamp() > config.getTtlMillis();
    }

    /** 超过 maxEntries 时按最后访问时间淘汰最旧条目 */
    private void evictStaleEntries() throws IOException {
        if (!Files.exists(cacheRoot)) return;
        int max = config.getMaxEntries();

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cacheRoot, "*.json")) {
            for (Path p : stream) files.add(p);
        }

        if (files.size() <= max) return;

        // 按文件 lastModifiedTime 排序（LRU：最久未访问的先淘汰）
        files.sort(Comparator.comparingLong(f -> {
            try {
                return Files.getLastModifiedTime(f).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }));

        int toEvict = files.size() - max;
        for (int i = 0; i < toEvict; i++) {
            Files.deleteIfExists(files.get(i));
        }
    }

    /** 将磁盘上的 JSON 反序列化为 CacheEntry */
    private CacheEntry parseEntry(String json, String filePath) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            String sourceHash = getString(obj, "source_hash");
            String promptHash = getString(obj, "prompt_hash");
            String file = getString(obj, "file");
            if (file == null) file = filePath;
            String model = getString(obj, "model");
            long timestamp = getLong(obj, "timestamp", 0L);
            String result = getString(obj, "result");

            if (sourceHash == null || result == null) return null;

            return new CacheEntry(sourceHash, promptHash, file, model, timestamp, result);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractFileName(String filePath) {
        String name = filePath;
        int sep = name.lastIndexOf('/');
        if (sep < 0) sep = name.lastIndexOf('\\');
        if (sep >= 0) name = name.substring(sep + 1);
        if (name.endsWith(".java")) {
            name = name.substring(0, name.length() - 5);
        }
        return name;
    }

    private static String getString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && !el.isJsonNull() ? el.getAsString() : null;
    }

    private static long getLong(JsonObject obj, String key, long def) {
        JsonElement el = obj.get(key);
        return el != null && !el.isJsonNull() ? el.getAsLong() : def;
    }
}
