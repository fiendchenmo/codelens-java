// SYNC_VERSION: 2026-05-25-v1
// IMPACT: LOGIC_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.models;

/**
 * 模型能力标记枚举。
 * 用于描述 LLM 模型的能力边界，SystemPrompt 和 Normalizer 据此自适应调整。
 */
public enum ModelCapability {
    LONG_CONTEXT,       // 支持 32K+ 上下文
    STABLE_JSON,        // JSON 输出格式稳定（截断率低）
    HIGH_ACCURACY,      // 高精度模式（适合 Pro/GPT-4）
    FAST_RESPONSE,      // 快速响应（适合 Flash/Doubao）
    LIMITED_OUTPUT,     // 输出长度受限（需精简 prompt）
    MULTI_TURN          // 支持多轮对话（L3 验证需要）
}
