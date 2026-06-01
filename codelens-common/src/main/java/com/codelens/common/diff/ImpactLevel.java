package com.codelens.common.diff;

/**
 * 影响级别
 * DIRECT: 直接调用变更方法（hop=1）
 * INDIRECT: 通过中间节点间接影响（hop≥2）
 */
public enum ImpactLevel {
    DIRECT,
    INDIRECT
}
