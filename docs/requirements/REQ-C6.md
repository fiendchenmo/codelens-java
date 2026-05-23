# 需求 — 双版测试

> 编号：REQ-C6
> 优先级：🟡 P1
> 工作量：1d
> 前置依赖：C-2~C-5（所有 V2/V3 分支代码完成）
> 责任人：喵呜
> 交付日期：5/29
> 变更归属：🟠 common变更（测试覆盖）

## 目的

C-2~C-5 引入了 V2/V3 双版本分支，需要确保双版本共存不破坏现有功能。现有 78+ 测试用例基于 V2 编写，需要：
1. 确保所有旧测试仍然通过（V2 兼容性）
2. 新增 V3 版本测试用例（V3 正确性）
3. 新增 V2/V3 交叉测试（版本隔离性）

## 测试范围

### 1. 旧测试回归（V2 兼容性）

- 所有现有测试用例不修改，直接运行
- 默认 SchemaVersion = V2 时，行为与改动前完全一致
- 重点：EvidenceValidator、SystemPrompt、Normalizer 的旧 API 调用

### 2. V3 新增测试用例

| 测试类 | 测试内容 |
|--------|---------|
| SchemaVersionTest | 枚举值、label、buildSchema(V2/V3) 输出差异 |
| CodeMetaDataV3Test | buildSchema(V3) 包含 calls、[FACT]/[INFER]，不含 architecture_issues |
| SystemPromptV3Test | buildBase(V3) 包含两阶段填充说明 |
| NormalizerV3Test | calls 数组截断修复、dependencies 截断修复（V3 模式） |
| EvidenceValidatorMethodRangeTest | MethodRange 包含/不包含行号、validateRisks 重载方法 |

### 3. V2/V3 隔离测试

- 调用 V2 API 不影响 V3 状态
- 调用 V3 API 不影响 V2 状态
- 同一进程内 V2/V3 可并行使用

## 验收标准

- [ ] 78+ 旧测试全部通过
- [ ] V3 新增测试全部通过
- [ ] V2/V3 隔离测试通过
- [ ] 测试覆盖率 ≥ 80%（新增类）
- [ ] `mvn test` 无 skip、无 ignore
- [ ] JDK 1.8 语法

## 约束

- 必须在 C-2~C-5 全部完成后执行
- 不修改旧测试用例的预期值
- JDK 1.8 语法
