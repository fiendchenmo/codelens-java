# 需求 — L3 多轮验证

> 编号：REQ-C7
> 优先级：🔴 P0
> 工作量：2d
> 前置依赖：无
> 责任人：喵呜
> 交付日期：5/25
> 变更归属：🟠 common变更 + 📋 Schema责任人

## 目的

当前 L1/L2 校验已有基础实现，但 L3（语义级验证）尚未落地。L3 多轮验证的核心思路：对 LLM 初次分析结果中置信度低的关键结论，自动发起追问/交叉验证，提升分析可信度。

目标：将 L3 校验算法提取到 common 模块，CLI 端和插件端共用。

## 背景

- Round0-1 基准测试显示，L1 通过率已达 98%+，但 L3 层面的可信度仍依赖人工判断
- LLM 对复杂依赖关系、隐式调用链的分析容易出错，需要二次验证
- 当前无自动化手段区分"高置信结论"和"低置信猜测"

## 设计方案

### 核心概念

1. **置信度分级**：LLM 输出的每个结论标注置信度（HIGH/MEDIUM/LOW），V3 Schema 中以 `[FACT]`/`[INFER]` 区分
2. **验证触发条件**：置信度低于阈值的结论自动触发二次验证
3. **验证策略**：
   - **交叉验证**：换一个角度/切入点重新问 LLM，比较两次结论一致性
   - **约束验证**：用已知约束（方法签名、调用链）反向校验结论
   - **投票验证**：多模型对同一结论投票，多数一致则通过

### 类设计（→ common）

```
L3Verifier (接口)
├── ConfidenceThreshold   # 置信度阈值配置
├── VerificationRequest   # 验证请求（原始结论+上下文）
├── VerificationResult    # 验证结果（通过/否决/待定+证据）
├── CrossValidator        # 交叉验证实现
├── ConstraintValidator   # 约束验证实现
└── VotingValidator       # 投票验证实现（多模型）
```

### 验证流程

```
LLM 初次输出
  ↓
L1/L2 校验通过
  ↓
提取低置信度结论 → VerificationRequest 列表
  ↓
并行执行验证策略（Cross + Constraint）
  ↓
合并 VerificationResult
  ├─ 通过 → 标注为已验证，纳入最终报告
  ├─ 否决 → 标注为已否决，附反驳证据
  └─ 待定 → 标注为待人工确认
  ↓
生成 L3 验证摘要（附在报告末尾）
```

### 与 V3 Schema 的关系

- V3 的 `[FACT]`/`[INFER]` 标注是 L3 的输入信号
- `[FACT]` = LLM 确信的事实，L3 仅做 ConstraintValidator
- `[INFER]` = LLM 的推理，L3 做 CrossValidator + ConstraintValidator
- V2 不含标注，L3 对所有结论统一做 CrossValidator

### 配置项（→ common）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| l3.enabled | false | L3 验证总开关 |
| l3.confidence.threshold | MEDIUM | 触发验证的置信度阈值 |
| l3.cross-validation.enabled | true | 交叉验证开关 |
| l3.voting.enabled | false | 投票验证开关（需多模型配置） |
| l3.max-retries | 1 | 单个结论最大验证轮次 |

## 变更范围

| 模块 | 变更内容 |
|------|---------|
| codelens-common | 新增 L3Verifier 接口 + 3 个实现类 + 配置类 |
| codelens-common | CodeMetaData 增加置信度相关字段 |
| codelens-cli | 集成 L3Verifier，在分析流程中调用 |
| codelens-common | JSON_SCHEMA V3 增加 `[FACT]`/`[INFER]` 标注支持 |

## 验收标准

- [ ] L3Verifier 接口及 3 个实现类在 common 模块中，通过单元测试
- [ ] CLI 端集成后，基准测试 L3 通过率达到目标值
- [ ] V3 Schema 输出含 `[FACT]`/`[INFER]` 标注
- [ ] V2 模式下 L3 验证仍可正常工作（无标注时统一验证）
- [ ] `mvn test` 全部通过
- [ ] 配置项可外部化（可通过配置文件/环境变量覆盖默认值）

## 风险与约束

- L3 验证会增加 LLM 调用次数（成本），默认关闭，需显式开启
- 交叉验证可能增加 50%~100% 的 LLM 调用开销
- JDK 1.8 语法
- 不改现有 L1/L2 校验逻辑
