package com.codelens.common.cache;

/**
 * LLM 分析缓存条目。
 * <p>
 * 不可变数据类，记录一次 LLM 调用的缓存：源文件 hash、模型、时间戳、分析结果。
 */
public class CacheEntry {

    private final String sourceHash;
    private final String file;
    private final String model;
    private final long timestamp;
    private final String result;

    public CacheEntry(String sourceHash, String file, String model, long timestamp, String result) {
        this.sourceHash = sourceHash;
        this.file = file;
        this.model = model;
        this.timestamp = timestamp;
        this.result = result;
    }

    /** 源文件内容 hash */
    public String getSourceHash() { return sourceHash; }

    /** 源文件路径 */
    public String getFile() { return file; }

    /** 使用的模型名 */
    public String getModel() { return model; }

    /** 缓存创建时间戳 */
    public long getTimestamp() { return timestamp; }

    /** LLM 返回的 JSON 分析结果 */
    public String getResult() { return result; }
}
