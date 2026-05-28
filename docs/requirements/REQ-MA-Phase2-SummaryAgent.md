# 需求 010：多Agent流水线 Phase 2 — 摘要 Agent Prompt + 校验器

> **版本：** v1.0
> **状态：** 待执行
> **优先级：** 🔴 P1
> **编写人：** 喵呜
> **日期：** 2026-05-28
> **目标模块：** codelens-common

---

## 1. 要解决什么问题

Phase 1 已完成 AnalysisTask + GranularCache 数据结构。Phase 2 要实现第一个独立 Agent：**摘要 Agent**，负责对整个 Java 文件生成结构化摘要（≤500 token），替代当前单次 LLM 调用中对全文件的粗糙处理。

当前痛点：
- 单次 LLM 调用处理大文件时容易截断，摘要质量差
- 无校验机制，LLM 输出格式不规范时静默失败
- 无缓存，相同文件重复分析浪费 token

## 2. 功能需求

### 2.1 SummaryPrompt 模板

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | Prompt 模板类 `SummaryPrompt` | 封装摘要 Agent 的 system prompt + user prompt 模板 |
| 2 | System Prompt | 定义角色（Java 代码分析专家）、输出格式要求（JSON Schema）、约束（≤500 token） |
| 3 | User Prompt 模板 | 接受源码 + 索引元数据（class列表、方法签名列表）作为输入变量 |
| 4 | 输出 JSON Schema | 定义摘要输出结构：className、stereotype、keyMethods[]、dependencies[]、complexity 5个字段 |

**输出 Schema 示例：**
```json
{
  "className": "com.example.OrderService",
  "stereotype": "SERVICE",
  "keyMethods": [
    {"name": "processOrder", "role": "核心业务入口", "complexity": 8},
    {"name": "validateOrder", "role": "参数校验", "complexity": 3}
  ],
  "dependencies": ["OrderRepository", "PaymentGateway"],
  "complexity": "MEDIUM"
}
```

### 2.2 SummaryValidator 校验器

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | 校验器类 `SummaryValidator` | 实现 `Predicate<AnalysisTask>` 接口，校验摘要 Agent 输出 |
| 2 | Schema 校验 | 检查5个必填字段是否都存在且类型正确 |
| 3 | Token 限制校验 | 摘要文本不超过500 token（按空格分词估算，允许±10%容差） |
| 4 | keyMethods 非空校验 | 至少要有1个 keyMethod |
| 5 | 校验失败时返回结构化错误信息 | 不抛异常，返回 ValidationResult（isValid + errorMessage） |

**ValidationResult 结构：**
```java
public class ValidationResult {
    private final boolean valid;
    private final String errorMessage;  // null if valid
    private final String fieldName;     // 出错字段名，null if valid
}
```

### 2.3 缓存 Key 策略

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | 缓存 key 生成 | `SHA-256(sourceCode + TaskType.SUMMARY.name())`，复用 Phase 1 的 CacheGranule |
| 2 | 缓存命中条件 | 源码未变 + 任务类型为 SUMMARY → 直接返回缓存 |
| 3 | 缓存失效 | 源码变更时 GranularCache.invalidateByFile() 自动失效 |

### 2.4 AnalysisTask 扩展

| # | 需求项 | 说明 |
|---|--------|------|
| 1 | SUMMARY 任务类型 | Phase 1 已定义 TaskType.SUMMARY，本阶段填充其 prompt + validator |
| 2 | 输入绑定 | AnalysisTask.setInput() 传入源码 + 索引元数据 |
| 3 | 输出绑定 | AnalysisTask.setOutput() 接收 JSON 字符串，由 SummaryValidator 校验 |

---

## 3. 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `common/agent/SummaryPrompt.java` | 新增 | 摘要 Prompt 模板 |
| `common/agent/SummaryValidator.java` | 新增 | 摘要输出校验器 |
| `common/agent/ValidationResult.java` | 新增 | 校验结果数据类 |
| `common/agent/TaskType.java` | 修改 | SUMMARY 枚举值补充 promptClass + validatorClass 属性 |

---

## 4. 验收标准

- [ ] SummaryPrompt.generateSystemPrompt() 返回包含 JSON Schema 约束的完整 system prompt
- [ ] SummaryPrompt.generateUserPrompt(sourceCode, metadata) 正确拼接模板
- [ ] SummaryValidator 对合法 JSON 输出返回 ValidationResult.valid=true
- [ ] SummaryValidator 对缺失字段、超 token、空 keyMethods 分别返回对应错误
- [ ] 缓存 key 使用 CacheGranule 生成，与源码内容绑定
- [ ] 全部单元测试通过（至少8个测试：prompt生成×2 + validator合法×1 + validator非法×4 + 缓存key×1）

---

## 5. Claude Code 任务描述

```
在 codelens-java（common模块）中实现多Agent流水线 Phase 2：摘要 Agent Prompt + 校验器。

仓库：https://github.com/fiendchenmo/codelens-java.git
分支：从 main 创建 feature/multi-agent-phase2

已有代码（Phase 1 产出，不要修改）：
- common/agent/AnalysisTask.java — 泛型数据类
- common/agent/TaskType.java — 4种任务类型枚举
- common/cache/CacheGranule.java — 缓存粒度（SHA-256 key生成）
- common/cache/GranularCache.java — 缓存接口
- common/cache/GranularCacheAdapter.java — FileSystemCache适配器

需要新增的文件：

1. common/agent/SummaryPrompt.java
   - 两个公共方法：generateSystemPrompt()、generateUserPrompt(String sourceCode, String metadata)
   - system prompt 要求 LLM 以 JSON 格式输出，包含5个字段：className, stereotype, keyMethods[], dependencies[], complexity
   - user prompt 接受 {{sourceCode}} 和 {{metadata}} 占位符
   - 摘要不超过500 token

2. common/agent/SummaryValidator.java
   - implements Predicate<String>（输入是 LLM 输出的 JSON 字符串）
   - 校验逻辑：JSON解析成功 → 5个必填字段存在 → keyMethods非空 → token估算≤550
   - 返回 ValidationResult
   - 不抛异常，解析失败也包装成 ValidationResult

3. common/agent/ValidationResult.java
   - 不可变数据类：boolean valid, String errorMessage, String fieldName
   - 静态工厂方法：ok()、fail(String fieldName, String errorMessage)

4. 修改 TaskType.java
   - 给每个枚举值加 promptClass 和 validatorClass 属性
   - SUMMARY → SummaryPrompt.class, SummaryValidator.class
   - 其他3个暂设 null

5. 测试文件：common/agent/SummaryPromptTest.java
   - testGenerateSystemPrompt_ContainsJsonSchema
   - testGenerateUserPrompt_SubstitutesVariables

6. 测试文件：common/agent/SummaryValidatorTest.java
   - testValidate_ValidOutput
   - testValidate_MissingClassName
   - testValidate_EmptyKeyMethods
   - testValidate_ExceedsTokenLimit
   - testValidate_InvalidJson
   - testValidate_MissingDependenciesField

7. 测试文件：common/agent/ValidationResultTest.java
   - testOk_ReturnsValidResult
   - testFail_ReturnsInvalidResult

注意事项：
- 包名统一为 com.codelens.common.agent
- 不依赖任何 LLM SDK，纯模板 + 校验逻辑
- JSON 解析用 Gson（项目已有依赖）
- 编译通过 + 全部测试通过后 push
- 只改 common 模块，不改 CLI 或插件端
```
