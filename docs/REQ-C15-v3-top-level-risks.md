# REQ-C15: V3 顶层 risks 兜底

## 背景

C-14 为 V3 methods[].risks 增加了 confidence 字段（CERTAIN/POSSIBLE），但基准测试显示 risks 召回率仍为 1/3，与改前一致。

**根因**：V3 把 risks 嵌套在 methods 内部，模型逐方法填充时只关注方法内确定性问题，不会做跨方法安全审计。V2 的顶层 risks 是独立的，模型看到"必须检查安全风险"会专门扫描全文件。

**C-9 基准测试对比**：
- V2: 3 条 risks（事务缺失 + 循环插入 + 权限校验）
- V3: 1 条 risk（仅事务缺失，标 CERTAIN），其余 2 条丢失

## 目标

V3 顶层增加 `risks[]`，用于文件级跨方法/跨字段风险审计，与 methods[].risks（方法内精确标注）互补，保证召回率不低于 V2。

## 设计

### 双层 risks 职责划分

| 层级 | 位置 | 职责 | confidence |
|---|---|---|---|
| 方法级 | methods[].risks | 方法内部可直接观测的风险 | CERTAIN 为主 |
| 文件级 | 顶层 risks[] | 跨方法/跨字段/安全审计类风险 | POSSIBLE 为主 |

### Schema 变更

V3 顶层增加 `risks[]`，结构与 methods[].risks 一致：

```json
{
  "summary": "...",
  "framework": "...",
  "fields": [...],
  "methods": [...],
  "risks": [{
    "type": "SECURITY|PERFORMANCE|MAINTAINABILITY",
    "description": "风险描述",
    "line": 行号,
    "severity": "HIGH|MEDIUM|LOW",
    "impact": "影响面",
    "suggestion": "修复建议",
    "confidence": "CERTAIN|POSSIBLE"
  }]
}
```

### Prompt 变更

V3 两阶段填充协议追加：

```
### 第三阶段：文件级风险审计 [AUDIT]
- 完成所有 methods 填充后，必须对整个文件做一次独立的安全审计扫描
- 重点检查：跨方法事务一致性、跨字段状态依赖、安全漏洞（路径遍历/SQL注入/权限缺失）、异常处理模式
- methods[].risks 已覆盖的风险不在顶层重复
- 顶层 risks 用于放跨方法、跨字段或需要全局视角才能发现的风险
- [AUDIT] 风险标记 confidence: "POSSIBLE"（因为依赖跨方法推理）
- 基于代码事实可直接确认的标记 confidence: "CERTAIN"
```

### CLI 渲染变更

V3 渲染分支新增顶层 risks 区块，显示在 methods 之后：

```
━━━ 文件级风险 ━━━
⚠ [可能] SECURITY  MEDIUM  resetPwd无权限校验 (行70)
⚠ [确定] MAINTAINABILITY  HIGH  复合操作缺事务保护 (行37)
```

方法内 risks 不变，继续在方法块内显示。

### Normalizer 变更

`normalizeAllTopLevelRisks()` 处理顶层 risks 的 confidence 归一化，逻辑与 methods[].risks 一致。

### V2 不动

V2 无顶层 risks 变更，零影响。

## 改动文件清单

| 文件 | 改动 |
|---|---|
| `CodeMetaData.java` | buildV3Schema() 顶层加 risks[] |
| `SystemPrompt.java` | V3 协议追加第三阶段 [AUDIT] |
| `OutputNormalizer.java` | 新增 normalizeTopLevelRisks() |
| `CodeLensCli.java` | V3 渲染新增顶层 risks 区块 |

## 验收标准

1. `mvn test` 全绿
2. V3 输出含顶层 risks[]，C9 基准测试 risks 召回率 ≥ V2（3/3）
3. methods[].risks 和顶层 risks 不重复（同风险只出现在一个层级）
4. V2 输出完全不变

## 分支

`feature/REQ-C15-v3-top-level-risks`

## 关联

- 前置：REQ-C14（confidence 分级，已合并）
- 数据支撑：C9 V2/V3 对比（V2 3条 risks vs V3 1条）
