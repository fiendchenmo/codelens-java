// SYNC_VERSION: 2026-06-23-v1
// IMPACT: SCHEMA_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.db.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 一次 SQL 操作的完整提取结果。
 * <p>
 * 对应 MyBatis XML 中的一个 {@code <select>}/{@code <insert>}/{@code <update>}/{@code <delete>} 标签，
 * 或 JPA 实体类的一个持久化操作。
 * </p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@link #mapperInterface} — Mapper 接口全限定名或 JPA 实体类名</li>
 *   <li>{@link #methodName} — Mapper 方法名或 JPA Repository 方法名</li>
 *   <li>{@link #sqlType} — SQL 操作类型</li>
 *   <li>{@link #tables} — 涉及的数据库表名集合</li>
 *   <li>{@link #fields} — 涉及的数据库字段名集合（可能含 {@code WILDCARD}）</li>
 *   <li>{@link #sqlText} — 精简后的 SQL 文本（≤200 字符）</li>
 *   <li>{@link #xmlLine} — XML 中的行号（1-based），非 XML 来源为 0</li>
 * </ul>
 */
public class SqlOperation {

    /** 通配符标记：SQL 包含 SELECT * 或动态片段无法确定具体字段 */
    public static final String WILDCARD = "*";

    /** SQL 包含动态片段（如 ${...}），部分内容无法静态分析 */
    public static final String DYNAMIC = "<DYNAMIC>";

    /** 字段来源：MyBatis XML */
    public static final String SOURCE_MYBATIS_XML = "MYBATIS_XML";

    /** 字段来源：JPA 实体注解 */
    public static final String SOURCE_JPA_ENTITY = "JPA_ENTITY";

    /** 字段来源：JPA @Query 注解 */
    public static final String SOURCE_JPA_QUERY = "JPA_QUERY";

    /** 字段来源：原生 SQL 字符串 */
    public static final String SOURCE_RAW_SQL = "RAW_SQL";

    /** Mapper 接口全限定名（如 com.ruoyi.system.mapper.SysUserMapper） */
    private String mapperInterface;

    /** Mapper 方法名（如 selectUserByUserName） */
    private String methodName;

    /** SQL 操作类型 */
    private SqlType sqlType;

    /** 涉及的数据库表名集合 */
    private Set<String> tables;

    /** 涉及的数据库字段名集合（可能含 WILDCARD 或 DYNAMIC） */
    private Set<String> fields;

    /** 精简后的 SQL 文本（≤200 字符） */
    private String sqlText;

    /** XML 中的行号（1-based），非 XML 来源为 0 */
    private int xmlLine;

    /** 关联的 resultMap ID（可能为空） */
    private String resultMapId;

    /** resultMap 字段映射（可能为空） */
    private FieldMapping fieldMapping;

    /** 数据来源类型（MYBATIS_XML / JPA_ENTITY / JPA_QUERY / RAW_SQL） */
    private String sourceType;

    /** 源文件路径 */
    private String sourceFile;

    public SqlOperation() {
        this.tables = new LinkedHashSet<String>();
        this.fields = new LinkedHashSet<String>();
    }

    public String getMapperInterface() { return mapperInterface; }
    public void setMapperInterface(String mapperInterface) { this.mapperInterface = mapperInterface; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public SqlType getSqlType() { return sqlType; }
    public void setSqlType(SqlType sqlType) { this.sqlType = sqlType; }

    public Set<String> getTables() { return tables; }
    public void setTables(Set<String> tables) { this.tables = tables; }

    /**
     * 添加一个表名。
     * @param table 表名
     */
    public void addTable(String table) {
        if (table != null && !table.isEmpty()) {
            this.tables.add(table);
        }
    }

    public Set<String> getFields() { return fields; }
    public void setFields(Set<String> fields) { this.fields = fields; }

    /**
     * 添加一个字段名。
     * @param field 字段名
     */
    public void addField(String field) {
        if (field != null && !field.isEmpty()) {
            this.fields.add(field);
        }
    }

    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }

    public int getXmlLine() { return xmlLine; }
    public void setXmlLine(int xmlLine) { this.xmlLine = xmlLine; }

    public String getResultMapId() { return resultMapId; }
    public void setResultMapId(String resultMapId) { this.resultMapId = resultMapId; }

    public FieldMapping getFieldMapping() { return fieldMapping; }
    public void setFieldMapping(FieldMapping fieldMapping) { this.fieldMapping = fieldMapping; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

    @Override
    public String toString() {
        return "SqlOperation{" + mapperInterface + "." + methodName
                + " [" + sqlType + "] tables=" + tables + " fields=" + fields + "}";
    }
}
