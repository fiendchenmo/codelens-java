// SYNC_VERSION: 2026-05-26-v1
// 维护方:喵呜(CLI端)
// 说明:Schema版本枚举，定义CodeMetaData输出格式的版本

package com.codelens.common.models;

/**
 * Schema版本枚举
 * 
 * @since 0.2.9
 */
public enum SchemaVersion {
    /**
     * V2版本 — 8顶层字段（旧版）
     * 字段：summary, design_intent, class_analysis, dependencies, risks, 
     *       keyMethods, framework_integration, architecture_issues
     */
    V2("v2", 2),
    
    /**
     * V3版本 — 4顶层字段（新版）
     * 字段：summary, framework, fields, methods
     */
    V3("v3", 3);

    private final String version;
    private final int majorVersion;

    SchemaVersion(String version, int majorVersion) {
        this.version = version;
        this.majorVersion = majorVersion;
    }

    public String getVersion() {
        return version;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    /**
     * 从字符串解析版本
     * @param version 版本字符串（"v2"/"v3"或"2"/"3"）
     * @return 对应版本，未知版本返回null
     */
    public static SchemaVersion fromString(String version) {
        if (version == null) {
            return null;
        }
        String v = version.toLowerCase().trim();
        for (SchemaVersion sv : values()) {
            if (sv.version.equals(v) || String.valueOf(sv.majorVersion).equals(v)) {
                return sv;
            }
        }
        return null;
    }

    /**
     * 检查是否为最新版本
     */
    public boolean isLatest() {
        return this == V3;
    }
}
