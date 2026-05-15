package com.codelens;

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
 * - model: 使用的模型名
 * - timestamp: 缓存时间
 * - result: LLM 返回的 JSON 结果
 */
public class SummaryCache {

    private static final String CACHE_DIR = "cache";
    private final Path cacheRoot;
    private final boolean enabled;

    public SummaryCache(Path projectRoot, boolean enabled) {
        this.enabled = enabled;
        if (projectRoot != null && enabled) {
            this.cacheRoot = projectRoot.resolve(".codelens").resolve(CACHE_DIR);
        } else {
            this.cacheRoot = null;
        }
    }

    /**
     * 查找缓存
     * @param filePath 源文件路径
     *param sourceCode 源文件内容
     * @param model 使用的模型名
     * @return 缓存的 LLM 结果，null 表示未命中
     */
    public CacheEntry lookup(String filePath, String sourceCode, String model) {
        if (!enabled || cacheRoot == null) return null;

        try {
            String sourceHash = md5(sourceCode);
            String fileName = Paths.get(filePath).getFileName().toString();
            // 缓存文件名：{sourceHash}_{fileName}.json
            Path cacheFile = cacheRoot.resolve(sourceHash + "_" + fileName.replace(".java", "") + ".json");
            if (!Files.exists(cacheFile)) return null;

            // 读取缓存
            String content = new String(Files.readAllBytes(cacheFile), "UTF-8");
            CacheEntry entry = parseCacheEntry(content);
            if (entry == null) return null;

            // 校验 source hash 和 model
            if (!sourceHash.equals(entry.sourceHash)) return null;
            if (model != null && !model.equals(entry.model)) return null;

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
            String fileName = Paths.get(filePath).getFileName().toString();
            Path cacheFile = cacheRoot.resolve(sourceHash + "_" + fileName.replace(".java", "") + ".json");

            long timestamp = System.currentTimeMillis();
            String cacheContent = "{\n"
                + "  \"source_hash\": \"" + sourceHash + "\",\n"
                + "  \"file\": \"" + escapeJson(filePath) + "\",\n"
                + "  \"model\": \"" + escapeJson(model != null ? model : "unknown") + "\",\n"
                + "  \"timestamp\": " + timestamp + ",\n"
                + "  \"result\": " + result + "\n"
                + "}";

            Files.write(cacheFile, cacheContent.getBytes("UTF-8"));
        } catch (Exception e) {
            // 缓存写入失败不影响流程
        }
    }

    /**
     * 清除指定文件的缓存
     */
    public boolean invalidate(String filePath, String sourceCode) {
        if (!enabled || cacheRoot == null) return false;
        try {
            String sourceHash = md5(sourceCode);
            String fileName = Paths.get(filePath).getFileName().toString();
            Path cacheFile = cacheRoot.resolve(sourceHash + "_" + fileName.replace(".java", "") + ".json");
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
        public String file;
        public String model;
        public long timestamp;
        public String result;
    }

    // ========== 内部方法 ==========

    private CacheEntry parseCacheEntry(String content) {
        try {
            CacheEntry entry = new CacheEntry();
            entry.sourceHash = extractJsonStringField(content, "source_hash");
            entry.file = extractJsonStringField(content, "file");
            entry.model = extractJsonStringField(content, "model");

            String tsStr = extractJsonNumberField(content, "timestamp");
            entry.timestamp = tsStr != null ? Long.parseLong(tsStr) : 0;

            // 提取 result 字段（整个 JSON 对象）
            entry.result = extractResultField(content);
            return entry;
        } catch (Exception e) {
            return null;
        }
    }

    static String extractJsonStringField(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int idx = json.indexOf(searchKey);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + searchKey.length());
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        start++;
        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append(json.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '"') break;
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    static String extractJsonNumberField(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int idx = json.indexOf(searchKey);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + searchKey.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        return json.substring(start, end);
    }

    /**
     * 提取 "result": {...} 中的完整 JSON 对象
     */
    static String extractResultField(String json) {
        String searchKey = "\"result\"";
        int idx = json.indexOf(searchKey);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + searchKey.length());
        if (colon < 0) return null;
        int objStart = json.indexOf('{', colon);
        if (objStart < 0) return null;

        int depth = 0;
        int i = objStart;
        boolean inString = false;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && inString) { i += 2; continue; }
            if (c == '"') inString = !inString;
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return json.substring(objStart, i + 1);
                    }
                }
            }
            i++;
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

    static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
}
