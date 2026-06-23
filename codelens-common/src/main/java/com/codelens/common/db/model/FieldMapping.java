// SYNC_VERSION: 2026-06-23-v1
// IMPACT: SCHEMA_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.db.model;

import java.util.ArrayList;
import java.util.List;

/**
 * resultMap 字段映射集合。
 * <p>
 * 来源：
 * <ul>
 *   <li>MyBatis {@code <resultMap id="..." type="...">} 标签</li>
 *   <li>JPA 实体类 {@code @Table} + {@code @Column} 注解组合</li>
 * </ul>
 * </p>
 */
public class FieldMapping {

    /** resultMap ID（如 "SysUserResult"）或 JPA 实体简单类名 */
    private String resultMapId;

    /** Java 类型全限定名（如 "com.ruoyi.system.domain.SysUser"） */
    private String javaType;

    /** 字段映射条目列表 */
    private List<FieldMapEntry> entries;

    public FieldMapping() {
        this.entries = new ArrayList<FieldMapEntry>();
    }

    public FieldMapping(String resultMapId, String javaType, List<FieldMapEntry> entries) {
        this.resultMapId = resultMapId;
        this.javaType = javaType;
        this.entries = entries != null ? entries : new ArrayList<FieldMapEntry>();
    }

    public String getResultMapId() { return resultMapId; }
    public void setResultMapId(String resultMapId) { this.resultMapId = resultMapId; }

    public String getJavaType() { return javaType; }
    public void setJavaType(String javaType) { this.javaType = javaType; }

    public List<FieldMapEntry> getEntries() { return entries; }
    public void setEntries(List<FieldMapEntry> entries) { this.entries = entries; }

    /**
     * 按属性名查找映射条目。
     * @param property Java 属性名
     * @return 匹配的条目，未找到返回 null
     */
    public FieldMapEntry findByProperty(String property) {
        for (FieldMapEntry entry : entries) {
            if (property.equals(entry.getProperty())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 按列名查找映射条目。
     * @param column 数据库列名
     * @return 匹配的条目，未找到返回 null
     */
    public FieldMapEntry findByColumn(String column) {
        for (FieldMapEntry entry : entries) {
            if (column.equals(entry.getColumn())) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "FieldMapping{" + resultMapId + " → " + javaType + ", entries=" + entries.size() + "}";
    }
}
