# 需求：Phase6.5 — Diff 影响分析（Impact Diffusion）

> **版本：** v1.0
> **状态：** 待执行
> **优先级：** 🔴 P1
> **编写人：** 喵呜
> **日期：** 2026-06-01
> **目标模块：** codelens-common + codelens-cli
> **前置依赖：** Phase6 包级聚合Summary + CallIndex 调用索引

---

## 1. 要解决什么问题

PR 审查时，reviewer 需要知道「改了这个文件/方法，还会影响哪些东西」。目前 CodeLens 能分析单个文件/方法，但缺少**变更传播**视角——从 git diff 出发，沿调用链和依赖关系扩散，标注所有受影响的节点。

核心场景：
- PR 提交后，快速定位变更的**影响范围**
- reviewer 重点审查受影响的高风险节点，而非逐文件通读
- CI 集成：影响范围超阈值时自动告警

## 2. 整体框架（一页纸看懂）

```
git diff → DiffParser → 变更点列表(ChangedFile[])
                                    ↓
                          ImpactAnalyzer
                          ├─ 方法级扩散：CallIndex.queryByCallee()
                          └─ 包级扩散：CrossPackageDep 依赖链
                                    ↓
                          ImpactReport
                          ├─ 直接影响（调用变更方法的方法）
                          ├─ 间接影响（2~N跳扩散）
                          ├─ 影响节点标注（HIGH/MEDIUM/LOW）
                          └─ 影响路径回溯
```

## 3. 数据模型

### 3.1 ChangedFile — 变更文件

```java
public class ChangedFile {
    private String filePath;         // src/main/java/com/example/Service.java
    private String className;        // com.example.Service
    private ChangeType changeType;   // ADDED, MODIFIED, DELETED
    private List<ChangedMethod> changedMethods;
}

public enum ChangeType {
    ADDED, MODIFIED, DELETED
}
```

### 3.2 ChangedMethod — 变更方法

```java
public class ChangedMethod {
    private String className;
    private String methodName;
    private String signature;       // 完整签名，用于 CallIndex 匹配
    private ChangeType changeType;
    private int oldStartLine;       // 旧版本行号（MODIFIED/DELETED）
    private int newStartLine;       // 新版本行号（ADDED/MODIFIED）
}
```

### 3.3 ImpactNode — 受影响节点

```java
public class ImpactNode {
    private String className;
    private String methodName;      // 方法级影响时非空，文件级为空
    private ImpactLevel level;      // DIRECT, INDIRECT
    private ImpactConfidence confidence; // HIGH, MEDIUM, LOW
    private int hopDistance;        // 距离变更点的跳数（1=直接影响）
    private List<String> impactPath; // 回溯路径：A→B→C
    private ArchitectureLayer layer;
}

public enum ImpactLevel {
    DIRECT,    // 直接调用变更方法
    INDIRECT   // 通过1+中间节点间接影响
}

public enum ImpactConfidence {
    HIGH,      // 静态调用链确认
    MEDIUM,    // 跨包依赖推断
    LOW        // 弱依赖（Spring注入/反射）
}
```

### 3.4 ImpactReport — 影响分析报告

```java
public class ImpactReport {
    private String commitHash;           // 对比基准commit
    private List<ChangedFile> changes;   // 变更点
    private List<ImpactNode> impacts;    // 受影响节点
    private ImpactSummary summary;       // 摘要统计
}

public class ImpactSummary {
    private int totalChangedFiles;
    private int totalChangedMethods;
    private int directImpactCount;
    private int indirectImpactCount;
    private Map<ArchitectureLayer, Integer> impactedLayerDist;
    private List<String> highRiskPaths;   // 影响路径中最需关注的Top5
}
```

## 4. 核心组件

### 4.1 DiffParser — git diff 解析器

**职责**：解析 `git diff` 输出，提取变更文件和方法列表。

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | `parseDiff(String diffOutput)` | 解析 unified diff 格式，返回 `List<ChangedFile>` |
| 2 | `parseDiffFromGit(Path repoPath, String baseCommit)` | 直接调用 `git diff`，获取 diff 后解析 |
| 3 | 方法级变更识别 | 通过 hunk header `@@ -a,b +c,d @@` 定位变更行，与 JavaParser 方法行号范围匹配 |
| 4 | 新增文件识别 | `--- /dev/null` → ADDED |
| 5 | 删除文件识别 | `+++ /dev/null` → DELETED |
| 6 | 纯格式变更过滤 | 空行/注释变更/仅import变更标记为 LOW，不参与影响扩散 |

**约束**：
- 仅处理 `.java` 文件，忽略其他文件类型
- 二进制文件和重命名文件标记后跳过
- JDK1.8 兼容

### 4.2 ImpactAnalyzer — 影响扩散引擎

**职责**：从变更点出发，沿调用链和依赖关系扩散，生成影响报告。

#### 4.2.1 扩散算法

```
输入：List<ChangedFile>, CallIndex, int maxHops
输出：List<ImpactNode>

算法（BFS 扩散）：
1. 初始化队列 Q = 所有变更方法
2. visited = Set<方法签名>
3. hop = 0
4. while Q 非空 && hop < maxHops:
   a. 取出当前层所有节点
   b. 对每个节点 queryByCallee() → 找到调用方
   c. 过滤已访问节点
   d. 标记 ImpactLevel (hop==0→DIRECT, else→INDIRECT)
   e. 标记 ImpactConfidence (callType→confidence映射)
   f. 记录 impactPath
   g. 新节点入队，hop++
5. 包级扩散：CrossPackageDep 补充跨包间接影响
```

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | 方法级扩散（BFS） | 从变更方法出发，`queryByCallee()` 反向查找调用方 |
| 2 | 最大跳数限制 | 默认 `maxHops=3`，防止扩散失控 |
| 3 | 置信度映射 | DIRECT→HIGH, SPRING_INJECTION→MEDIUM, REFLECTION→LOW |
| 4 | 包级扩散 | 方法级完成后，通过 CrossPackageDep 补充跨包间接影响 |
| 5 | 影响路径记录 | 每个受影响节点记录完整路径 `A.method→B.method→C.method` |
| 6 | 去重 | 同一方法从多条路径到达时，保留最短路径 |

#### 4.2.2 影响评级

| 条件 | 评级 | 说明 |
|------|------|------|
| hop=1 && confidence=HIGH | 🔴 HIGH | 直接调用，高风险 |
| hop=1 && confidence≤MEDIUM | 🟡 MEDIUM | 直接调用但弱依赖 |
| hop=2 && confidence=HIGH | 🟡 MEDIUM | 间接但高置信 |
| hop≥3 或 confidence=LOW | 🟢 LOW | 间接且低置信 |

### 4.3 ImpactReportWriter — 报告输出

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | JSON 输出 | `impact_report.json`，完整 ImpactReport 结构 |
| 2 | Markdown 输出 | `impact_report.md`，人类可读的审查报告 |
| 3 | CLI 输出 | 控制台彩色输出，变更点 + 影响摘要 + Top5 高风险路径 |
| 4 | 影响热力图数据 | 按包聚合影响节点数，输出 `impact_heatmap.json` 供可视化 |

**Markdown 报告模板**：
```markdown
# Impact Report

## 变更概览
- 基准 Commit: {commitHash}
- 变更文件: {totalChangedFiles}
- 变更方法: {totalChangedMethods}

## 变更列表
| 文件 | 方法 | 变更类型 |
|------|------|----------|
| Service.java | processOrder | MODIFIED |

## 影响分析
- 直接影响: {directImpactCount}
- 间接影响: {indirectImpactCount}
- 影响层分布: CONTROLLER(3), SERVICE(5), HANDLER(2)

### 🔴 高风险影响路径
1. OrderService.processOrder → OrderController.submit
2. ...

### 🟡 中风险影响路径
1. ...

## 影响热力图
| 包 | 直接影响 | 间接影响 | 总计 |
|----|----------|----------|------|
| com.example.service | 3 | 5 | 8 |
```

## 5. CLI 集成

### 5.1 新增命令

```
java -jar codelens.jar diff --source=<项目目录> --base=<基准commit或分支> [--max-hops=3] [--format=json|md|console]
```

### 5.2 参数说明

| 参数 | 必填 | 说明 |
|------|------|------|
| `--source` | 是 | 项目根目录 |
| `--base` | 是 | 基准commit hash 或分支名（如 `main`, `HEAD~3`） |
| `--max-hops` | 否 | 最大扩散跳数，默认3 |
| `--format` | 否 | 输出格式：json/md/console，默认console |
| `--api-key` | 否 | LLM API Key（本阶段不需要LLM，预留） |

### 5.3 执行流程

1. 验证 `--source` 是 git 仓库
2. 执行 `git diff <base> HEAD` 获取变更
3. DiffParser 解析变更
4. 加载 CallIndex（`.codelens/callindex.db`）
5. ImpactAnalyzer 扩散分析
6. 输出报告

## 6. 关键决策点

| # | 决策 | 推荐方案 | 理由 | 备选 |
|---|------|----------|------|------|
| 1 | CallIndex 缺失时怎么办 | 降级为文件级分析（只列出变更文件，不扩散） | 用户体验优先，不能报错 | 报错要求先建索引 |
| 2 | maxHops 默认值 | 3 | 2跳太浅（漏间接影响），4+跳噪音太多 | 可配置 |
| 3 | 是否需要 LLM | Phase6.5 不需要 | 纯静态分析，调用链扩散是确定性算法 | Phase7 RefactorAdvisor 时引入LLM |
| 4 | 新增文件处理 | 标记 ADDED，不参与反向扩散（没有调用方查它） | 新增方法是叶子节点，只有正向影响 | 对新增的public方法提示"可能被外部调用" |
| 5 | 删除文件处理 | 标记 DELETED，参与反向扩散（调用方会编译失败） | 删除影响最大 | — |

## 7. 风险项 + 缓解措施

| # | 风险 | 概率 | 影响 | 缓解 |
|---|------|------|------|------|
| 1 | CallIndex 不完整（未全量索引） | 高 | 遗漏部分影响节点 | 报告中标注"索引覆盖率：X/Y文件"，低覆盖率时警告 |
| 2 | Spring注入/反射调用无法静态追踪 | 中 | 间接影响漏报 | 标记 LOW 置信度，报告中提示"可能存在隐式依赖" |
| 3 | 大型仓库 diff 很大（几百文件变更） | 中 | 扩散节点爆炸 | maxHops 限制 + 变更文件超50时自动降级为包级分析 |
| 4 | git diff 解析边界case | 低 | 解析错误 | 充分测试用例覆盖二进制/重命名/空diff等 |
| 5 | 接口/实现类映射 | 中 | 接口变更但查不到实现类调用 | 复用接口穿透逻辑（Phase5已完成） |

## 8. MVP 路径

**最快跑通方案**：3步交付

| 步骤 | 内容 | 预估 | 验收标准 |
|------|------|------|----------|
| Step 1 | DiffParser + ChangedFile/ChangedMethod 数据模型 | 1天 | 解析 git diff 输出，正确识别变更文件和方法 |
| Step 2 | ImpactAnalyzer BFS 扩散 + CallIndex 集成 | 1天 | 从变更方法出发3跳扩散，输出 ImpactNode 列表 |
| Step 3 | ImpactReportWriter + CLI `diff` 命令 | 0.5天 | 控制台输出影响报告，JSON/Markdown 可选 |

**MVP 验收**：对 codelens-java 自身执行 `diff --base=v0.4.0`，输出 Phase5~6 的变更影响分析。

## 9. 与现有组件的接口

| 组件 | 接口 | 方向 |
|------|------|------|
| CallIndex | `queryByCallee(className, methodName)` | 读取 |
| CallIndex | `queryByCaller(className, methodName)` | 读取 |
| CrossPackageDep | `targetPackage`, `viaMethods`, `direction` | 读取 |
| ArchitectureLayerDetector | `detectClassLayer()`, `detectPackageLayer()` | 读取 |
| AggregateSummaryOutput | `totalFiles`, `riskOverview` 等字段 | 读取（补充上下文） |

## 10. 不在范围内

- LLM 辅助影响分析（Phase7）
- 自动生成修复建议（Phase7）
- CI 集成 webhook（独立功能）
- 插件端内 diff 视图（插件端 Phase7）
