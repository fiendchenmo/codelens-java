// SYNC_VERSION: 2026-06-23-v1
// IMPACT: SCHEMA_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.db.model;

/**
 * 单条字段映射条目：Java 属性 ↔ 数据库列。
 * <p>
 * 来源：
 * <ul>
 *   <li>MyBatis {@code <resultMap>} 中的 {@code <result>} / {@code <id>} 标签</li>
 *   <li>JPA {@code @Column(name="...")} 注解</li>
 * </ul>
 * </p>
 */
public class FieldMapEntry {

    /** Java 属性名（如 "userId"） */
    private String property;

    /** 数据库列名（如 "user_id"） */
    private String column;

    /** 是否为主键（MyBatis {@code <id>} 标签或 JPA {@code @Id}） */
    private boolean isId;

    public FieldMapEntry() {}

    public FieldMapEntry(String property, String column, boolean isId) {
        this.property = property;
        this.column = column;
        this.isId = isId;
    }

    public String getProperty() { return property; }
    public void setProperty(String property) { this.property = property; }

    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }

    public boolean isId() { return isId; }
    public void setId(boolean isId) { this.isId = isId; }

    @Override
    public String toString() {
        return property + " ↔ " + column + (isId ? " (PK)" : "");
    }
}
