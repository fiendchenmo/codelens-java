# REQ-C9 测试用例 — CallIndex 迁移 Phase 2

> 编号：REQ-C9-TestCases
> 对应需求：REQ-C9
> 测试目标：CallIndex 接口 + SQLiteCallIndex 实现 + CallIndexManager

---

## 一、测试维度

| 维度 | 覆盖点 | 用例数 |
|------|--------|--------|
| A1 CRUD 基本 | insert / queryByCaller / queryByCallee / deleteByFile / close | 8 |
| A2 增量更新 | 文件变更→删旧→重新插入→结果正确 | 3 |
| A3 持久化 | 关闭后重开，数据仍可用 | 2 |
| A4 并发安全 | 多线程并发读写不丢数据 | 2 |
| A5 边界条件 | 空表查询 / 大批量插入 / 重复插入 / null字段 | 4 |
| A6 CallIndexManager | 生命周期管理 | 3 |

**总计：22**

---

## 二、完整测试用例清单

### A1 — CRUD 基本

| # | 测试方法 | 测试内容 | 验证点 |
|---|----------|----------|--------|
| 1 | testInsertAndQueryByCaller | 插入 CallRecord 后按 caller 查询 | queryByCaller 返回匹配记录，字段值正确（callerClass/callerMethod/calleeClass/calleeMethod/callType/lineNumber） |
| 2 | testQueryByCaller_noMatch | 查询不存在的 caller | 返回空列表，不抛异常 |
| 3 | testInsertAndQueryByCallee | 插入 CallRecord 后按 callee 查询 | queryByCallee 返回匹配记录 |
| 4 | testQueryByCallee_multipleRecords | 同一 callee 被多处调用 | 返回所有调用方，按行号排序 |
| 5 | testDeleteByFile | 删除某文件的所有记录后查询 | 该文件相关记录为空，其他文件记录不受影响 |
| 6 | testDeleteByFile_nonExistent | 删除不存在的文件 | 不抛异常，其他数据不变 |
| 7 | testInsert_multipleCallTypes | 插入 DIRECT / REFLECTION / SPRING_INJECTION 三种类型 | callType 字段正确存储和查询 |
| 8 | testClose_andReopen | close 后 reopen 数据库 | 无异常（验证 close 不损坏数据库文件） |

### A2 — 增量更新

| # | 测试方法 | 测试内容 | 验证点 |
|---|----------|----------|--------|
| 9 | testIncrementalUpdate_modifyFile | 修改文件后重新索引 | 旧记录被清除，新记录正确插入，无残留 |
| 10 | testIncrementalUpdate_addNewCall | 文件新增方法调用后重新索引 | 新调用出现在查询结果中 |
| 11 | testIncrementalUpdate_removeCall | 文件删除方法调用后重新索引 | 被删除的调用不再出现在查询结果中 |

### A3 — 持久化

| # | 测试方法 | 测试内容 | 验证点 |
|---|----------|----------|--------|
| 12 | testPersistence_closeAndReopen | 插入数据→close→重新创建 SQLiteCallIndex→查询 | 数据完整存在，字段值一致 |
| 13 | testPersistence_afterIncrementalUpdate | 增量更新后关闭重开 | 增量更新后的数据持久化正确 |

### A4 — 并发安全

| # | 测试方法 | 测试内容 | 验证点 |
|---|----------|----------|--------|
| 14 | testConcurrentWrite | 10个线程同时 insert 各100条 | 总记录数=1000，无丢失，无异常 |
| 15 | testConcurrentReadWrite | 写入线程 insert + 读取线程 queryByCaller 交替执行 | 读取不抛异常，最终数据完整 |

### A5 — 边界条件

| # | 测试方法 | 测试内容 | 验证点 |
|---|----------|----------|--------|
| 16 | testQuery_emptyTable | 空表查询 caller/callee | 返回空列表，不抛异常 |
| 17 | testInsert_batchLargeVolume | 批量插入 10000 条记录 | 插入成功，查询结果数量正确 |
| 18 | testInsert_duplicateRecord | 插入两条完全相同的 CallRecord | 两条都存在（允许重复，不自动去重） |
| 19 | testInsert_nullOptionalFields | confidence 字段为 null | 插入成功，查询时 confidence 为 null |

### A6 — CallIndexManager

| # | 测试方法 | 测试内容 | 验证点 |
|---|----------|----------|--------|
| 20 | testManager_lifecycle | create → getIndex → close | getIndex 返回有效实例，close 后资源释放 |
| 21 | testManager_getIndex_afterClose | close 后再 getIndex | 抛出 IllegalStateException 或返回 null |
| 22 | testManager_multipleCreate | 同一路径重复创建 CallIndexManager | 不损坏数据库，后创建的能正常使用 |

---

## 三、测试数据

### CallRecord 构造模板

```java
// 标准记录
new CallRecord("UserService", "process", "OrderService", "createOrder", "DIRECT", "UserService.java", 42, "HIGH")

// Spring 注入调用
new CallRecord("ReportController", "generate", "ReportService", null, "SPRING_INJECTION", "ReportController.java", 15, "MEDIUM")

// 反射调用
new CallRecord("ProxyHandler", "invoke", "TargetClass", "targetMethod", "REFLECTION", "ProxyHandler.java", 88, "LOW")

// 无置信度
new CallRecord("Util", "run", "Helper", "assist", "DIRECT", "Util.java", 10, null)
```

---

## 四、验收标准映射

| 验收标准 | 对应用例 |
|----------|----------|
| CallIndex 接口在 common 模块中 | #1~#8（通过接口类型引用测试） |
| SQLiteCallIndex 实现完整 CRUD | #1~#8 |
| 增量更新正确 | #9~#11 |
| 首次全量构建后直接加载 SQLite | #12~#13 |
| 并发安全 | #14~#15 |
| JDK 1.8 语法 | 代码审查（无 var/record/text block 等） |

---

## 五、注意事项

- 所有测试使用 `@TempDir` 创建临时目录，测试结束自动清理
- SQLite 文件路径：`{tempDir}/.codelens/callindex.db`
- 并发测试需启用 WAL 模式（`PRAGMA journal_mode=WAL`）
- CallRecord 的 confidence 字段允许 null，测试需覆盖
- JDK 1.8 语法：不使用 var、lambda 方法引用以外的 JDK 11+ 特性
