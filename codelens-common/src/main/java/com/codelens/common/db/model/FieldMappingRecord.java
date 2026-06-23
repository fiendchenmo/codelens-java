// SYNC_VERSION: 2026-06-23-v1
// IMPACT: SCHEMA_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.db.model;

/**
 * 查询返回：字段映射记录。
 * <p>
 * 描述 Java 属性与数据库列的对应关系，
 * 来源包括 MyBatis resultMap 和 JPA @Column 注解。
 * </p>
 */
public class FieldMappingRecord {

    /** Mapper 接口全限定名或 JPA 实体类名 */
    private String mapperInterface;

    /** Java 类型 */
    private String javaType;

    /** Java 属性名 */
    private String propertyName;

    /** 数据库列名 */
    private String columnName;

    /** 是否为主键 */
    private boolean isId;

    /** 数据来源类型（MYBATIS_RESULTMAP / JPA_COLUMN） */
    private String sourceType;

    /** 源文件路径 */
    private String sourceFile;

    public static final String SOURCE_MYBATIS_RESULTMAP = "MYBATIS_RESULTMAP";
    public static final String SOURCE_JPA_COLUMN = "JPA_COLUMN";

    public FieldMappingRecord() {}

    public String getMapperInterface() { return mapperInterface; }
    public void setMapperInterface(String mapperInterface) { this.mapperInterface = mapperInterface; }

    public String getJavaType() { return javaType; }
    public void setJavaType(String javaType) { this.javaType = javaType; }

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }

    public boolean isId() { return isId; }
    public void setId(boolean isId) { this.isId = isId; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

    @Override
    public String toString() {
        return "FieldMappingRecord{" + javaType + "." + propertyName
                + " ↔ " + columnName + (isId ? " (PK)" : "") + "}";
    }
}
