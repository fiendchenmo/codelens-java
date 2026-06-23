// SYNC_VERSION: 2026-06-23-v1
// IMPACT: SCHEMA_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.db.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询返回：表共享记录。
 * <p>
 * 描述同一张数据库表被哪些 Mapper 接口操作，
 * 用于跨模块共享表检测和 C5 矛盾规则。
 * </p>
 */
public class TableSharingRecord {

    /** 数据库表名 */
    private String tableName;

    /** 操作该表的所有 Mapper 接口全限定名 */
    private List<String> mapperInterfaces;

    /** 操作该表的 Mapper 数量 */
    private int mapperCount;

    /** 涉及的包名集合（用于跨模块检测） */
    private List<String> packages;

    public TableSharingRecord() {
        this.mapperInterfaces = new ArrayList<String>();
        this.packages = new ArrayList<String>();
    }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public List<String> getMapperInterfaces() { return mapperInterfaces; }
    public void setMapperInterfaces(List<String> mapperInterfaces) { this.mapperInterfaces = mapperInterfaces; }

    public int getMapperCount() { return mapperCount; }
    public void setMapperCount(int mapperCount) { this.mapperCount = mapperCount; }

    public List<String> getPackages() { return packages; }
    public void setPackages(List<String> packages) { this.packages = packages; }

    /**
     * 涉及的独立包数量（用于跨模块检测）。
     * <p>
     * 包名取前三级（如 com.ruoyi.system），
     * 去重后即为独立模块数。
     * </p>
     */
    public int getDistinctModuleCount() {
        if (packages == null || packages.isEmpty()) return 0;
        // 取前三级包名去重（com.ruoyi.system）
        java.util.Set<String> modules = new java.util.LinkedHashSet<String>();
        for (String pkg : packages) {
            String module = extractTopLevelPackage(pkg, 3);
            if (module != null) {
                modules.add(module);
            }
        }
        return modules.size();
    }

    /**
     * 提取前 N 级包名。
     * @param pkg 完整包名（如 com.ruoyi.system.mapper）
     * @param levels 级数
     * @return 前 N 级包名（如 com.ruoyi），不足则返回原值
     */
    public static String extractTopLevelPackage(String pkg, int levels) {
        if (pkg == null) return null;
        String[] parts = pkg.split("\\.");
        if (parts.length <= levels) return pkg;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels; i++) {
            if (i > 0) sb.append('.');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "TableSharingRecord{" + tableName
                + " mappers=" + mapperCount + " modules=" + getDistinctModuleCount() + "}";
    }
}
