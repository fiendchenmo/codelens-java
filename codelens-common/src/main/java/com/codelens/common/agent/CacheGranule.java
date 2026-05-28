package com.codelens.common.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 缓存粒度数据类，基于内容 hash 标识单个缓存条目。
 * <p>
 * 不可变对象，通过全参构造或 Builder 模式创建。
 */
public class CacheGranule {

    private final TaskType taskType;
    private final String version;
    private final String contentType;
    private final String contentHash;
    private final String modelId;
    private final String output;
    private final long createdAt;
    private final List<String> invalidatedBy;

    public CacheGranule(TaskType taskType, String version, String contentType,
                        String contentHash, String modelId, String output,
                        long createdAt, List<String> invalidatedBy) {
        this.taskType = taskType;
        this.version = version;
        this.contentType = contentType;
        this.contentHash = contentHash;
        this.modelId = modelId;
        this.output = output;
        this.createdAt = createdAt;
        this.invalidatedBy = invalidatedBy;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public String getVersion() {
        return version;
    }

    public String getContentType() {
        return contentType;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getModelId() {
        return modelId;
    }

    public String getOutput() {
        return output;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public List<String> getInvalidatedBy() {
        return invalidatedBy;
    }

    /**
     * 生成缓存 key：SHA-256(inputContent + taskType.name())。
     *
     * @param inputContent 输入内容（如源码）
     * @param taskType     任务类型
     * @return 64 位小写 hex SHA-256
     */
    public static String generateKey(String inputContent, TaskType taskType) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((inputContent + taskType.name()).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 is guaranteed by Java spec, should never happen", e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private TaskType taskType;
        private String version;
        private String contentType;
        private String contentHash;
        private String modelId;
        private String output;
        private long createdAt;
        private List<String> invalidatedBy;

        Builder() {}

        public Builder taskType(TaskType taskType) { this.taskType = taskType; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder contentType(String contentType) { this.contentType = contentType; return this; }
        public Builder contentHash(String contentHash) { this.contentHash = contentHash; return this; }
        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder output(String output) { this.output = output; return this; }
        public Builder createdAt(long createdAt) { this.createdAt = createdAt; return this; }
        public Builder invalidatedBy(List<String> invalidatedBy) { this.invalidatedBy = invalidatedBy; return this; }

        public CacheGranule build() {
            return new CacheGranule(taskType, version, contentType, contentHash,
                    modelId, output, createdAt, invalidatedBy);
        }
    }
}
