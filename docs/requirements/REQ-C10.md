# 需求 — ProviderPreset 温度锁定

> 编号：REQ-C10
> 优先级：🟡 P1
> 工作量：0.5d
> 前置依赖：无
> 责任人：喵呜
> 交付日期：5/26
> 变更归属：🟠 common变更

## 目的

不同 LLM 模型对分析任务的最优温度不同。当前温度设置散落在各处配置中，且用户可能误设高温导致分析结果不稳定。

目标：将温度逻辑收归 common 模块的 ProviderPreset，按模型/任务类型锁定推荐温度，防止误配置。

## 设计方案

### ProviderPreset 增强

```java
public class ProviderPreset {
    private final String provider;    // openai, doubao, qwen, deepseek
    private final String model;       // gpt-4, flash, pro
    private final double temperature; // 锁定温度
    private final boolean locked;     // 是否锁定（锁定时用户无法覆盖）
    
    // 按 provider+model 获取预设
    public static ProviderPreset of(String provider, String model);
    
    // 已知最优温度
    // Flash/Doubao(免费): 0.1  — 代码分析需要低随机性
    // Pro/DeepSeek:       0.0  — 精准模式
    // GPT-4:              0.1  — 默认
}
```

### 温度锁定逻辑

- `locked=true`：忽略用户传入的 temperature，强制使用预设值
- `locked=false`：用户可覆盖，但记录一条 warn 日志说明推荐值
- 默认：locked=true（代码分析场景不应高温）

### 配置外化

```properties
# 可通过配置文件覆盖
codelens.provider.flash.temperature=0.1
codelens.provider.flash.locked=true
```

## 变更范围

| 模块 | 变更内容 |
|------|---------|
| codelens-common | ProviderPreset 增加 temperature + locked 字段 |
| codelens-common | 新增 of(provider, model) 工厂方法 |
| codelens-cli | LLMClient 调用时使用 ProviderPreset 温度 |

## 验收标准

- [ ] ProviderPreset 包含 temperature 和 locked 字段
- [ ] of(provider, model) 返回对应预设
- [ ] locked=true 时用户传入温度被忽略
- [ ] locked=false 时用户温度生效，但输出 warn 日志
- [ ] `mvn test` 全部通过
- [ ] JDK 1.8 语法

## 约束

- 不改现有 ProviderPreset 的其他字段
- 温度锁定是新增能力，不破坏现有配置方式
- JDK 1.8 语法
