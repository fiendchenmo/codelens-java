# 需求 — Normalizer V3 分支

> 编号：REQ-C5
> 优先级：🟡 P1
> 工作量：1d
> 前置依赖：C-3（SchemaVersion 枚举，按版本分支）
> 责任人：喵呜
> 交付日期：5/28
> 变更归属：🟠 common变更

## 目的

当前 OutputNormalizer 处理 LLM 截断输出时，只修复 `dependencies` 数组的截断问题。V3 Schema 中 `dependencies` 仅含 field 类型，`method_call` 类型的依赖移入了 `keyMethods[].calls[]`，需要 Normalizer 也处理 `calls` 数组的截断修复。

目标：Normalizer 按 SchemaVersion 分支处理，V2 修 dependencies 截断，V3 修 calls 截断。

## 背景

- Round0-1 基准测试中，C2 文件出现 JSON 截断，原因是 calls 数组过长导致 LLM 输出被 max_tokens 截断
- 当前 Normalizer 只处理顶层 dependencies 的截断，不处理嵌套在 keyMethods 中的 calls 截断
- V3 的 calls 数组替代了 V2 dependencies 中的 method_call 类型

## 设计方案

### 分支逻辑

```java
public NormalizedOutput normalize(String rawOutput, SchemaVersion version) {
    // 公共预处理：提取已生成的有效 JSON 数据
    Map<String, Object> extracted = extractValidData(rawOutput);
    
    switch (version) {
        case V2:
            return normalizeV2(extracted);  // 修 dependencies 截断
        case V3:
            return normalizeV3(extracted);  // 修 calls 截断
        default:
            throw new IllegalArgumentException("Unsupported version: " + version);
    }
}
```

### V2 截断修复（现有逻辑，不变）

- 检测 `dependencies` 数组是否被截断（最后一个元素不完整）
- 移除不完整元素，保留已完整生成的部分
- 保留 OutputNormalizer 已有的增强：从不闭合 JSON 中提取有效数据

### V3 截断修复（新增）

- 检测 `keyMethods[].calls[]` 是否被截断
- 移除不完整的 call 项，保留已完整生成的部分
- 同时检测顶层 `dependencies` 截断（V3 中 deps 仅含 field 类型）
- 如果某个 keyMethod 的 calls 被完全截断（空数组），保留 keyMethod 本体，calls 标记为空

### 数据流

```
V2: LLM 输出 → extractValidData → 修 dependencies → 输出
V3: LLM 输出 → extractValidData → 修 dependencies + 修 calls → 输出
```

## 变更范围

| 模块 | 变更内容 |
|------|---------|
| codelens-common | OutputNormalizer: normalize(String, SchemaVersion) 新增重载 |
| codelens-common | 新增 normalizeV3 方法 |
| codelens-common | 保留 normalize(String) 标记 @Deprecated，默认 V2 |

## 验收标准

- [ ] normalizeV2 行为与现有 normalize 完全一致
- [ ] normalizeV3 能修复 calls 数组截断（最后一个 call 不完整时移除）
- [ ] normalizeV3 能修复 dependencies 数组截断
- [ ] V3 截断修复后输出的 JSON 可被 Gson 正常解析
- [ ] `mvn test` 全部通过
- [ ] JDK 1.8 语法

## 约束

- 依赖 C-3（SchemaVersion 枚举）
- 不改现有 V2 的 normalize 逻辑
- JDK 1.8 语法
