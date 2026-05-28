# 需求 011：多Agent流水线 Phase 3 — 方法分析 Agent Prompt + 校验器 + 基准测试

> **版本：** v1.0
> **状态：** 待执行
> **优先级：** 🔴 P1
> **编写人：** 喵呜
> **日期：** 2026-05-28
> **目标模块：** codelens-common

---

## 1. 要解决什么问题

Phase 2 完成摘要 Agent。Phase 3 实现方法级分析 Agent：对每个非平凡方法生成 L1 证据 + L2 置信度标注，替代当前单次 LLM 调用中对所有方法的一锅端分析。

核心改进：
- **方法级粒度**：每个方法独立调用 LLM，大文件不再截断
- **增量缓存**：只改了的方法重新分析，未变的走缓存
- **可验证输出**：校验器确保 L1 证据格式正确，L2 置信度在合理范围

## 2. 功能需求

### 2.1 MethodAnalysisPrompt 模板

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | Prompt 模板类 `MethodAnalysisPrompt` | 方法级分析 Agent 的 system prompt + user prompt |
| 2 | System Prompt | 定义角色、输出格式（JSON Schema）、L1/L2 字段要求 |
| 3 | User Prompt 模板 | 接受：方法签名 + 方法源码 + 文件摘要（Phase 2 产出）+ 索引元数据 |
| 4 | 输出 JSON Schema | L1 证据字段 + L2 置信度字段，与现有 ReportBuilder 输出对齐 |

**输出 Schema 示例：**
```json
{
  "method": "processOrder",
  "l1Evidence": {
    "calls": ["validateOrder", "calculateTotal", "saveOrder"],
    "calledBy": ["OrderController.createOrder"],
    "fieldsUsed": ["orderRepository", "paymentGateway"]
  },
  "l2Confidence": {
    "overallScore": 0.85,
    "reasoningBasis": "SOLID_ANALYSIS",
    "riskIndicators": ["HIGH_CYCLOMATIC"]
  }
}
```

### 2.2 MethodAnalysisValidator 校验器

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | 校验器类 `MethodAnalysisValidator` | 校验方法分析 Agent 输出 |
| 2 | L1 字段完整性 | calls / calledBy / fieldsUsed 至少有一个非空 |
| 3 | L2 字段范围 | overallScore ∈ [0, 1]，reasoningBasis 是已知枚举值 |
| 4 | 方法名匹配 | 输出的 method 字段与输入方法签名一致 |
| 5 | 返回 ValidationResult | 复用 Phase 2 的 ValidationResult |

### 2.3 基准测试框架

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | BenchmarkRunner 类 | 加载测试样本，运行 Agent，收集指标 |
| 2 | 测试样本格式 | Java 文件 + 预期分析结果（JSON） |
| 3 | 指标收集 | L1 覆盖率、L2 准确率、token 消耗、延迟 |
| 4 | 报告输出 | Markdown 格式基准报告 |

### 2.4 缓存策略

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | 缓存 key | `SHA-256(methodSourceCode + TaskType.METHOD_ANALYSIS.name())` |
| 2 | 方法级粒度 | 每个方法独立缓存，文件中改一个方法只重分析那一个 |
| 3 | 依赖摘要缓存 | 文件摘要变更时，该文件所有方法分析缓存失效 |

---

## 3. 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `common/agent/MethodAnalysisPrompt.java` | 新增 | 方法分析 Prompt 模板 |
| `common/agent/MethodAnalysisValidator.java` | 新增 | 方法分析输出校验器 |
| `common/benchmark/BenchmarkRunner.java` | 新增 | 基准测试运行器 |
| `common/benchmark/BenchmarkResult.java` | 新增 | 基准测试结果数据类 |
| `common/benchmark/SampleLoader.java` | 新增 | 测试样本加载器 |
| `common/agent/TaskType.java` | 修改 | METHOD_ANALYSIS 补充 promptClass + validatorClass |

---

## 4. 验收标准

- [ ] MethodAnalysisPrompt 生成包含 L1/L2 Schema 约束的 prompt
- [ ] MethodAnalysisValidator 对合法输出通过，对 L1 空字段、L2 越界、方法名不匹配报错
- [ ] BenchmarkRunner 能加载样本并收集指标
- [ ] 方法级缓存 key 与源码绑定，改一个方法不影响其他方法缓存
- [ ] 至少12个单元测试通过

---

## 5. Claude Code 任务描述

```
在 codelens-java（common模块）中实现多Agent流水线 Phase 3：方法分析 Agent + 基准测试框架。

仓库：https://github.com/fiendchenmo/codelens-java.git
分支：从 feature/multi-agent-phase2 创建 feature/multi-agent-phase3

已有代码（不要修改 Phase 1/2 的核心逻辑）：
- Phase 1: AnalysisTask, TaskType, CacheGranule, GranularCache, GranularCacheAdapter
- Phase 2: SummaryPrompt, SummaryValidator, ValidationResult

需要新增的文件：

1. common/agent/MethodAnalysisPrompt.java
   - generateSystemPrompt(): 要求 LLM 输出 L1 证据 + L2 置信度 JSON
   - generateUserPrompt(String methodSignature, String methodSourceCode, String fileSummary, String metadata)
   - L1 字段: calls[], calledBy[], fieldsUsed[]
   - L2 字段: overallScore(0-1), reasoningBasis(枚举), riskIndicators[]

2. common/agent/MethodAnalysisValidator.java
   - implements Predicate<String>
   - 校验: JSON解析 → method字段匹配 → L1至少1个非空 → overallScore∈[0,1] → reasoningBasis是枚举值
   - 返回 ValidationResult
   - reasoningBasis 枚举值: SOLID_ANALYSIS, HEURISTIC, PARTIAL, UNKNOWN

3. common/benchmark/BenchmarkRunner.java
   - run(SampleLoader loader): 对每个样本运行 Agent，收集结果
   - 输出 BenchmarkResult 列表

4. common/benchmark/BenchmarkResult.java
   - 数据类: sampleName, l1Coverage, l2Accuracy, tokenCount, latencyMs, passed

5. common/benchmark/SampleLoader.java
   - 从 classpath 或文件系统加载测试样本
   - 样本格式: {name}.java + {name}.expected.json

6. 修改 TaskType.java
   - METHOD_ANALYSIS → MethodAnalysisPrompt.class, MethodAnalysisValidator.class

7. 测试:
   - MethodAnalysisPromptTest (2个)
   - MethodAnalysisValidatorTest (6个: valid + 5种invalid)
   - BenchmarkRunnerTest (2个: 基本运行 + 空样本)
   - SampleLoaderTest (2个: 加载成功 + 文件不存在)

注意:
- 包名: com.codelens.common.agent, com.codelens.common.benchmark
- 不依赖 LLM SDK
- JSON 解析用 Gson
- 编译通过 + 全部测试通过后 push
```
