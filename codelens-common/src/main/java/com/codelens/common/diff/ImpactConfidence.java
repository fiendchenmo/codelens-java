package com.codelens.common.diff;

/**
 * 影响置信度
 * HIGH:   静态调用链确认（DIRECT callType）
 * MEDIUM: 跨包依赖推断或间接影响（SPRING_INJECTION 或 hop≥2）
 * LOW:    弱依赖（REFLECTION）
 */
public enum ImpactConfidence {
    HIGH,
    MEDIUM,
    LOW
}
