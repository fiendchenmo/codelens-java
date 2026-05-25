# 需求 — CallIndex 迁移 Phase 2

> 编号：REQ-C9
> 优先级：🟢 P2
> 工作量：2d
> 前置依赖：C-2（MethodRange 类，CallIndex 需要方法范围信息）
> 责任人：喵呜
> 交付日期：5/30~5/31
> 变更归属：🟠 common变更

## 目的

Phase 1 的 CallIndex 使用内存 Map 存储调用索引，每次启动需全量重建。Phase 2 迁移到 SQLite 持久化存储，支持增量更新，减少启动时间和内存占用。

目标：CallIndex 的 CRUD 接口和 SQLite 实现提取到 common 模块，CLI 端和插件端共用。

## 设计方案

### CallIndex 接口（→ common）

```java
public interface CallIndex extends AutoCloseable {
    void insert(CallRecord record);
    void batchInsert(List<CallRecord> records);  // 批量插入，单次事务提交
    List<CallRecord> queryByCaller(String className, String methodName);
    List<CallRecord> queryByCallee(String className, String methodName);
    void deleteByFile(String filePath);  // 删除某文件的所有记录（增量更新前清理）
    void close();
}
```

### SQLite 实现

- 数据库文件：`{projectRoot}/.codelens/callindex.db`
- 表结构：
  ```sql
  CREATE TABLE call_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    caller_class TEXT NOT NULL,
    caller_method TEXT NOT NULL,
    callee_class TEXT NOT NULL,
    callee_method TEXT NOT NULL,
    call_type TEXT NOT NULL,  -- DIRECT, REFLECTION, SPRING_INJECTION
    file_path TEXT NOT NULL,
    line_number INTEGER,
    confidence TEXT,          -- HIGH, MEDIUM, LOW
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );
  CREATE INDEX idx_caller ON call_records(caller_class, caller_method);
  CREATE INDEX idx_callee ON call_records(callee_class, callee_method);
  CREATE INDEX idx_file ON call_records(file_path);
  ```

### 增量更新流程

```
文件变更事件
  ↓
deleteByFile(changedFile)  // 清除旧记录
  ↓
重新解析该文件，extractCalls()
  ↓
batch insert 新记录
  ↓
完成
```

## 变更范围

| 模块 | 变更内容 |
|------|---------|
| codelens-common | 新增 CallIndex 接口 + CallRecord 数据类 |
| codelens-common | 新增 SQLiteCallIndex 实现 |
| codelens-common | 新增 CallIndexManager（管理生命周期） |
| codelens-cli | 替换内存 CallIndex 为 SQLiteCallIndex |

## 验收标准

- [x] CallIndex 接口在 common 模块中
- [x] SQLiteCallIndex 实现完整 CRUD + batchInsert
- [x] 增量更新正确（修改文件后索引同步更新）
- [x] 首次全量构建后，后续启动直接加载 SQLite（无需重建）
- [x] 并发安全（多线程读写不丢数据，所有方法 synchronized(lock)）
- [x] close 后调用 CRUD 方法抛 IllegalStateException（checkClosed 防护）
- [x] `mvn test` 全部通过
- [x] JDK 1.8 语法

## 实现备注

- SQLiteCallIndex 所有公开方法均加 synchronized(lock)，查询也不例外
- close() 后 conn=null，方法入口 checkClosed() 防 NPE
- batchInsert 用 addBatch() + executeBatch()，单次 commit，批量场景性能优
- callee_method 字段允许 NULL（SPRING_INJECTION 类型无明确被调用方法名）

## 风险与约束

- SQLite 文件可能膨胀，需考虑定期清理或压缩策略
- 并发写入 SQLite 需用 WAL 模式
- 依赖 C-2 的 MethodRange（调用记录关联方法范围）
- JDK 1.8 语法
- 优先级最低，等其他任务完成后再启动
