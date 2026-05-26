# REQ-C14: V3 risks 置信度分级

## 背景

V3 引入 [FACT]/[INFER] 两阶段填充协议后，模型对 risks 采取保守策略——拿不准的 [INFER] 风险直接不输出，导致 V3 risks 召回率从 V2 的 3/3 降至 1/3（C9 SysUserServiceImpl 基准测试数据）。

根因：协议未强制 [INFER] risks 输出，模型选择"宁可漏也不错"。

## 目标

- **精准保留**：确定的风险标 CERTAIN，推理的风险标 POSSIBLE，不丢弃
- **可区分显示**：CLI 渲染时区分 CERTAIN/POSSIBLE，用户一眼识别
- **向后兼容**：无 confidence 字段的旧 V3 输出默认按 CERTAIN 处理；V2 完全不动

## 改动清单

### 1. Schema — `CodeMetaData.buildV3Schema()`

methods[].risks 增加 `confidence` 字段：

```json
"risks": [{
  "type": "SECURITY|PERFORMANCE|MAINTAINABILITY",
  "description": "风险描述",
  "line": 行号,
  "severity": "HIGH|MEDIUM|LOW",
  "impact": "影响面",
  "suggestion": "修复建议",
  "confidence": "CERTAIN|POSSIBLE"
}]
```

### 2. Prompt — `SystemPrompt.buildBase(SchemaVersion.V3)`

修改两阶段填充协议中 risks 相关措辞：

- [FACT] 风险 → `confidence: "CERTAIN"`（代码中可直接观测）
- [INFER] 风险 → `confidence: "POSSIBLE"`（基于 FACT 推导，可能发生）
- **关键约束**：[INFER] 风险不得省略，宁可标 POSSIBLE 也不得遗漏
- [INFER] 风险的 description 需简述推理依据（引用哪些代码事实支撑）

### 3. CLI 渲染 — `CodeLensCli.formatAnalysisResult()` V3 分支

risks 渲染区分显示：

```
[确定] SECURITY  HIGH  空指针链式调用 (line 85)
[可能] PERFORMANCE  MEDIUM  批量操作未分页可能OOM (line 120)
       └─ 依据: listAll() 无 limit 参数 + 返回 List<XXX>
```

- CERTAIN → `[确定]`
- POSSIBLE → `[可能]`，换行缩进显示推理依据
- 无 confidence 字段时默认 `[确定]`（兼容旧输出）

### 4. 校验器

V3 risks 校验中 confidence 为可选字段；有值时只能是 `CERTAIN` 或 `POSSIBLE`。

### 5. V2 不动

V2 无 confidence 字段，不走此逻辑，零影响。

## 验收标准

1. `mvn test` 全绿（当前 290+ tests）
2. V3 渲染输出含 `[确定]` / `[可能]` 标记
3. V2 输出完全不变
4. 无 confidence 字段的旧 V3 JSON 不报错，默认 `[确定]`

## 分支

`feature/REQ-C14-v3-risk-confidence`

## 关联

- 前置：REQ-C13（CLI V3 渲染，已合并）
- 数据支撑：C6 V2/V3 对比报告（`docs/C6-v2v3-comparison-report.md`）
