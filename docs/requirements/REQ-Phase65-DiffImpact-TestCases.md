# Phase6.5 Diff 影响分析 — 测试用例

> **版本：** v1.0
> **编写人：** 喵呜
> **日期：** 2026-06-01

---

## 1. DiffParser 测试

### TC-DP-01：解析单文件 MODIFIED

**输入**：
```diff
diff --git a/src/Service.java b/src/Service.java
--- a/src/Service.java
+++ b/src/Service.java
@@ -10,7 +10,7 @@
-    public void processOrder() {
+    public void processOrder(Order order) {
```

**期望**：
- `changedFiles.size() == 1`
- `filePath == "src/Service.java"`
- `changeType == MODIFIED`
- `changedMethods` 包含 `processOrder`

### TC-DP-02：解析新增文件

**输入**：
```diff
diff --git a/src/NewService.java b/src/NewService.java
--- /dev/null
+++ b/src/NewService.java
@@ -0,0 +1,20 @@
+public class NewService {
```

**期望**：
- `changeType == ADDED`
- `changedMethods` 为空（新文件整体新增，不标记方法级）

### TC-DP-03：解析删除文件

**输入**：
```diff
diff --git a/src/OldService.java b/src/OldService.java
--- a/src/OldService.java
+++ /dev/null
@@ -1,30 +0,0 @@
-public class OldService {
```

**期望**：
- `changeType == DELETED`

### TC-DP-04：多文件变更

**输入**：包含 3 个文件 diff 的字符串

**期望**：
- `changedFiles.size() == 3`
- 每个文件的 changeType 正确

### TC-DP-05：空 diff

**输入**：空字符串

**期望**：
- `changedFiles.isEmpty()`
- 不抛异常

### TC-DP-06：非 Java 文件过滤

**输入**：包含 `.xml`, `.properties`, `.java` 文件变更的 diff

**期望**：
- `changedFiles.size() == 1`（只保留 .java）

### TC-DP-07：纯 import 变更

**输入**：
```diff
-import java.util.List;
+import java.util.ArrayList;
```

**期望**：
- 文件标记为 `changeType == MODIFIED`
- 但变更方法列表为空（import 变更不产生方法级影响）

### TC-DP-08：方法行号匹配

**输入**：hunk 显示修改了第 15-20 行

**前置**：JavaParser 解析出 `processOrder()` 在第 12-25 行

**期望**：
- `changedMethods` 包含 `processOrder`
- `newStartLine == 15`

### TC-DP-09：二进制文件跳过

**输入**：
```diff
diff --git a/lib.jar b/lib.jar
Binary files differ
```

**期望**：
- 不出现在 changedFiles 中

### TC-DP-10：从 git 仓库直接解析

**输入**：`parseDiffFromGit(repoPath, "HEAD~3")`

**期望**：
- 执行 `git diff HEAD~3 HEAD`
- 返回最近3个commit的变更

---

## 2. ImpactAnalyzer 测试

### TC-IA-01：单方法直接影响

**前置**：
- `OrderService.processOrder()` 变更
- CallIndex 中存在 `OrderController.submit() → OrderService.processOrder()` (DIRECT)

**期望**：
- `impacts.size() == 1`
- `OrderController.submit` 被标记为 DIRECT + HIGH
- `hopDistance == 1`
- `impactPath == ["OrderService.processOrder", "OrderController.submit"]`

### TC-IA-02：多跳间接影响

**前置**：
- `ServiceA.methodA()` 变更
- 链路：`ServiceB.methodB() → ServiceA.methodA()` (hop1)
- 链路：`ControllerC.handle() → ServiceB.methodB()` (hop2)

**期望**：
- `ServiceB.methodB`: DIRECT, hop=1, HIGH
- `ControllerC.handle`: INDIRECT, hop=2, MEDIUM
- 两条路径正确记录

### TC-IA-03：maxHops 限制

**前置**：4跳调用链

**配置**：`maxHops=2`

**期望**：
- 只返回 hop≤2 的节点
- hop=3,4 的节点不在结果中

### TC-IA-04：去重 — 多路径到达同一方法

**前置**：
- `ServiceA.methodA()` 变更
- 路径1：`B.m1 → A.methodA` (hop1)
- 路径2：`C.m2 → B.m1 → A.methodA` (hop2)
- 路径3：`D.m3 → A.methodA` (hop1)

**期望**：
- `B.m1` 只出现1次，标记 DIRECT, hop=1
- `C.m2` 只出现1次，标记 INDIRECT, hop=2
- `D.m3` 只出现1次，标记 DIRECT, hop=1
- 每个节点保留最短路径

### TC-IA-05：Spring 注入依赖置信度

**前置**：`OrderService.processOrder() → PaymentService.pay()` (SPRING_INJECTION)

**期望**：
- `PaymentService` 调用方标记 confidence=MEDIUM（而非 HIGH）

### TC-IA-06：反射调用置信度

**前置**：`Invoker.invoke() → Target.execute()` (REFLECTION)

**期望**：
- 调用方标记 confidence=LOW

### TC-IA-07：新增文件不参与反向扩散

**前置**：`NewService.process()` 标记 ADDED

**期望**：
- `queryByCallee("NewService", "process")` 返回空（没有人调用新增方法）
- 报告中 NewService 出现在变更列表但不出现在影响节点中

### TC-IA-08：删除文件参与反向扩散

**前置**：`OldService.process()` 标记 DELETED

**前置 CallIndex**：`OrderController.submit() → OldService.process()` (DIRECT)

**期望**：
- `OrderController.submit` 被标记为受影响（HIGH）
- 影响路径包含 "DELETED" 标注

### TC-IA-09：CallIndex 缺失降级

**前置**：项目目录下没有 `.codelens/callindex.db`

**期望**：
- 不抛异常
- 降级为文件级分析（只输出变更文件列表，无扩散）
- 报告标注"CallIndex 缺失，仅文件级分析"

### TC-IA-10：大变更量降级

**前置**：变更文件数 > 50

**期望**：
- 自动从方法级降级为包级分析
- 报告标注"变更量过大（N文件），已降级为包级分析"

### TC-IA-11：包级扩散

**前置**：
- `com.example.service.OrderService` 变更
- CrossPackageDep: `com.example.controller → com.example.service` (outgoing, viaMethods: [submit])

**期望**：
- controller 包被标记为受影响
- `impactPath` 包含包级依赖路径

### TC-IA-12：接口穿透

**前置**：
- `IOrderService.processOrder()` 接口方法变更
- CallIndex 中只有 `OrderController.submit() → IOrderService.processOrder()` 
- 但实际运行时走的是 `OrderServiceImpl.processOrder()`

**期望**：
- 通过接口穿透逻辑，同时标记：
  - 调用接口的 `OrderController.submit`
  - 实现类 `OrderServiceImpl.processOrder` 的调用方

---

## 3. ImpactReportWriter 测试

### TC-RW-01：JSON 输出完整性

**输入**：包含3个变更文件、5个影响节点的 ImpactReport

**期望**：
- JSON 包含所有字段：commitHash, changes, impacts, summary
- summary.totalChangedFiles == 3
- summary.directImpactCount + indirectImpactCount == 5
- impactedLayerDist 包含层分布

### TC-RW-02：Markdown 输出格式

**输入**：同 TC-RW-01

**期望**：
- 包含"变更概览"、"变更列表"、"影响分析"三个章节
- 高风险路径 🔴 标记
- 表格格式正确

### TC-RW-03：空影响报告

**输入**：0变更

**期望**：
- 输出"No changes detected"
- 不抛异常

### TC-RW-04：影响热力图数据

**输入**：多包受影响的 ImpactReport

**期望**：
- `impact_heatmap.json` 包含每个包的直接/间接影响计数
- 按总影响数降序排列

---

## 4. CLI 集成测试

### TC-CLI-01：基本 diff 命令

**命令**：`java -jar codelens.jar diff --source=/path/to/repo --base=HEAD~1`

**期望**：
- 控制台输出变更概览 + 影响分析
- 退出码 0

### TC-CLI-02：非 git 仓库

**命令**：`java -jar codelens.jar diff --source=/tmp --base=HEAD~1`

**期望**：
- 输出错误信息"不是有效的 git 仓库"
- 退出码 1

### TC-CLI-03：无效 base commit

**命令**：`java -jar codelens.jar diff --source=/path/to/repo --base=nonexistent`

**期望**：
- 输出错误信息"无效的基准commit"
- 退出码 1

### TC-CLI-04：JSON 格式输出

**命令**：`java -jar codelens.jar diff --source=/path/to/repo --base=HEAD~1 --format=json`

**期望**：
- 输出合法 JSON
- 包含完整 ImpactReport 结构

### TC-CLI-05：max-hops 参数

**命令**：`java -jar codelens.jar diff --source=/path/to/repo --base=HEAD~1 --max-hops=1`

**期望**：
- 只输出直接影响（hop=1）
- 不包含间接影响

### TC-CLI-06：MVP 验收 — 对 codelens-java 自身执行

**命令**：`java -jar codelens.jar diff --source=/path/to/codelens-java --base=v0.4.0`

**期望**：
- 检测到 Phase5-6 的变更（AgentRunner, AggregateSummaryAgent 等新增/修改）
- 输出影响分析报告
- CodeLensCli 被 AgentRunner 等类间接影响

---

## 5. 边界与异常测试

### TC-EDGE-01：超大 diff（1000+ 文件）

**期望**：
- 自动降级为包级分析
- 不 OOM，不超时

### TC-EDGE-02：CallIndex 部分覆盖

**前置**：CallIndex 只索引了项目 60% 的文件

**期望**：
- 正常分析已索引部分
- 报告标注"索引覆盖率：60%"
- 低覆盖率警告

### TC-EDGE-03：重命名文件

**输入**：git diff 显示 `rename from A.java to B.java`

**期望**：
- 标记为 MODIFIED（语义等同）
- 不作为 ADDED + DELETED 处理

### TC-EDGE-04：merge commit diff

**输入**：`git diff` 包含 merge conflict 标记

**期望**：
- 跳过 conflict markers
- 只处理已解决的部分
