# REQ-C7 测试用例 — L3 多轮验证

> 需求编号：REQ-C7
> 需求文档：`docs/requirements/REQ-C7.md`
> 测试源码：`codelens-common/src/test/java/com/codelens/common/validators/L3VerifierTest.java`
> 创建日期：2026-05-24

---

## 一、需求验收标准 → 测试用例映射

| # | 需求验收标准 | 对应测试用例 | 覆盖状态 |
|---|-------------|-------------|---------|
| A1 | L3Verifier 接口及 3 个实现类在 common 模块中，通过单元测试 | testVerifierInterfaceVerify, testVerifierInterfaceBatchVerify, testCrossValidatorConsistentResult, testConstraintValidatorKnownConstraintPass, testVotingValidatorMajorityPass | ✅ |
| A2 | CLI 端集成后，基准测试 L3 通过率达到目标值 | （集成测试，不在本文件范围） | ⏳ 集成阶段 |
| A3 | V3 Schema 输出含 [FACT]/[INFER] 标注 | testV3SchemaFactInferenceExtraction, testV3FactOnlyConstraintValidation | ✅ |
| A4 | V2 模式下 L3 验证仍可正常工作（无标注时统一验证） | testV2SchemaNoTagging | ✅ |
| A5 | mvn test 全部通过 | 全部 29 个测试通过即满足 | ✅ |
| A6 | 配置项可外部化（可通过配置文件/环境变量覆盖默认值） | testConfigExternalizable | ✅ |

---

## 二、需求设计方案 → 测试用例映射

### 2.1 核心概念 → 测试

| 需求设计点 | 对应测试 | 说明 |
|-----------|---------|------|
| 置信度分级 HIGH/MEDIUM/LOW | testThresholdFiltersHighConfidence, testThresholdFiltersMediumConfidence, testThresholdLowPassesAll | 三级阈值过滤逻辑 |
| 验证触发条件：置信度低于阈值 | testAllHighConfidenceNoVerification | 全HIGH不触发 |
| V3 [FACT] 只做 ConstraintValidator | testV3FactOnlyConstraintValidation, testConstraintValidatorFactTagOnly | [FACT] HIGH不触发交叉验证 |
| V3 [INFER] 做 Cross + Constraint | testV3SchemaFactInferenceExtraction | [INFER]触发验证 |
| V2 无标注统一做 Cross | testV2SchemaNoTagging | 全部中/低置信度触发 |

### 2.2 类设计 → 测试

| 需求中的类 | 对应测试 | 覆盖行为 |
|-----------|---------|---------|
| L3Verifier (接口) | testVerifierInterfaceVerify, testVerifierInterfaceBatchVerify | verify / verifyAll 契约 |
| ConfidenceThreshold | testThresholdDefault, testThresholdFiltersHighConfidence, testThresholdFiltersMediumConfidence, testThresholdLowPassesAll | 默认值 / 三级过滤 |
| VerificationRequest | testVerificationRequestConstruction | 构造+字段取值 |
| VerificationResult | testVerificationResultPass, testVerificationResultReject, testVerificationResultPending | 三种状态 + isPassed/isRejected/isPending |
| CrossValidator | testCrossValidatorConsistentResult, testCrossValidatorInconsistentResult, testCrossValidatorDisabled, testCrossValidatorMaxRetryExceeded | 一致/矛盾/关闭/超重试 |
| ConstraintValidator | testConstraintValidatorKnownConstraintPass, testConstraintValidatorUnknownConstraintReject, testConstraintValidatorEmptyConstraints, testConstraintValidatorFactTagOnly | 存在/不存在/空/[FACT] |
| VotingValidator | testVotingValidatorMajorityPass, testVotingValidatorMajorityReject, testVotingValidatorDisabled, testVotingValidatorTieResult | 多数通过/否决/关闭/平票 |

### 2.3 验证流程 → 测试

| 需求流程步骤 | 对应测试 | 说明 |
|-------------|---------|------|
| 提取低置信度结论 → Request 列表 | testV3SchemaFactInferenceExtraction, testV2SchemaNoTagging | V2/V3 两种提取路径 |
| 并行执行验证策略 | testVerifierInterfaceBatchVerify | verifyAll 批量 |
| 通过 → 标注已验证 | testVerificationResultPass | PASSED |
| 否决 → 标注已否决+反驳证据 | testCrossValidatorInconsistentResult, testConstraintValidatorUnknownConstraintReject | REJECTED + evidence |
| 待定 → 标注待人工确认 | testVerificationResultPending, testVotingValidatorTieResult | PENDING |
| 生成 L3 验证摘要 | testVerificationSummaryGeneration | 统计 + formatReport |

### 2.4 配置项 → 测试

| 配置项 | 默认值 | 对应测试 |
|--------|--------|---------|
| l3.enabled | false | testL3DisabledSkipsAll, testThresholdDefault |
| l3.confidence.threshold | MEDIUM | testThresholdDefault, testThresholdFiltersHighConfidence |
| l3.cross-validation.enabled | true | testCrossValidatorDisabled |
| l3.voting.enabled | false | testVotingValidatorDisabled |
| l3.max-retries | 1 | testCrossValidatorMaxRetryExceeded |
| 配置外部化 | — | testConfigExternalizable |

### 2.5 风险与约束 → 测试

| 风险/约束 | 对应测试 | 说明 |
|----------|---------|------|
| L3 默认关闭 | testL3DisabledSkipsAll, testThresholdDefault | enabled=false 跳过 |
| 不改现有 L1/L2 校验逻辑 | L3VerifierTest 独立文件，无 L1/L2 import | 物理隔离 |
| JDK 1.8 语法 | testJdk8Compatibility | 不用 List.of/Map.of/var |
| null 输入不崩溃 | testNullContextHandling | context=null 不抛 NPE |

---

## 三、完整测试用例清单

| # | 测试方法 | 所属维度 | 对应需求点 |
|---|---------|---------|-----------|
| 1 | testVerifierInterfaceVerify | 接口契约 | A1 L3Verifier接口 |
| 2 | testVerifierInterfaceBatchVerify | 接口契约 | A1 批量验证 |
| 3 | testThresholdDefault | 配置 | A6 默认值 |
| 4 | testThresholdFiltersHighConfidence | 配置 | 置信度分级 |
| 5 | testThresholdFiltersMediumConfidence | 配置 | 置信度分级 |
| 6 | testThresholdLowPassesAll | 配置 | 置信度分级 |
| 7 | testConfigExternalizable | 配置 | A6 外部化 |
| 8 | testVerificationRequestConstruction | 数据结构 | VerificationRequest |
| 9 | testVerificationResultPass | 数据结构 | VerificationResult/PASSED |
| 10 | testVerificationResultReject | 数据结构 | VerificationResult/REJECTED |
| 11 | testVerificationResultPending | 数据结构 | VerificationResult/PENDING |
| 12 | testCrossValidatorConsistentResult | CrossValidator | A1 交叉验证 |
| 13 | testCrossValidatorInconsistentResult | CrossValidator | A1 交叉验证 |
| 14 | testCrossValidatorDisabled | CrossValidator | 配置 l3.cross-validation.enabled |
| 15 | testCrossValidatorMaxRetryExceeded | CrossValidator | 配置 l3.max-retries |
| 16 | testConstraintValidatorKnownConstraintPass | ConstraintValidator | A1 约束验证 |
| 17 | testConstraintValidatorUnknownConstraintReject | ConstraintValidator | A1 约束验证 |
| 18 | testConstraintValidatorEmptyConstraints | ConstraintValidator | 边界条件 |
| 19 | testConstraintValidatorFactTagOnly | ConstraintValidator | A3 [FACT]标注 |
| 20 | testVotingValidatorMajorityPass | VotingValidator | A1 投票验证 |
| 21 | testVotingValidatorMajorityReject | VotingValidator | A1 投票验证 |
| 22 | testVotingValidatorDisabled | VotingValidator | 配置 l3.voting.enabled |
| 23 | testVotingValidatorTieResult | VotingValidator | 投票平票 |
| 24 | testV3SchemaFactInferenceExtraction | V2/V3兼容 | A3 V3标注 |
| 25 | testV2SchemaNoTagging | V2/V3兼容 | A4 V2无标注 |
| 26 | testV3FactOnlyConstraintValidation | V2/V3兼容 | A3 [FACT]只走约束 |
| 27 | testVerificationSummaryGeneration | 验证摘要 | 验证流程最后一步 |
| 28 | testL3DisabledSkipsAll | 边界条件 | 风险：默认关闭 |
| 29 | testEmptyClaimList | 边界条件 | 边界条件 |
| 30 | testNullContextHandling | 边界条件 | 风险：不崩溃 |
| 31 | testAllHighConfidenceNoVerification | 边界条件 | 触发条件 |
| 32 | testJdk8Compatibility | 边界条件 | 约束：JDK1.8 |

---

## 四、待实现类清单（给 Claude Code）

测试引用的类尚未实现，需按以下顺序创建：

```
com.codelens.common.validators.l3.ConfidenceLevel      — 枚举: HIGH/MEDIUM/LOW
com.codelens.common.validators.l3.VerificationStatus    — 枚举: PASSED/REJECTED/PENDING/SKIPPED
com.codelens.common.validators.l3.VerificationRequest   — 验证请求 POJO
com.codelens.common.validators.l3.VerificationResult    — 验证结果 POJO
com.codelens.common.validators.l3.ConfidenceThreshold   — 阈值判断工具
com.codelens.common.validators.l3.L3Config              — 配置类（含 fromMap 外部化）
com.codelens.common.validators.l3.KnownConstraint       — 已知约束 POJO
com.codelens.common.validators.l3.L3Verifier            — 接口（verify + verifyAll）
com.codelens.common.validators.l3.CrossValidator        — 交叉验证实现
com.codelens.common.validators.l3.ConstraintValidator   — 约束验证实现
com.codelens.common.validators.l3.VotingValidator       — 投票验证实现
com.codelens.common.validators.l3.L3RequestExtractor    — V2/V3 请求提取
com.codelens.common.validators.l3.L3Summary             — 验证摘要
```
