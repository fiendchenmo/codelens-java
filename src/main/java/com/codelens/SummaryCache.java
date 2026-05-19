package com.codelens;

import com.codelens.common.prompts.SystemPrompt;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * LLM 摘要缓存
 * 同文件内容不变时不重复调用 LLM，节省 API 开销。
 *
 * 缓存结构：{projectRoot}/.codelens/cache/{fileHash}.json
 * 每个缓存文件包含：
 * - source_hash: 源文件内容 MD5
 * - prompt_hash: SystemPrompt.build() 的 MD5（版本号，用于 prompt 更新后失效缓存）
 * - model: 使用的模型名
 * - timestamp: 缓存时间
 * - result: LLM 返回的 JSON 结果
 * 
 * 淘汰机制：
 * - TTL: 7天（过期自动删除）
 * - maxEntries: 1000（超出时删除最旧的缓存文件）
 */
public class SummaryCache {

    private static final String CACHE_DIR = "cache";
    private static final Gson GSON = new GsonBuilder().create();
    
    // TTL: 7天（毫秒）
    private static final long TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L;
    // 最大缓存条目数
    private static final int MAX_ENTRIES = 1000;

    private final Path cacheRoot;
    private final boolean enabled;
    
    // SystemPrompt.build() 的 MD5 缓存，避免重复计算
    private static String promptHash = null;

    public SummaryCache(Path projectRoot, boolean enabled) {
        this.enabled = enabled;
        if (projectRoot != null && enabled) {
            this.cacheRoot = projectRoot.resolve(".codelens").resolve(CACHE_DIR);
        } else {
            this.cacheRoot = null;
        }
    }
    
    /**
     * 获取 SystemPrompt.build() 的 MD5 哈希值
     */
    private synchronized String getPromptHash() {
        if (promptHash == null) {
            promptHash = md5(SystemPrompt.build());
        }
        return promptHash;
    }

    /**
     * 查找缓存
     * @param filePath 源文件路径
     * @param sourceCode 源文件内容
     * @param model 使用的模型名
     * @return 缓存的 LLM 结果，null 表示未命中
     */
    public CacheEntry lookup(String filePath, String sourceCode, String model) {
        if (!enabled || cacheRoot == null) return null;

        try {
            String sourceHash = md5(sourceCode);
            String currentPromptHash = getPromptHash();
            String fileName = Paths.get(filePath).getFileName().toString();
            // 缓存文件名：{sourceHash}_{promptHash}_{fileName}.json (P1-5 修复)
            Path cacheFile = cacheRoot.resolve(sourceHash + "_" + currentPromptHash + "_" + fileName.replace(".java", "") + ".json");
            if (!Files.exists(cacheFile)) return null;

            // 读取缓存
            String content = new String(Files.readAllBytes(cacheFile), "UTF-8");
            CacheEntry entry = parseCacheEntry(content);
            if (entry == null) return null;

            // 校验 source hash 和 model
            if (!sourceHash.equals(entry.sourceHash)) return null;
            if (model != null && !model.equals(entry.model)) return null;
            
            // 校验 prompt hash (P1-5 修复)
            if (!currentPromptHash.equals(entry.promptHash)) return null;
            
            // 检查 TTL 是否过期 (P1-6 修复)
            if (isExpired(entry.timestamp)) {
                // 过期，删除缓存文件
                Files.deleteIfExists(cacheFile);
                return null;
            }

            // 更新 lastModified 时间，实现 LRU
            Files.setLastModifiedTime(cacheFile, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));

            return entry;
        } catch (Exception e) {
            // 缓存读取失败不影响流程
            return null;
        }
    }

    /**
     * 保存缓存
     * @param filePath 源文件路径
     * @param sourceCode 源文件内容
     * @param model 使用的模型名
     * @param result LLM 返回的 JSON 结果
     */
    public void save(String filePath, String sourceCode, String model, String result) {
        if (!enabled || cacheRoot == null) return;

        try {
            Files.createDirectories(cacheRoot);
            String sourceHash = md5(sourceCode);
            String currentPromptHash = getPromptHash();
            String fileName = Paths.get(filePath).getFileName().toString();
            Path cacheFile = cacheRoot.resolve(sourceHash + "_" + currentPromptHash + "_" + fileName.replace(".java", "") + ".json");

            long timestamp = System.currentTimeMillis();

            // 用 Gson 序列化为 JSON
            Map<String, Object> cacheData = new LinkedHashMap<>();
            cacheData.put("source_hash", sourceHash);
            cacheData.put("prompt_hash", currentPromptHash); // P1-5: 保存 prompt hash
            cacheData.put("file", filePath);
            cacheData.put("model", model != null ? model : "unknown");
            cacheData.put("timestamp", timestamp);
            // result 本身是 JSON 字符串，需要解析后放入
            try {
                JsonElement resultElement = JsonParser.parseString(result);
                cacheData.put("result", resultElement);
            } catch (Exception e) {
                // result 不是有效 JSON，当作字符串处理
                cacheData.put("result", result);
            }

            String cacheContent = GSON.toJson(cacheData);
            Files.write(cacheFile, cacheContent.getBytes("UTF-8"));
            
            // P1-6: 检查并执行淘汰
            enforceMaxEntries();
        } catch (Exception e) {
            // 缓存写入失败不影响流程
        }
    }
    
    /**
     * 检查缓存是否过期
     * @param timestamp 缓存时间戳
     * @return true 表示过期
     */
    private boolean isExpired(long timestamp) {
        return (System.currentTimeMillis() - timestamp) > TTL_MILLIS;
    }
    
    /**
     * 强制执行最大条目数限制（LRU 淘汰）
     */
    private void enforceMaxEntries() {
        try {
            File[] files = cacheRoot.toFile().listFiles((dir, name) -> name.endsWith(".json"));
            if (files == null || files.length <= MAX_ENTRIES) {
                return;
            }
            
            // 按 lastModified 时间排序（最旧的在前）
            Arrays.sort(files, (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
            
            // 删除超出部分的旧缓存
            int toDelete = files.length - MAX_ENTRIES;
            for (int i = 0; i < toDelete; i++) {
                files[i].delete();
            }
        } catch (Exception e) {
            // 忽略错误
        }
    }

    /**
     * 清除指定文件的缓存
     */
    public boolean invalidate(String filePath, String sourceCode) {
        if (!enabled || cacheRoot == null) return false;
        try {
            String sourceHash = md5(sourceCode);
            String currentPromptHash = getPromptHash();
            String fileName = Paths.get(filePath).getFileName().toString();
            Path cacheFile = cacheRoot.resolve(sourceHash + "_" + currentPromptHash + "_" + fileName.replace(".java", "") + ".json");
            if (Files.exists(cacheFile)) {
                Files.delete(cacheFile);
                return true;
            }
        } catch (Exception e) { /* ignore */ }
        return false;
    }

    /**
     * 清除所有缓存
     */
    public int clearAll() {
        if (!enabled || cacheRoot == null || !Files.exists(cacheRoot)) return 0;
        int count = 0;
        try {
            File[] files = cacheRoot.toFile().listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    if (f.delete()) count++;
                }
            }
        } catch (Exception e) { /* ignore */ }
        return count;
    }

    /**
     * 列出所有缓存条目
     */
    public List<String> listEntries() {
        List<String> entries = new ArrayList<>();
        if (!enabled || cacheRoot == null || !Files.exists(cacheRoot)) return entries;
        try {
            File[] files = cacheRoot.toFile().listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    entries.add(f.getName());
                }
            }
        } catch (Exception e) { /* ignore */ }
        return entries;
    }

    // ========== 数据类 ==========

    public static class CacheEntry {
        public String sourceHash;
        public String promptHash; // P1-5: 新增字段
        public String file;
        public String model;
        public long timestamp;
        public String result;
    }

    // ========== 内部方法 ==========

    private CacheEntry parseCacheEntry(String content) {
        try {
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();

            CacheEntry entry = new CacheEntry();
            entry.sourceHash = getStringField(obj, "source_hash");
            entry.promptHash = getStringField(obj, "prompt_hash"); // P1-5: 解析 prompt_hash
            entry.file = getStringField(obj, "file");
            entry.model = getStringField(obj, "model");

            entry.timestamp = obj.has("timestamp") ? obj.get("timestamp").getAsLong() : 0;

            // 提取 result 字段（可以是任意 JSON 元素）
            if (obj.has("result")) {
                entry.result = GSON.toJson(obj.get("result"));
            } else {
                entry.result = null;
            }

            return entry;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getStringField(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
