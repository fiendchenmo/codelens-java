# 需求 008.1：多Agent流水线 Phase 1 — AnalysisTask + GranularCache 数据结构与接口

> **版本：** v1.0
> **状态：** 待执行
> **优先级：** 🔴 P1
> **编写人：** 喵呜
> **日期：** 2026-05-28
> **目标模块：** codelens-common
> **原始需求：** 需求文档/004-multi-agent-pipeline-common.md Phase 1 部分

---

## 1. 要解决什么问题

多Agent流水线需要统一的任务数据模型和方法级缓存接口，作为 Phase 2-4 的基础设施。

## 2. 功能需求

### 2.1 AnalysisTask 泛型数据类

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | `AnalysisTask<TInput, TOutput>` | 泛型数据类，封装单个 Agent 任务的输入输出 |
| 2 | 字段 | taskId(String, UUID自动)、taskType(TaskType)、input(TInput)、output(TOutput)、status(ExecutionStatus)、createdAt、completedAt |
| 3 | 不可变 | input/output 通过 getter 访问，output 通过 setOutput() 设置 |

### 2.2 TaskType 枚举

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | 4种任务类型 | STRUCTURE_EXTRACTION / SUMMARY / METHOD_ANALYSIS / CROSS_FILE_INFERENCE |
| 2 | 属性 | 每个枚举值含 promptClass 和 validatorClass（Phase 1 暂设 null） |

### 2.3 CacheGranule 数据类

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | 缓存粒度数据类 | 基于内容 hash 标识单个缓存条目 |
| 2 | 字段 | taskType(TaskType)、version(String)、contentType(String)、contentHash(String, SHA-256)、modelId(String)、output(String)、createdAt(long)、invalidatedBy(List<String>) |
| 3 | key生成 | `SHA-256(inputContent + taskType.name())` |

### 2.4 GranularCache 接口

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | 接口定义 | put / get / invalidate / invalidateByFile / listByType |
| 2 | get | 按 contentHash 查找，返回 Optional<CacheGranule> |
| 3 | invalidate | 按 contentHash 失效 |
| 4 | invalidateByFile | 按文件路径失效（子串匹配，后续改精确匹配） |

### 2.5 GranularCacheAdapter

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | 适配器 | 实现 GranularCache 接口，内部委托给现有 FileSystemCache |
| 2 | 向后兼容 | 不修改 FileSystemCache 的代码和目录结构 |
| 3 | syncToLegacyCache | 暂为空实现，后续补充 |

---

## 3. 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `common/agent/AnalysisTask.java` | 新增 | 泛型任务数据类 |
| `common/agent/TaskType.java` | 新增 | 任务类型枚举 |
| `common/agent/CacheGranule.java` | 新增 | 缓存粒度数据类 |
| `common/cache/GranularCache.java` | 新增 | 缓存接口 |
| `common/cache/GranularCacheAdapter.java` | 新增 | FileSystemCache 适配器 |

---

## 4. 验收标准

- [ ] AnalysisTask 可实例化，泛型输入输出类型正确
- [ ] TaskType 包含4种类型，promptClass/validatorClass 暂为 null
- [ ] CacheGranule 的 contentHash 基于 SHA-256 生成，相同输入产生相同 hash
- [ ] GranularCache 接口5个方法签名正确
- [ ] GranularCacheAdapter 实现所有接口方法，委托给 FileSystemCache
- [ ] 全部单元测试通过（至少10个）

---

## 5. Claude Code 任务描述

```
在 codelens-java（common模块）中实现多Agent流水线 Phase 1：数据模型与缓存接口。

仓库：https://github.com/fiendchenmo/codelens-java.git
分支：从 main 创建 feature/multi-agent-phase1

现有 common 模块结构（不要修改已有文件的核心逻辑）：
- codelens-common/src/main/java/com/codelens/common/cache/FileSystemCache.java — 现有文件缓存
- codelens-common/src/main/java/com/codelens/common/cache/Cache.java — 现有缓存接口
- codelens-common/src/main/java/com/codelens/common/cache/CacheConfig.java
- codelens-common/src/main/java/com/codelens/common/cache/CacheEntry.java
- codelens-common/src/main/java/com/codelens/common/cache/CacheKeyGenerator.java

需要新增的文件：

1. codelens-common/src/main/java/com/codelens/common/agent/AnalysisTask.java
   - 泛型类 AnalysisTask<TInput, TOutput>
   - 字段: taskId(String, 构造时UUID.randomUUID().toString()), taskType(TaskType), input(TInput), output(TOutput, 初始null), status(ExecutionStatus, 初始PENDING), createdAt(long, System.currentTimeMillis()), completedAt(Long, 初始null)
   - 方法: getTaskId(), getTaskType(), getInput(), getOutput(), setOutput(TOutput), getStatus(), setStatus(ExecutionStatus), getCreatedAt(), getCompletedAt(), setCompletedAt(long)
   - setOutput() 同时设 status=COMPLETED, completedAt=System.currentTimeMillis()

2. codelens-common/src/main/java/com/codelens/common/agent/TaskType.java
   - 枚举: STRUCTURE_EXTRACTION, SUMMARY, METHOD_ANALYSIS, CROSS_FILE_INFERENCE
   - 每个枚举值有 Class<?> promptClass 和 Class<?> validatorClass 属性
   - 构造函数: TaskType(Class<?> promptClass, Class<?> validatorClass)
   - Phase 1 全部设为 null

3. codelens-common/src/main/java/com/codelens/common/agent/CacheGranule.java
   - 不可变数据类
   - 字段: taskType(TaskType), version(String), contentType(String), contentHash(String), modelId(String), output(String), createdAt(long), invalidatedBy(List<String>)
   - 全参构造 + Builder模式
   - 静态方法 generateKey(String inputContent, TaskType taskType): SHA-256(inputContent + taskType.name())

4. codelens-common/src/main/java/com/codelens/common/cache/GranularCache.java
   - 接口
   - void put(CacheGranule granule)
   - Optional<CacheGranule> get(String contentHash)
   - void invalidate(String contentHash)
   - void invalidateByFile(String filePath)
   - List<CacheGranule> listByType(TaskType taskType)

5. codelens-common/src/main/java/com/codelens/common/cache/GranularCacheAdapter.java
   - implements GranularCache
   - 构造参数: FileSystemCache delegate
   - put: 委托给 delegate.put(contentHash, granule的JSON序列化)
   - get: 从 delegate.get(contentHash) 反序列化为 CacheGranule
   - invalidate: delegate.invalidate(contentHash)
   - invalidateByFile: 遍历 delegate 的所有条目，文件路径子串匹配则 invalidate
   - listByType: 遍历 delegate 的所有条目，按 taskType 过滤
   - syncToLegacyCache(): 空实现，后续补充
   - JSON 序列化/反序列化用 Gson

6. 测试文件:

   codelens-common/src/test/java/com/codelens/common/agent/AnalysisTaskTest.java
   - testCreateTask_WithInputAndType
   - testSetOutput_UpdatesStatusAndCompletedAt
   - testTaskId_AutoGenerated

   codelens-common/src/test/java/com/codelens/common/agent/CacheGranuleTest.java
   - testGenerateKey_SameInputSameHash
   - testGenerateKey_DifferentInputDifferentHash
   - testBuilder_AllFields

   codelens-common/src/test/java/com/codelens/common/cache/GranularCacheAdapterTest.java
   - testPutAndGet
   - testGet_NotFound
   - testInvalidate
   - testInvalidateByFile

注意事项：
- 包名统一为 com.codelens.common.agent 和 com.codelens.common.cache
- 不修改现有 FileSystemCache / Cache / CacheEntry 的代码
- JSON 用 Gson（项目已有依赖）
- SHA-256 用 java.security.MessageDigest
- 编译通过 + 全部测试通过后 push 到 origin feature/multi-agent-phase1
- 不要 push 到 main
```
