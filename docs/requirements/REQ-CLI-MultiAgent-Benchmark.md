# 需求 015：CLI 端多Agent接入 + 基准测试

> **版本：** v1.0
> **状态：** 待执行
> **优先级：** 🔴 P1
> **编写人：** 喵呜
> **日期：** 2026-05-28
> **目标模块：** codelens-cli
> **前置依赖：** Phase 1-4 已合入 main，common v0.4.0 tag 已打

---

## 1. 要解决什么问题

common 模块的多Agent框架（AgentRunner / Prompts / Validators / Cache / ReportMerger）已就绪，但 CLI 端仍在使用单次 LLM 调用。需要：

1. CLI 端接入多Agent流水线，支持单文件分析
2. 跑基准测试对比 single vs multi 两种模式的质量和性能

### 对比路径

```
single 模式（原路径）：
  源码 → SystemPrompt → 单次LLM调用 → V3Result → 输出

multi 模式（新路径）：
  源码 → AgentRunner(SUMMARY) → AgentRunner(METHOD_ANALYSIS×N) → ReportMerger → 输出
```

---

## 2. 改动清单

### 2.1 CLI 端 LLMClient 实现

| # | 改动 | 说明 |
|---|------|------|
| 1 | 新增 `CliLLMClient` 类 | 实现 `com.codelens.common.llm.LLMClient` 接口 |
| 2 | 委托给现有 DeepSeek 调用 | 读取命令行参数或配置的 apiKey/baseUrl/model/temperature |
| 3 | chat() 映射 | `chat(systemPrompt, userPrompt)` → 现有 HTTP 调用逻辑 |

### 2.2 CLI 参数扩展

| # | 改动 | 说明 |
|---|------|------|
| 1 | 新增 `--mode` 参数 | 选项：`single`（默认）/ `multi` |
| 2 | `single` 模式 | 走原有 CodeLensCli 单次调用路径，行为完全不变 |
| 3 | `multi` 模式 | 走 AgentRunner 多Agent路径 |

### 2.3 multi 模式执行逻辑

| # | 改动 | 说明 |
|---|------|------|
| 1 | 新增 `runMultiAgent()` 方法 | 在 CodeLensCli 中 |
| 2 | 流程 | 创建 CliLLMClient + GranularCacheAdapter → AgentRunner.run(SUMMARY) → AgentRunner.runMethodAnalysis()×N → ReportMerger.merge() |
| 3 | 输出格式 | 与 single 模式对齐（JSON），确保基准测试可对比 |
| 4 | 缓存 | multi 模式使用 GranularCache，single 模式不变 |

### 2.4 输出对齐

| # | 改动 | 说明 |
|---|------|------|
| 1 | ReportMerger 输出转换 | AnalysisReport → 与 V3Result 同构的 JSON，方便基准测试脚本直接对比 |
| 2 | 保留 multi 特有字段 | 在 JSON 中额外保留 executionTrace 等多Agent特有信息，不破坏对齐 |

---

## 3. 基准测试计划

### 3.1 测试集

使用现有 C1-C10 基准测试文件。

### 3.2 对比指标

| 指标 | single 模式基线 | multi 模式 | 对比目标 |
|------|----------------|------------|----------|
| L1 通过率 | 100% (99/99) | ? | ≥ single |
| 置信度 HIGH+ | 38.3% (38/99) | ? | > single（多Agent分步分析应更精准） |
| called_by | ❌ 无 | ⚠️ CLI端无PSI，暂无 | — |
| 平均耗时 | ~86s/文件 | ? | 记录，可接受即可 |
| Token 消耗 | 1次调用 ~4k tokens | SUMMARY 1次 + METHOD ×N | 记录对比 |
| 方法级覆盖 | 整文件一把出 | 每个方法独立分析 | multi 应更细粒度 |

### 3.3 测试流程

```bash
# 1. single 模式（基线）
for file in C1.java C2.java ... C10.java; do
  java -jar codelens.jar analyze --file $file --mode single
done

# 2. multi 模式
for file in C1.java C2.java ... C10.java; do
  java -jar codelens.jar analyze --file $file --mode multi
done

# 3. 对比
# - L1/L2 校验通过率
# - 置信度分布
# - 耗时
# - Token 消耗（从 ExecutionTrace 获取）
```

### 3.4 预期结论

| 指标 | 预期 | 理由 |
|------|------|------|
| L1 通过率 | ≥ single | multi 每个方法独立分析 + SummaryValidator 校验，更精准 |
| 置信度 HIGH+ | > single | 方法级分析减少幻觉，行号精度提升 |
| 耗时 | 可能更长 | 多次 LLM 调用，但可并行 + 缓存命中可缩短 |
| Token | 更多 | 多次调用总量更多，但单次更聚焦 |

---

## 4. 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `CliLLMClient.java` | 新增 | LLMClient 接口的 CLI 实现 |
| `CodeLensCli.java` | 修改 | 新增 --mode 参数 + runMultiAgent() |
| `ReportConverter.java` | 新增 | AnalysisReport → V3Result 对齐格式 |

---

## 5. 验收标准

- [ ] `--mode single` 行为与当前完全一致
- [ ] `--mode multi` 成功走 AgentRunner 流程，输出 JSON
- [ ] multi 模式输出格式与 single 模式对齐（兼容基准测试脚本）
- [ ] multi 模式输出额外含 executionTrace 信息
- [ ] C1-C10 基准测试 both modes 跑完，产出对比报告
- [ ] multi 模式 L1 通过率 ≥ single 模式
- [ ] 全部原有测试仍然通过

---

## 6. Claude Code 任务描述

```
在 codelens-java CLI 模块中接入多Agent流水线，并支持基准测试对比。

仓库：https://github.com/fiendchenmo/codelens-java.git
分支：从 main 创建 feature/cli-multi-agent

前置依赖：common v0.4.0（Phase 1-4 已合入 main）

需要做的改动：

1. 新增 codelens-cli/src/main/java/com/codelens/cli/CliLLMClient.java
   - implements com.codelens.common.llm.LLMClient
   - 构造参数: String apiKey, String baseUrl, String model, double temperature
   - chat(String systemPrompt, String userPrompt):
     复用现有 CodeLensCli 中的 DeepSeek HTTP 调用逻辑（OkHttp + JSON 解析）
     提取为内部方法，CliLLMClient 委托调用
   - 异常时抛 RuntimeException

2. 新增 codelens-cli/src/main/java/com/codelens/cli/ReportConverter.java
   - 静态方法: String convertToJson(AnalysisReport report)
   - 将 AnalysisReport 转为与现有 V3Result 同构的 JSON 格式：
     {
       "className": "...",
       "methods": [
         {
           "name": "...",
           "startLine": ...,
           "risks": [...],
           "l1": {...},
           "l2": {...}
         }
       ]
     }
   - 额外在顶层加 "executionTrace" 字段（从 AnalysisReport 获取）
   - 使用 Gson 序列化

3. 修改 codelens-cli/src/main/java/com/codelens/cli/CodeLensCli.java
   - 新增 CLI 参数 --mode，默认 "single"，可选 "multi"
   - 新增方法 runMultiAgent(String filePath, String apiKey, String baseUrl, String model, double temperature):
     a. 读取文件源码
     b. 创建 CliLLMClient
     c. 创建 GranularCacheAdapter（存储目录 .codelens/granular/）
     d. 创建 AgentRunner(llmClient, cache)
     e. AgentRunner.run(SUMMARY task, sourceCode, "")
     f. 对每个非平凡方法：AgentRunner.runMethodAnalysis(METHOD_ANALYSIS, method, sourceCode, summaryOutput, "")
     g. ReportMerger.merge(summaryOutput, methodOutputs)
     h. ReportConverter.convertToJson(report) → 输出到 stdout 或文件
   - main() 中根据 --mode 分支调用原路径或 runMultiAgent()

4. 新增单测
   - CliLLMClientTest: 验证接口实现、异常处理
   - ReportConverterTest: 验证 AnalysisReport → V3Result JSON 格式对齐
   - CodeLensCliModeTest: 验证 --mode 参数解析和分支

注意事项：
- single 模式代码一行不改，行为完全不变
- multi 模式的 GranularCache 存储在 .codelens/granular/ 下，与现有 .codelens/cache/ 不冲突
- CliLLMClient 的 HTTP 调用逻辑从 CodeLensCli 中提取，不要重复实现
- ReportConverter 输出的 JSON 必须与 V3Result 兼容，确保基准测试脚本无需修改
- 编译通过 + 全部测试通过后 push
```
