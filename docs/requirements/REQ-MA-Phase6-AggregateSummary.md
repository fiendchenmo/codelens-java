# 需求 014：多Agent流水线 Phase 6 — 包级/模块级聚合 Summary

> **版本：** v1.0
> **状态：** 待执行
> **优先级：** 🔴 P1
> **编写人：** 喵呜
> **日期：** 2026-05-28
> **目标模块：** codelens-common（核心）+ codelens-cli（输出）+ codelens-plugin（渲染+存盘）
> **前置依赖：** Phase 1-5 完成，AgentRunner 调度框架可用

---

## 1. 要解决什么问题

当前多Agent流水线的分析粒度停在文件级（File Summary + Method Analysis）。用户选一个包或模块时，只能看到一堆散的文件报告，没有整体视图。

**目标**：自底向上分层聚合，生成包级和模块级摘要，让用户一眼看懂"这个包干什么、核心类是哪些、包间怎么依赖"。

### 聚合路径

```
用户选模块/包
    │
    ├── 递归扫描所有文件
    │
    ├─── 文件级 MethodAnalysis × N（并行）
    │         ↓
    ├─── 文件级 File Summary × N（并行）
    │         ↓
    ├─── Package Summary × M（同包内 File Summary 完成后触发）
    │         ↓
    └─── Module Summary × 1（所有 Package Summary 完成后触发）
```

每一层模式相同：**聚合子级摘要 → LLM 生成本级摘要**。

---

## 2. 核心设计

### 2.1 统一聚合模型

用统一的 `AGGREGATE_SUMMARY` + level 参数，不拆两个枚举：

| 层级 | TaskType | 输入 | 输出 | 聚合键 |
|------|----------|------|------|--------|
| File | SUMMARY | 文件源码 | keyMethods/核心逻辑/风险点 | 文件 SHA-256 |
| Package | AGGREGATE_SUMMARY(level=PACKAGE) | 包内所有 File Summary | 包功能/核心类/包间依赖 | 包内文件 SHA-256 组合 |
| Module | AGGREGATE_SUMMARY(level=MODULE) | 模块内所有 Package Summary | 模块定位/子包职责/对外接口 | 模块内包 SHA-256 组合 |

### 2.2 Token 爆炸解法：Map-Reduce 分层聚合

大包50个文件，50份 Summary 全塞 LLM 会爆上下文。解法是**树形聚合**：

```
50个 File Summary
    │
    ├─── Group 1 (≤10个) → Agent A → 中间摘要1
    ├─── Group 2 (≤10个) → Agent B → 中间摘要2
    ├─── Group 3 (≤10个) → Agent C → 中间摘要3
    ├─── Group 4 (≤10个) → Agent D → 中间摘要4
    ├─── Group 5 (≤10个) → Agent E → 中间摘要5
    │
    5个中间摘要 → Agent F → Package Summary
```

**分组策略**（AgentRunner 根据文件数自动选择）：

| 文件数 | 策略 | LLM 调用次数 |
|--------|------|--------------|
| ≤ 10 | 直接聚合 | 1 |
| 11 ~ 50 | 一级分组 → 聚合 | ⌈N/10⌉ + 1 |
| > 50 | 两级分组 → 两级聚合 | ⌈N/10⌉ + ⌈⌈N/10⌉/5⌉ + 1 |

**分组方式**：让 LLM 做一次轻量分类调用——收到所有 File Summary 的文件名+一句话摘要，只输出分组表（哪个文件属于哪组），输出极小。然后各组并行跑 Summary。

### 2.3 增量更新

不是无脑全量重跑，而是**逐层检查输出是否变化**：

```
文件修改 → 重跑 File Summary（必须）
        → 新旧 File Summary 对比
        → 输出变了 → 重跑 Package Summary
        → 输出没变 → 跳过，Package 缓存命中
                    → Package 没重跑 → Module 缓存也命中

文件新增/删除 → 包内文件集合变化 → Package Summary 必须重跑
             → 新旧 Package Summary 对比
             → 输出变了 → 重跑 Module Summary
             → 输出没变 → Module 缓存命中
```

**关键逻辑**：上层缓存失效条件不是"子级重跑了"，而是"子级输出确实变了"。小改动（bug fix、变量重命名）通常只在 File Summary 层重跑，不会向上传播。

**缓存键**：包/模块的缓存键 = 所有子级输出内容的组合 hash。子级输出不变 → 缓存键不变 → 缓存命中。

### 2.4 跨包依赖提取

Package Summary 输出中包含 `dependencies` 和 `dependents` 字段，这是知识图谱的直接数据源：

```json
{
  "packageName": "com.example.service",
  "dependencies": ["com.example.dao", "com.example.util"],
  "dependents": ["com.example.controller"],
  "coreClasses": ["OrderService", "PaymentService"],
  "description": "业务服务层，处理订单和支付核心逻辑"
}
```

Phase 7 知识图谱可直接消费此数据。

### 2.5 输出格式：双格式存盘

两端都存盘，分析报告作为**项目资产**，可共享、可版本管理、新人入职直接看。

**存储目录**（与项目路径一致）：

```
项目/.codelens/reports/com/example/
├── controller/
│   ├── UserController.json         ← 结构化数据（程序读，缓存/渲染/增量用）
│   ├── UserController.md           ← 人读文档（可直接纳入 git）
│   ├── OrderController.json
│   ├── OrderController.md
│   ├── _package.json               ← Package Summary 结构化
│   └── _package.md                 ← Package Summary 人读
├── service/
│   ├── UserService.json / .md
│   ├── OrderService.json / .md
│   ├── _package.json / _package.md
└── _module.json / _module.md       ← Module Summary
```

**双格式说明**：

| 格式 | 用途 | 说明 |
|------|------|------|
| `.json` | 程序读 | 插件端渲染、缓存校验、增量更新判断、ReportRenderer 输入 |
| `.md` | 人读 | 项目文档、code review 参考、新人 onboarding、可纳入 git |

**存盘行为**：
- CLI 端：分析完成后自动存盘（.json + .md）
- 插件端：分析完成后自动存盘到项目 `.codelens/reports/` 目录
- 插件端可打开已有 `.json` 报告直接渲染，不用重跑
- `.codelens/` 是否入 git 由团队自行决定（默认不加 `.gitignore`，团队可自选）

---

## 3. 改动清单

### 3.1 common 模块

| # | 改动 | 说明 |
|---|------|------|
| 1 | TaskType 新增 `AGGREGATE_SUMMARY` | 枚举值，带 level 参数（PACKAGE / MODULE） |
| 2 | 新增 `AggregateSummaryPrompt` | 通用聚合 Prompt，根据 level 切换指令模板 |
| 3 | 新增 `AggregateSummaryValidator` | 校验：包描述、核心类列表、依赖列表、职责划分 |
| 4 | 新增 `GroupingPrompt` | 轻量分类 Prompt，输入文件名+一句话摘要，输出分组表 |
| 5 | 新增 `GroupingValidator` | 校验分组表格式 |
| 6 | AgentRunner 新增 `runAggregate()` | 汇总调度：等子 Task 完成 → 分组 → 并行聚合 → 缓存 |
| 7 | GranularCache 扩展 | 支持 AGGREGATE_SUMMARY 类型缓存，缓存键 = 子级输出组合 hash |
| 8 | 新增 `AnalysisReport` 扩展 | 支持包级/模块级报告字段 |

### 3.2 CLI 模块

| # | 改动 | 说明 |
|---|------|------|
| 1 | 新增 `--module` CLI 参数 | 指定分析的包/模块路径 |
| 2 | 新增 `ReportWriter` | 将 AnalysisReport 写入镜像目录结构（.json + .md） |
| 3 | 递归扫描 + 调度逻辑 | 扫描包下所有 .java 文件，构建 DAG，调用 AgentRunner |

### 3.3 插件端（Phase 5 完成后）

| # | 改动 | 说明 |
|---|------|------|
| 1 | ReportRenderer 扩展 | 支持渲染包级/模块级摘要 |
| 2 | ReportWriter 复用 | 分析完成后自动存盘到项目 .codelens/reports/ |
| 3 | 包级导航 | ToolWindow 支持按包/模块切换视图 |
| 4 | 报告打开 | 可直接打开已有 .json 报告渲染，不用重跑 |

---

## 4. AGGREGATE_SUMMARY Schema

### 4.1 Package Summary 输出

```json
{
  "packageName": "com.example.service",
  "level": "PACKAGE",
  "description": "业务服务层，处理订单和支付核心逻辑",
  "responsibilities": [
    "订单创建、修改、取消全流程",
    "支付确认与退款处理",
    "库存预占与释放"
  ],
  "coreClasses": [
    {
      "className": "OrderService",
      "role": "核心业务编排",
      "keyMethods": ["createOrder", "cancelOrder"]
    }
  ],
  "auxiliaryClasses": ["OrderValidator", "PaymentConverter"],
  "dependencies": ["com.example.dao", "com.example.util"],
  "dependents": ["com.example.controller"],
  "riskIndicators": ["OrderService 单类 800+ 行，建议拆分"]
}
```

### 4.2 Module Summary 输出

```json
{
  "moduleName": "com.example",
  "level": "MODULE",
  "description": "订单管理系统后端，基于 Spring Boot + MyBatis",
  "architecture": "标准三层架构：Controller → Service → DAO",
  "subPackages": [
    {
      "packageName": "controller",
      "role": "REST 接口层",
      "coreClasses": 2
    },
    {
      "packageName": "service",
      "role": "业务逻辑层",
      "coreClasses": 3
    }
  ],
  "externalInterfaces": [
    "POST /api/orders — 创建订单",
    "POST /api/payments — 确认支付"
  ],
  "dependencies": ["com.shared.common"],
  "riskIndicators": ["service 包与 dao 包耦合度高，建议引入 DTO 层"]
}
```

---

## 5. AggregateSummaryPrompt 模板

### PACKAGE 级

```
你是一个 Java 代码架构分析师。以下是一个包内所有文件的摘要信息，请生成包级别的分析报告。

包名：{{packageName}}
文件数：{{fileCount}}

{{#summaries}}
---
文件：{{fileName}}
摘要：{{summary}}
---
{{/summaries}}

请输出 JSON，包含以下字段：
- packageName: 包名
- description: 一句话描述包的职责
- responsibilities: 包的核心职责列表（3-5条）
- coreClasses: 核心类列表（每个含 className、role、keyMethods）
- auxiliaryClasses: 辅助类列表（只需类名）
- dependencies: 依赖的其他包
- dependents: 被哪些包依赖
- riskIndicators: 风险提示
```

### MODULE 级

```
你是一个 Java 代码架构分析师。以下是一个模块内所有包的摘要信息，请生成模块级别的分析报告。

模块名：{{moduleName}}
包数：{{packageCount}}

{{#packageSummaries}}
---
包：{{packageName}}
描述：{{description}}
核心类：{{coreClasses}}
依赖：{{dependencies}}
被依赖：{{dependents}}
---
{{/packageSummaries}}

请输出 JSON，包含以下字段：
- moduleName: 模块名
- description: 一句话描述模块的定位
- architecture: 模块的架构风格
- subPackages: 子包列表（每个含 packageName、role、coreClasses 数量）
- externalInterfaces: 对外暴露的接口
- dependencies: 依赖的外部模块
- riskIndicators: 风险提示
```

---

## 6. AgentRunner 调度逻辑

```java
public AnalysisReport runModuleAnalysis(String modulePath) {
    // 1. 递归扫描所有 .java 文件，按包分组
    Map<String, List<JavaFile>> packageFiles = scanModule(modulePath);
    
    // 2. 并行执行所有文件的 SUMMARY task
    List<AnalysisTask> fileTasks = packageFiles.values().stream()
        .flatMap(List::stream)
        .map(f -> createSummaryTask(f))
        .collect(Collectors.toList());
    agentRunner.runAll(fileTasks);  // 并行 + 缓存
    
    // 3. 同包内 File Summary 全部完成后，触发 Package Summary
    List<AnalysisTask> packageTasks = packageFiles.entrySet().stream()
        .map(e -> createAggregateTask(e.getKey(), PACKAGE, e.getValue()))
        .collect(Collectors.toList());
    agentRunner.runAll(packageTasks);  // 并行 + 分组聚合 + 缓存
    
    // 4. 所有 Package Summary 完成后，触发 Module Summary
    AnalysisTask moduleTask = createAggregateTask(modulePath, MODULE, packageTasks);
    agentRunner.run(moduleTask);
    
    // 5. 合并所有层级报告
    return ReportMerger.mergeAll(fileTasks, packageTasks, moduleTask);
}

private AnalysisTask createAggregateTask(String name, SummaryLevel level, List<?> childOutputs) {
    // 根据子级数量决定分组策略
    if (childOutputs.size() <= 10) {
        // 直接聚合
        return AnalysisTask.builder()
            .taskType(AGGREGATE_SUMMARY)
            .level(level)
            .inputs(childOutputs)
            .build();
    } else {
        // Map-Reduce: 先分组 → 中间摘要 → 聚合
        List<List<?>> groups = groupByClassification(childOutputs);
        return AnalysisTask.builder()
            .taskType(AGGREGATE_SUMMARY)
            .level(level)
            .inputs(childOutputs)
            .groups(groups)
            .build();
    }
}
```

### 增量更新调度

```java
// 文件修改时的增量逻辑
public void onFileChanged(JavaFile file) {
    // 1. 重跑 File Summary（必须）
    String oldSummary = cache.get(file.getCacheKey());
    String newSummary = agentRunner.run(createSummaryTask(file));
    
    // 2. 对比输出是否变化
    if (oldSummary.equals(newSummary)) {
        return; // 没变，上层缓存全部命中
    }
    
    // 3. 输出变了 → 重跑 Package Summary
    String pkgKey = file.getPackageName();
    String oldPkgSummary = cache.get(pkgKey);
    String newPkgSummary = agentRunner.runAggregate(pkgKey, PACKAGE, collectChildSummaries(pkgKey));
    
    // 4. Package Summary 是否变化
    if (oldPkgSummary.equals(newPkgSummary)) {
        return; // Package 没变，Module 缓存命中
    }
    
    // 5. Package 变了 → 重跑 Module Summary
    agentRunner.runAggregate(moduleKey, MODULE, collectChildSummaries(moduleKey));
}

// 文件增删：包内文件集合变化，Package Summary 必须重跑
public void onFileAddedOrRemoved(JavaFile file) {
    agentRunner.runAggregate(file.getPackageName(), PACKAGE, collectChildSummaries(file.getPackageName()));
    // 然后同上检查 Package 输出是否变化，决定是否重跑 Module
}
```

---

## 7. ReportWriter 输出

### 7.1 目录结构

```
项目/.codelens/reports/com/example/
├── controller/
│   ├── UserController.json         ← 结构化数据
│   ├── UserController.md           ← 人读文档
│   ├── OrderController.json
│   ├── OrderController.md
│   ├── _package.json
│   └── _package.md
├── service/
│   ├── UserService.json / .md
│   ├── OrderService.json / .md
│   ├── _package.json / .package.md
└── _module.json / _module.md
```

- `_package.*` / `_module.*` 下划线前缀，和文件级报告区分
- CLI `--module com.example.controller` 指定分析范围，递归产出所有层级

### 7.2 Markdown 输出示例

**UserController.md（File Summary）**

```markdown
# UserController

**包：** com.example.controller  
**核心职责：** 订单和支付相关的 REST 接口

## 核心方法

| 方法 | 职责 | 风险 |
|------|------|------|
| createOrder | 创建订单 | ⚠️ 缺少参数校验 |
| confirmPayment | 确认支付 | — |

## 调用链
- createOrder → OrderService.createOrder
- confirmPayment → PaymentService.confirm
```

**_package.md（Package Summary）**

```markdown
# com.example.controller

**层级：** Package  
**描述：** REST 接口层，承接前端请求并委派给 Service 层

## 核心类
- **UserController** — 订单/支付接口，项目主入口
- **SysDimenController** — 系统配置接口

## 依赖
- → com.example.service（业务逻辑）
- → com.example.util（工具类）

## 被依赖
- 无（顶层入口）

## 风险提示
- ⚠️ UserController 单类承担多职责，建议拆分
```

**_module.md（Module Summary）**

```markdown
# com.example

**层级：** Module  
**描述：** 订单管理系统后端，标准三层架构

## 架构
Controller → Service → DAO

## 子包概览

| 包 | 职责 | 核心类数 |
|----|------|---------|
| controller | REST 接口层 | 2 |
| service | 业务逻辑层 | 3 |
| dao | 数据访问层 | 4 |

## 对外接口
- POST /api/orders — 创建订单
- POST /api/payments — 确认支付

## 风险提示
- ⚠️ service 与 dao 耦合度高，建议引入 DTO
```

---

## 8. 验收标准

- [ ] TaskType.AGGREGATE_SUMMARY 可用，支持 PACKAGE/MODULE level
- [ ] AggregateSummaryPrompt 根据 level 生成不同模板
- [ ] AggregateSummaryValidator 校验所有必填字段
- [ ] ≤10 文件的包：直接聚合，1 次 LLM 调用
- [ ] 11~50 文件的包：一级 Map-Reduce，分组并行
- [ ] >50 文件的包：两级 Map-Reduce
- [ ] 增量更新：文件修改时逐层检查输出是否变化，不变则跳过上层重跑
- [ ] 文件增删时 Package Summary 强制重跑
- [ ] 缓存键 = 子级输出组合 hash，子级输出不变则缓存命中
- [ ] CLI `--module` 参数可用，输出镜像目录结构的 .json + .md 文件
- [ ] 插件端分析完成后自动存盘到项目 .codelens/reports/
- [ ] 插件端可打开已有 .json 报告直接渲染
- [ ] Package Summary 包含 dependencies/dependents 字段
- [ ] 全部测试通过

---

## 9. Claude Code 任务描述

```
在 codelens-java 中实现多Agent流水线 Phase 6：包级/模块级聚合 Summary。

仓库：https://github.com/fiendchenmo/codelens-java.git
分支：从 main 创建 feature/multi-agent-phase6

前置依赖：Phase 1-4 已合入 main，Phase 5 在插件端进行，本分支只改 common + CLI

需要做的改动（common 模块）：

1. 修改 TaskType 枚举
   - 新增 AGGREGATE_SUMMARY（String level 字段，值为 "PACKAGE" 或 "MODULE"）
   - 关联 AggregateSummaryPrompt.class + AggregateSummaryValidator.class

2. 新增 SummaryLevel 枚举
   - 包：com.codelens.common.agent
   - PACKAGE, MODULE

3. 新增 AggregateSummaryPrompt.java
   - 包：com.codelens.common.agent
   - 两个模板方法：buildPackagePrompt(packageName, fileSummaries) 和 buildModulePrompt(moduleName, packageSummaries)
   - 模板内容见需求文档第 5 节
   - fileSummaries/packageSummaries 以分隔符格式传入

4. 新增 AggregateSummaryValidator.java
   - 包：com.codelens.common.agent
   - validate(String json, SummaryLevel level)：
     - PACKAGE: 必填 packageName, description, coreClasses, dependencies
     - MODULE: 必填 moduleName, description, architecture, subPackages
   - 返回 ValidationResult

5. 新增 GroupingPrompt.java
   - 轻量分类 Prompt：输入文件名+一句话摘要列表，输出分组表 JSON
   - 输出格式：[{"group":"业务逻辑","files":["OrderService","PaymentService"]}, ...]

6. 新增 GroupingValidator.java
   - 校验分组表格式：每个 group 有 name 和 files 数组

7. 修改 AgentRunner.java
   - 新增方法：runAggregate(AnalysisTask task)
   - 逻辑：
     a. 获取子级输出列表（File Summary 或 Package Summary）
     b. 如果子级数 ≤ 10：直接用 AggregateSummaryPrompt 聚合
     c. 如果子级数 > 10：
        - 先跑 GroupingPrompt 分类
        - 按分组并行跑中间摘要
        - 再用 AggregateSummaryPrompt 聚合中间摘要
     d. 缓存键 = DigestUtils.sha256Hex(所有子级输出拼接)
     e. 缓存命中直接返回
   - 新增增量更新方法：onFileChanged / onFileAddedOrRemoved
     - 文件修改：重跑 File Summary → 对比输出 → 变了才重跑 Package → 对比 → 变了才重跑 Module
     - 文件增删：Package Summary 强制重跑 → 对比输出 → 变了才重跑 Module

8. 修改 GranularCache / GranularCacheAdapter
   - 支持 AGGREGATE_SUMMARY 类型的缓存读写
   - 缓存键格式：aggregate:{level}:{sha256}

9. 修改 AnalysisReport
   - 新增 packageSummaries 字段（List<PackageSummary>）
   - 新增 moduleSummary 字段（ModuleSummary）
   - 新增 PackageSummary 和 ModuleSummary 数据类

10. 新增单测
    - AggregateSummaryPromptTest: 验证两个 level 的 Prompt 生成
    - AggregateSummaryValidatorTest: 验证校验逻辑（必填字段缺失、格式错误）
    - GroupingPromptTest / GroupingValidatorTest
    - AgentRunnerAggregateTest: 验证调度逻辑（≤10 直接聚合、>10 分组聚合、增量更新）
    - 缓存命中/失效测试

需要做的改动（CLI 模块）：

11. 新增 CLI 参数 --module
    - 用法：--module com.example.service
    - 扫描该模块下所有 .java 文件

12. 新增 ReportWriter.java
    - 包：com.codelens.cli
    - 将 AnalysisReport 写入 .codelens/reports/ 目录
    - 双格式输出：
      - .json：结构化数据，使用 Gson 格式化
      - .md：人读 Markdown，按需求文档第 7.2 节模板
    - File Summary → {ClassName}.json + {ClassName}.md
    - Package Summary → _package.json + _package.md
    - Module Summary → _module.json + _module.md
    - 目录结构与源码包路径镜像

13. 修改 CodeLensCli.java
    - 解析 --module 参数
    - 调用 AgentRunner.runModuleAnalysis()
    - 调用 ReportWriter 输出

注意事项：
- 不修改已有的 SUMMARY / METHOD_ANALYSIS 相关代码，只新增 AGGREGATE_SUMMARY
- AggregateSummaryPrompt 的模板文本用常量或资源文件，不要硬编码在逻辑里
- GroupingPrompt 只做一次轻量 LLM 调用，输入是文件名+一行摘要，不是完整 Summary
- 缓存键必须包含所有子级输出的 hash，确保任一子级变化上级缓存失效
- 增量更新逻辑：上层缓存失效条件是"子级输出确实变了"，不是"子级重跑了"
- 所有新增类放在 com.codelens.common.agent 包下
- 编译通过 + 全部测试通过后 push
```
