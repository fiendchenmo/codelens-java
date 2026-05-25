// SYNC_VERSION: 2026-05-25-v1
// IMPACT: LOGIC_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.validators.l3;

public enum VerificationVerdict {
    CONFIRMED,   // 声明被验证通过
    REJECTED,    // 声明被明确否定
    UNCERTAIN    // 无法确定
}
