# 需求 — SystemPrompt 双模板 + 两阶段填充说明

> 编号：REQ-C4
> 优先级：🟡 P1
> 工作量：1d
> 前置依赖：C-3（SchemaVersion 枚举，按版本选模板）
> 责任人：喵呜
> 交付日期：5/28
> 变更归属：🟠 common变更

## 目的

当前 SystemPrompt 只有单一模板，LLM 容易篡改基准事实（struct 中的已知信息）来"迎合"推理结果。V3 引入两阶段填充机制：

1. **第一阶段（事实填充）**：LLM 只能原样复述 struct 中的已知信息，标注为 `[FACT]`
2. **第二阶段（推理填充）**：LLM 基于 FACT 进行推理，标注为 `[INFER]`

目标：SystemPrompt 按 SchemaVersion 选择 V2/V3 模板，V3 模板包含两阶段填充说明。

## 设计方案

### buildBase(version) 方法

```java
public static String buildBase(SchemaVersion version) {
    switch (version) {
        case V2: return buildBaseV2();  // 现有模板，不变
        case V3: return buildBaseV3();  // 新模板，含两阶段说明
        default: throw new IllegalArgumentException("Unsupported version: " + version);
    }
}
```

### V3 模板两阶段说明（追加在 CORE_RULES 末尾）

```
## 两阶段填充协议（V3 专用）

### 第一阶段：事实填充 [FACT]
- struct 中已有的信息（字段名、方法签名、调用关系）标记为 [FACT]
- [FACT] 项必须与 struct 完全一致，不得修改、省略或"纠正"
- 违反 [FACT] 约束的输出将被校验器标记为错误

### 第二阶段：推理填充 [INFER]
- 基于 [FACT] 推导出的结论标记为 [INFER]
- [INFER] 项必须说明推理依据（引用哪些 [FACT] 支撑）
- [INFER] 项的置信度由 LLM 自行判断
- [INFER] 项可能被 L3 验证器二次校验
```

### 与现有模板的关系

- V2 模板完全不变，保持向后兼容
- V3 模板在 V2 基础上追加两阶段说明
- CORE_RULES 中与 V3 冲突的规则（如强制 architecture_issues 数量）已在 C-1 中移除

## 变更范围

| 模块 | 变更内容 |
|------|---------|
| codelens-common | SystemPrompt: buildBase(SchemaVersion) 按版本选模板 |
| codelens-common | 新增 buildBaseV3() 方法，含两阶段说明 |
| codelens-common | 保留 buildBase() 无参方法标记 @Deprecated，默认 V2 |

## 验收标准

- [ ] buildBase(V2) 输出与当前 buildBase() 完全一致
- [ ] buildBase(V3) 输出包含两阶段填充协议说明
- [ ] V3 prompt 中 [FACT] 和 [INFER] 的规则清晰无歧义
- [ ] V2 prompt 不受影响
- [ ] `mvn test` 全部通过
- [ ] JDK 1.8 语法

## 约束

- 依赖 C-3（SchemaVersion 枚举）
- 不改 V2 模板内容
- JDK 1.8 语法
