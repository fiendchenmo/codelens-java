package com.codelens.common.diff;

/**
 * 变更重要程度
 * HIGH: 方法签名/逻辑变更
 * LOW: 仅import/注释/空行变更
 */
public enum ChangeSignificance {
    HIGH,
    LOW
}
