// SYNC_VERSION: 2026-06-23-v1
// IMPACT: SCHEMA_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.db.model;

/**
 * 查询返回：单条数据库操作记录。
 * <p>
 * 由 {@link com.codelens.common.db.query.DbAnalysisRepository} 的查询方法返回，
 * 用于字段影响分析、表级影响分析、Mapper 方法关联查询。
 * </p>
 */
public class DbOperationRecord {

    /** Mapper 接口全限定名 */
    private String mapperInterface;

    /** Mapper 方法名 */
    private String methodName;

    /** SQL 操作类型（SELECT/INSERT/UPDATE/DELETE） */
    private String sqlType;

    /** 涉及的数据库表名 */
    private String tableName;

    /** 涉及的字段名，逗号分隔（可能含 WILDCARD） */
    private String fields;

    /** XML 行号（1-based），非 XML 来源为 0 */
    private int xmlLine;

    /** 精简后的 SQL 文本 */
    private String sqlText;

    /** 数据来源类型（MYBATIS_XML / JPA_ENTITY / JPA_QUERY / RAW_SQL） */
    private String sourceType;

    /** 源文件路径 */
    private String sourceFile;

    public DbOperationRecord() {}

    public String getMapperInterface() { return mapperInterface; }
    public void setMapperInterface(String mapperInterface) { this.mapperInterface = mapperInterface; }

    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }

    public String getSqlType() { return sqlType; }
    public void setSqlType(String sqlType) { this.sqlType = sqlType; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getFields() { return fields; }
    public void setFields(String fields) { this.fields = fields; }

    public int getXmlLine() { return xmlLine; }
    public void setXmlLine(int xmlLine) { this.xmlLine = xmlLine; }

    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

    @Override
    public String toString() {
        return "DbOperationRecord{" + mapperInterface + "." + methodName
                + " [" + sqlType + "] table=" + tableName + " fields=" + fields + "}";
    }
}
