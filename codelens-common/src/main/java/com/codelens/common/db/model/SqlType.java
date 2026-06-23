// SYNC_VERSION: 2026-06-23-v1
// IMPACT: SCHEMA_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.db.model;

/**
 * SQL 操作类型枚举。
 * <p>
 * 对应 MyBatis XML 中的 {@code <select>}, {@code <insert>},
 * {@code <update>}, {@code <delete>} 标签，
 * 以及 JPA {@code @Query} 注解中的 SQL 类型。
 * </p>
 */
public enum SqlType {
    SELECT,
    INSERT,
    UPDATE,
    DELETE
}
