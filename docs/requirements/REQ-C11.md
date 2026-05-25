# 需求 — 模型特性标记

> 编号：REQ-C11
> 优先级：🟡 P1
> 工作量：1d
> 前置依赖：无
> 责任人：喵呜
> 交付日期：5/29
> 变更归属：🟠 common变更 + 📋 Schema责任人

## 目的

不同 LLM 模型的能力边界差异大（上下文长度、输出格式稳定性、JSON 能力等），当前无法根据模型特性自动调整 prompt 和校验策略。

目标：引入模型特性标记枚举，让 SystemPrompt 和 Normalizer 根据模型特性自适应调整。

## 设计方案

### ModelCapability 枚举

```java
public enum ModelCapability {
    LONG_CONTEXT,       // 支持 32K+ 上下文
    STABLE_JSON,        // JSON 输出格式稳定（截断率低）
    HIGH_ACCURACY,      // 高精度模式（适合 Pro/GPT-4）
    FAST_RESPONSE,      // 快速响应（适合 Flash/Doubao）
    LIMITED_OUTPUT,     // 输出长度受限（需精简 prompt）
    MULTI_TURN,         // 支持多轮对话（L3 验证需要）
}
```

### ModelProfile 数据类

```java
public class ModelProfile {
    private final String provider;
    private final String model;
    private final Set<ModelCapability> capabilities;
    private final int maxContextTokens;    // 最大上下文 token 数
    private final int maxOutputTokens;     // 最大输出 token 数
    private final double recommendedTemperature;  // 推荐温度（与 C-10 联动）
    
    public static ModelProfile of(String provider, String model);
    
    public boolean hasCapability(ModelCapability cap) {
        return capabilities.contains(cap);
    }
}
```

### 已知模型 Profile

| Provider | Model | Capabilities | Max Context | Max Output |
|----------|-------|-------------|-------------|------------|
| doubao | flash | FAST_RESPONSE, STABLE_JSON | 32K | 4K |
| doubao | pro | HIGH_ACCURACY, LONG_CONTEXT, MULTI_TURN | 128K | 16K |
| openai | gpt-4 | HIGH_ACCURACY, LONG_CONTEXT, STABLE_JSON, MULTI_TURN | 128K | 16K |
| qwen | qwen-plus | FAST_RESPONSE | 32K | 8K |
| deepseek | deepseek-chat | HIGH_ACCURACY, MULTI_TURN | 64K | 8K |

### 与 SystemPrompt/Normalizer 的联动

- SystemPrompt: 根据 `LIMITED_OUTPUT` 自动精简 struct 上下文
- SystemPrompt: 根据 `STABLE_JSON` 调整 JSON 格式提示的详细程度
- Normalizer: 根据 `STABLE_JSON` 决定是否启用截断修复逻辑
- L3 验证: 根据 `MULTI_TURN` 决定是否启用多轮验证

## 变更范围

| 模块 | 变更内容 |
|------|---------|
| codelens-common | 新增 ModelCapability 枚举 |
| codelens-common | 新增 ModelProfile 数据类 + 预设 Profile |
| codelens-common | Schema 中增加 modelProfile 字段（可选） |

## 验收标准

- [x] ModelCapability 枚举在 common 模块中
- [x] ModelProfile 数据类在 common 模块中
- [x] of(provider, model) 至少支持上述 5 个模型
- [x] 未知模型返回带默认值的 fallback Profile
- [x] of() 全部转小写匹配（provider.toLowerCase() + model.toLowerCase()）
- [x] `mvn test` 全部通过
- [x] JDK 1.8 语法

## 约束

- ModelProfile 是只读数据类（不可变）
- 未知模型不抛异常，返回合理默认值
- 与 C-10 ProviderPreset 温度联动，但互不依赖
- JDK 1.8 语法
