package com.codelens.common.profile;

/**
 * 画像元信息。
 */
public class ProfileMeta {
    private String projectHash;      // 项目内容哈希，用于失效检测
    private long inferredAt;         // 推断时间戳
    private int classCount;          // 扫描类数
    private String version;          // 画像格式版本

    public ProfileMeta() {}

    public ProfileMeta(String projectHash, long inferredAt, int classCount, String version) {
        this.projectHash = projectHash;
        this.inferredAt = inferredAt;
        this.classCount = classCount;
        this.version = version;
    }

    public String getProjectHash() { return projectHash; }
    public void setProjectHash(String projectHash) { this.projectHash = projectHash; }
    public long getInferredAt() { return inferredAt; }
    public void setInferredAt(long inferredAt) { this.inferredAt = inferredAt; }
    public int getClassCount() { return classCount; }
    public void setClassCount(int classCount) { this.classCount = classCount; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
