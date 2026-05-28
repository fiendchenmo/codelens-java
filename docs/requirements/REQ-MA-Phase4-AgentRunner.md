# 需求 012：多Agent流水线 Phase 4 — AgentRunner 调度框架 + 结果合并

> **版本：** v1.0
> **状态：** 待执行
> **优先级：** 🔴 P1
> **编写人：** 喵呜
> **日期：** 2026-05-28
> **目标模块：** codelens-common

---

## 1. 要解决什么问题

Phase 2/3 完成了摘要 Agent 和方法分析 Agent 的 Prompt + Validator。Phase 4 实现 AgentRunner 调度框架，将多个 Agent 串联起来，统一管理执行流程、缓存查询、结果合并。

核心目标：
- **一个入口**：调用方只管 `AgentRunner.run(task)`，不用关心 Agent 串并联逻辑
- **缓存优先**：每个 Agent 执行前先查缓存，命中则跳过
- **结果合并**：摘要 + 方法分析 → 合并为最终分析报告，供插件端/CLI端消费

## 2. 功能需求

### 2.1 AgentRunner 调度框架

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | `AgentRunner` 类 | 接受 AnalysisTask，按 TaskType 路由到对应 Agent |
| 2 | 执行流程 | 查缓存 → 命中返回 / 未命中 → 调 LLM → 校验 → 缓存结果 → 返回 |
| 3 | 串联逻辑 | METHOD_ANALYSIS 类型先执行 SUMMARY（如果无缓存），再执行方法分析 |
| 4 | 重试策略 | 校验失败时最多重试1次（重新调 LLM），仍失败则标记 SKIPPED |
| 5 | 执行日志 | 每步记录：缓存命中/未命中、LLM调用、校验结果、耗时 |

### 2.2 结果合并

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | `AnalysisReport` 数据类 | 合并摘要 + 方法分析的最终输出结构 |
| 2 | `ReportMerger` 类 | 将 SUMMARY 输出 + 多个 METHOD_ANALYSIS 输出合并为 AnalysisReport |
| 3 | 字段合并规则 | class级信息取摘要，方法级信息取方法分析，冲突时方法分析优先 |
| 4 | 序列化 | AnalysisReport → JSON，与现有插件端/CLI端报告格式兼容 |

**AnalysisReport 结构：**
```java
public class AnalysisReport {
    private String className;
    private String stereotype;
    private List<MethodReport> methods;
    private List<String> dependencies;
    private String overallComplexity;
    private Map<String, Object> metadata;  // 执行元数据
}

public class MethodReport {
    private String methodName;
    private String signature;
    private L1Evidence l1Evidence;
    private L2Confidence l2Confidence;
}
```

### 2.3 LLM Client 接口

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | `LLMClient` 接口 | 统一 LLM 调用抽象，CLI端和插件端各自实现 |
| 2 | 方法签名 | `String chat(String systemPrompt, String userPrompt)` |
| 3 | 默认实现 | `StubLLMClient` — 返回固定 JSON，用于测试 |

### 2.4 执行状态追踪

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | `ExecutionStatus` 枚举 | PENDING / RUNNING / COMPLETED / FAILED / SKIPPED / CACHED |
| 2 | `ExecutionTrace` 数据类 | 每个 Agent 执行的完整轨迹：taskId, status, cacheHit, retryCount, latencyMs |

---

## 3. 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `common/agent/AgentRunner.java` | 新增 | 调度框架 |
| `common/agent/AnalysisReport.java` | 新增 | 最终报告数据类 |
| `common/agent/MethodReport.java` | 新增 | 方法报告数据类 |
| `common/agent/L1Evidence.java` | 新增 | L1 证据数据类 |
| `common/agent/L2Confidence.java` | 新增 | L2 置信度数据类 |
| `common/agent/ReportMerger.java` | 新增 | 结果合并器 |
| `common/agent/ExecutionStatus.java` | 新增 | 执行状态枚举 |
| `common/agent/ExecutionTrace.java` | 新增 | 执行轨迹数据类 |
| `common/llm/LLMClient.java` | 新增 | LLM 调用接口 |
| `common/llm/StubLLMClient.java` | 新增 | 测试用 Stub 实现 |

---

## 4. 验收标准

- [ ] AgentRunner.run(SUMMARY task) 正确执行摘要流程
- [ ] AgentRunner.run(METHOD_ANALYSIS task) 先触发摘要再执行方法分析
- [ ] 缓存命中时跳过 LLM 调用，返回 CACHED 状态
- [ ] 校验失败重试1次，仍失败返回 SKIPPED
- [ ] ReportMerger 正确合并摘要 + 方法分析输出
- [ ] AnalysisReport 可序列化为 JSON
- [ ] 至少15个单元测试通过

---

## 5. Claude Code 任务描述

```
在 codelens-java（common模块）中实现多Agent流水线 Phase 4：AgentRunner 调度框架 + 结果合并。

仓库：https://github.com/fiendchenmo/codelens-java.git
分支：从 feature/multi-agent-phase3 创建 feature/multi-agent-phase4

已有代码（不要修改 Phase 1/2/3 的核心逻辑）：
- Phase 1: AnalysisTask, TaskType, CacheGranule, GranularCache, GranularCacheAdapter
- Phase 2: SummaryPrompt, SummaryValidator, ValidationResult
- Phase 3: MethodAnalysisPrompt, MethodAnalysisValidator, BenchmarkRunner

需要新增的文件：

1. common/agent/AgentRunner.java
   - 构造参数: LLMClient, GranularCache
   - run(AnalysisTask): 根据TaskType路由
   - 执行流程: 查缓存 → LLM → 校验 → 缓存 → 返回
   - METHOD_ANALYSIS 时先确保有 SUMMARY 结果
   - 校验失败重试1次
   - 每步记录 ExecutionTrace

2. common/agent/AnalysisReport.java
   - className, stereotype, methods(List<MethodReport>), dependencies, overallComplexity, metadata
   - toJson() 方法

3. common/agent/MethodReport.java
   - methodName, signature, l1Evidence(L1Evidence), l2Confidence(L2Confidence)

4. common/agent/L1Evidence.java
   - calls(List<String>), calledBy(List<String>), fieldsUsed(List<String>)

5. common/agent/L2Confidence.java
   - overallScore(double), reasoningBasis(String), riskIndicators(List<String>)

6. common/agent/ReportMerger.java
   - merge(String summaryJson, List<String> methodJsons): → AnalysisReport
   - class级取summary，方法级取method analysis

7. common/agent/ExecutionStatus.java
   - 枚举: PENDING, RUNNING, COMPLETED, FAILED, SKIPPED, CACHED

8. common/agent/ExecutionTrace.java
   - taskId, taskType, status, cacheHit(boolean), retryCount, latencyMs

9. common/llm/LLMClient.java
   - 接口: String chat(String systemPrompt, String userPrompt)

10. common/llm/StubLLMClient.java
    - 实现 LLMClient，返回预定义 JSON，用于测试

11. 修改 TaskType.java
    - 补全 CROSS_FILE_INFERENCE 的 promptClass/validatorClass（暂设null）

12. 测试:
    - AgentRunnerTest (6个: 摘要执行、方法分析执行、缓存命中、校验失败重试、校验2次失败SKIPPED、串联摘要)
    - ReportMergerTest (3个: 正常合并、空方法列表、方法覆盖摘要)
    - AnalysisReportTest (2个: toJson、字段完整性)
    - ExecutionTraceTest (2个: 状态转换、缓存命中标记)
    - StubLLMClientTest (2个: 返回有效JSON、返回空)

注意:
- 包名: com.codelens.common.agent, com.codelens.common.llm
- 不依赖具体 LLM SDK
- JSON 用 Gson
- 编译通过 + 全部测试通过后 push
```
