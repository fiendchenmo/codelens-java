// SYNC_VERSION: 2026-06-23-v1
// IMPACT: LOGIC_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.db.parser;

import com.codelens.common.db.model.SqlType;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 表名/字段名提取器。
 * <p>
 * 从纯 SQL 文本（已剥离 MyBatis 动态标签）中提取涉及的表名和字段名。
 * 基于正则表达式，适用于简单 CRUD SQL（若依 90%+ 场景）。
 * </p>
 *
 * <p>提取精度分级：</p>
 * <ul>
 *   <li><b>L1 确定</b> — 明确出现的字段名（WHERE user_name = ...）</li>
 *   <li><b>L2 推断</b> — 别名.字段 → 可关联 FROM 表</li>
 *   <li><b>L3 粗筛</b> — SELECT * / 动态片段 → 标记为 WILDCARD</li>
 * </ul>
 *
 * <p>MVP 只做 L1 + L2，L3 标记为 WILDCARD。</p>
 */
public class SqlTableExtractor {

    // ─── 表名提取正则 ────────────────────────────────

    /** FROM 主表或子查询后的表名 */
    private static final Pattern FROM_TABLE = Pattern.compile(
            "\\bFROM\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
            Pattern.CASE_INSENSITIVE);

    /** JOIN 表名 */
    private static final Pattern JOIN_TABLE = Pattern.compile(
            "\\bJOIN\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
            Pattern.CASE_INSENSITIVE);

    /** INSERT INTO 目标表 */
    private static final Pattern INSERT_TABLE = Pattern.compile(
            "\\bINSERT\\s+(?:INTO\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)",
            Pattern.CASE_INSENSITIVE);

    /** UPDATE 目标表 */
    private static final Pattern UPDATE_TABLE = Pattern.compile(
            "\\bUPDATE\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
            Pattern.CASE_INSENSITIVE);

    /** DELETE FROM 目标表 */
    private static final Pattern DELETE_TABLE = Pattern.compile(
            "\\bDELETE\\s+FROM\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
            Pattern.CASE_INSENSITIVE);

    // ─── 字段名提取正则 ──────────────────────────────

    /** SELECT 列（含别名) */
    private static final Pattern SELECT_COLUMNS = Pattern.compile(
            "\\bSELECT\\s+(.*?)\\s+FROM\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** INSERT 列 */
    private static final Pattern INSERT_COLUMNS = Pattern.compile(
            "\\bINSERT\\s+(?:INTO\\s+)?[a-zA-Z_][a-zA-Z0-9_]*\\s*\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE);

    /** SET 子句中的字段 */
    private static final Pattern SET_FIELDS = Pattern.compile(
            "\\bSET\\s+(.*?)(?:\\bWHERE\\b|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** WHERE 子句中的字段 */
    private static final Pattern WHERE_CONDITIONS = Pattern.compile(
            "\\bWHERE\\s+(.*?)(?:\\bGROUP\\s+BY\\b|\\bORDER\\s+BY\\b|\\bLIMIT\\b|\\bHAVING\\b|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 单个字段引用: column_name = ... 或 table.column_name */
    private static final Pattern FIELD_REF = Pattern.compile(
            "(?:^|[^a-zA-Z0-9_])"
                    + "([a-zA-Z_][a-zA-Z0-9_]*\\.[a-zA-Z_][a-zA-Z0-9_]*"
                    + "|"
                    + "[a-zA-Z_][a-zA-Z0-9_]*)"
                    + "\\s*[=<>!]",
            Pattern.CASE_INSENSITIVE);

    /** SET 子句中的 column = value 模式 */
    private static final Pattern SET_COLUMN = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_]*)\\s*=",
            Pattern.CASE_INSENSITIVE);

    /** SELECT 列表中的字段（含别名，如 "user_name AS userName"） */
    private static final Pattern SELECT_FIELD = Pattern.compile(
            "(?:^|,)\\s*([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)",
            Pattern.CASE_INSENSITIVE);

    /** LIKE/IN/BETWEEN 中的字段 */
    private static final Pattern LIKE_IN_FIELD = Pattern.compile(
            "(?:^|[^a-zA-Z0-9_.])([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)"
                    + "\\s+(?:LIKE|IN|BETWEEN|IS|NOT)",
            Pattern.CASE_INSENSITIVE);

    /** ORDER BY / GROUP BY 中的字段 */
    private static final Pattern ORDER_GROUP_FIELD = Pattern.compile(
            "\\b(?:ORDER|GROUP)\\s+BY\\s+(.*?)(?:$|\\bLIMIT\\b|\\bHAVING\\b|;)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** MyBatis 动态标签 */
    private static final Pattern MYBATIS_TAG = Pattern.compile(
            "<(/?)(?:if|where|foreach|choose|when|otherwise|trim|set|bind)"
                    + "[^>]*>",
            Pattern.CASE_INSENSITIVE);

    /** MyBatis 参数占位符 */
    private static final Pattern MYBATIS_PARAM = Pattern.compile(
            "[#$]\\{[^}]+\\}");

    /** WILDCARD 匹配：SELECT * 或动态片段 */
    private static final Pattern WILDCARD_SELECT = Pattern.compile(
            "\\bSELECT\\s+\\*",
            Pattern.CASE_INSENSITIVE);

    /** 动态 SQL 标记：${...} 片段 */
    private static final Pattern DYNAMIC_MARKER = Pattern.compile(
            "\\$\\{[^}]+\\}");

    /** 关键字/函数过滤（不应被当作表名或字段名） */
    private static final Set<String> KEYWORDS = new java.util.HashSet<String>();
    static {
        String[] kws = {"SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "LIKE",
                "BETWEEN", "IS", "NULL", "AS", "ON", "SET", "INTO", "VALUES",
                "ORDER", "GROUP", "BY", "ASC", "DESC", "LIMIT", "OFFSET",
                "HAVING", "DISTINCT", "ALL", "CASE", "WHEN", "THEN", "ELSE",
                "END", "LEFT", "RIGHT", "INNER", "OUTER", "CROSS", "JOIN",
                "COUNT", "SUM", "AVG", "MIN", "MAX", "TRUE", "FALSE",
                "NOW", "CURDATE", "SYSDATE", "DATE", "TIME", "TIMESTAMP"};
        for (String kw : kws) {
            KEYWORDS.add(kw);
            KEYWORDS.add(kw.toLowerCase());
        }
    }

    /**
     * 从 SQL 文本中提取表名。
     *
     * @param sqlText 纯 SQL 文本（已剥离 MyBatis 动态标签）
     * @param sqlType SQL 操作类型
     * @return 涉及的表名集合
     */
    public static Set<String> extractTables(String sqlText, SqlType sqlType) {
        Set<String> tables = new LinkedHashSet<String>();
        if (sqlText == null || sqlText.trim().isEmpty()) {
            return tables;
        }

        String sql = normalizeSql(sqlText);

        switch (sqlType) {
            case SELECT:
                // FROM table 和 JOIN table
                extractAllMatches(FROM_TABLE, sql, 1, tables);
                extractAllMatches(JOIN_TABLE, sql, 1, tables);
                break;
            case INSERT:
                extractAllMatches(INSERT_TABLE, sql, 1, tables);
                break;
            case UPDATE:
                extractAllMatches(UPDATE_TABLE, sql, 1, tables);
                break;
            case DELETE:
                extractAllMatches(DELETE_TABLE, sql, 1, tables);
                break;
        }

        // 过滤关键字和空值
        tables.removeIf(t -> t == null || t.isEmpty() || isKeyword(t));

        return tables;
    }

    /**
     * 从 SQL 文本中提取字段名。
     *
     * @param sqlText 纯 SQL 文本（已剥离 MyBatis 动态标签）
     * @param sqlType SQL 操作类型
     * @return 涉及的字段名集合（可能含 WILDCARD 或 DYNAMIC）
     */
    public static Set<String> extractFields(String sqlText, SqlType sqlType) {
        Set<String> fields = new LinkedHashSet<String>();
        if (sqlText == null || sqlText.trim().isEmpty()) {
            return fields;
        }

        String sql = normalizeSql(sqlText);

        // 检测 WILDCARD
        if (WILDCARD_SELECT.matcher(sql).find()) {
            fields.add("WILDCARD");
        }

        // 检测动态标记
        if (DYNAMIC_MARKER.matcher(sqlText).find()) {
            fields.add("DYNAMIC");
        }

        switch (sqlType) {
            case SELECT:
                extractSelectFields(sql, fields);
                extractWhereFields(sql, fields);
                extractOrderGroupFields(sql, fields);
                break;
            case INSERT:
                extractInsertFields(sql, fields);
                break;
            case UPDATE:
                extractSetFields(sql, fields);
                extractWhereFields(sql, fields);
                break;
            case DELETE:
                extractWhereFields(sql, fields);
                break;
        }

        // 清理：去别名前缀、过滤关键字
        Set<String> cleaned = new LinkedHashSet<String>();
        for (String f : fields) {
            if (f == null || f.isEmpty() || isKeyword(f)) continue;
            // "table.column" → "column"
            int dot = f.indexOf('.');
            if (dot >= 0 && dot < f.length() - 1) {
                String col = f.substring(dot + 1);
                if (!isKeyword(col) && !col.isEmpty()) {
                    cleaned.add(col);
                }
            } else if (dot < 0) {
                cleaned.add(f);
            }
        }

        return cleaned;
    }

    /**
     * 剥离 MyBatis 动态标签，保留纯 SQL 文本。
     *
     * @param sqlWithTags 含动态标签的 SQL
     * @return 纯 SQL 文本
     */
    public static String stripDynamicTags(String sqlWithTags) {
        if (sqlWithTags == null || sqlWithTags.isEmpty()) {
            return "";
        }

        // 1. 移除 MyBatis 动态标签（保留标签内的文本内容）
        String stripped = MYBATIS_TAG.matcher(sqlWithTags).replaceAll("");

        // 2. 移除 MyBatis 参数占位符
        stripped = MYBATIS_PARAM.matcher(stripped).replaceAll("?");

        // 3. 压缩空白
        stripped = stripped.replaceAll("\\s+", " ").trim();

        return stripped;
    }

    // ─── 私有提取方法 ─────────────────────────────────

    /**
     * 标准化 SQL 文本：压缩空白、移除多余空格。
     */
    private static String normalizeSql(String sql) {
        if (sql == null) return "";
        return sql.replaceAll("\\s+", " ").trim();
    }

    private static void extractAllMatches(Pattern pattern, String sql,
                                          int group, Set<String> results) {
        Matcher m = pattern.matcher(sql);
        while (m.find()) {
            String value = m.group(group);
            if (value != null && !value.isEmpty()) {
                results.add(value);
            }
        }
    }

    private static void extractSelectFields(String sql, Set<String> fields) {
        Matcher m = SELECT_COLUMNS.matcher(sql);
        if (m.find()) {
            String columns = m.group(1);
            // 分割逗号并提取字段名
            String[] parts = columns.split(",");
            for (String part : parts) {
                Matcher fm = SELECT_FIELD.matcher(part.trim());
                if (fm.find()) {
                    String field = fm.group(1);
                    if (field != null && !field.isEmpty() && !"*".equals(field)) {
                        fields.add(field);
                    }
                }
            }
        }
    }

    private static void extractInsertFields(String sql, Set<String> fields) {
        Matcher m = INSERT_COLUMNS.matcher(sql);
        if (m.find()) {
            String columns = m.group(1);
            String[] parts = columns.split(",");
            for (String part : parts) {
                String field = part.trim();
                if (!field.isEmpty()) {
                    fields.add(field);
                }
            }
        }
    }

    private static void extractSetFields(String sql, Set<String> fields) {
        // 先找到 SET 子句
        Matcher setMatcher = Pattern.compile(
                "\\bSET\\s+(.*?)(?:\\bWHERE\\b|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(sql);
        if (setMatcher.find()) {
            String setClause = setMatcher.group(1);
            Matcher cm = SET_COLUMN.matcher(setClause);
            while (cm.find()) {
                String field = cm.group(1);
                if (field != null && !isKeyword(field)) {
                    fields.add(field);
                }
            }
        }
    }

    private static void extractWhereFields(String sql, Set<String> fields) {
        // 找到 WHERE 子句
        Matcher whereMatcher = Pattern.compile(
                "\\bWHERE\\s+(.*?)(?:\\bGROUP\\s+BY\\b|\\bORDER\\s+BY\\b|\\bLIMIT\\b|\\bHAVING\\b|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(sql);
        if (whereMatcher.find()) {
            String whereClause = whereMatcher.group(1);
            // 提取等号引用
            Matcher fm = FIELD_REF.matcher(whereClause);
            while (fm.find()) {
                String field = fm.group(1);
                if (field != null && !isKeyword(field)) {
                    fields.add(field);
                }
            }
            // 提取 LIKE/IN/BETWEEN 引用
            Matcher lm = LIKE_IN_FIELD.matcher(whereClause);
            while (lm.find()) {
                String field = lm.group(1);
                if (field != null && !isKeyword(field)) {
                    fields.add(field);
                }
            }
        }
    }

    private static void extractOrderGroupFields(String sql, Set<String> fields) {
        Matcher m = ORDER_GROUP_FIELD.matcher(sql);
        if (m.find()) {
            String clause = m.group(1);
            String[] parts = clause.split(",");
            for (String part : parts) {
                String field = part.trim().replaceAll("\\s+(?:ASC|DESC).*", "").trim();
                if (!field.isEmpty() && !isKeyword(field)) {
                    fields.add(field);
                }
            }
        }
    }

    /**
     * 判断是否为 SQL 关键字或函数名。
     */
    static boolean isKeyword(String word) {
        if (word == null) return true;
        return KEYWORDS.contains(word) || KEYWORDS.contains(word.toLowerCase());
    }
}
