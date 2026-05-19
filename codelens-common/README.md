# CodeLens 共享规范文档

> 版本：2026-05-16-v2
> 维护方：喵呜（CLI端）
> 状态：进行中

## 1. 共享目录结构

```
codelens-common/
├── validators/
│   ├── EvidenceValidator.java    # L1 证据校验器
│   └── ConfidenceAnnotator.java  # L2 置信度标注器
├── models/
│   └── CodeMetaData.java        # 数据模型 & JSON Schema & 标签规范
└── utils/
    └── ColorUtil.java           # 终端颜色工具
```

## 2. 同步机制

### 2.1 版本号规则
每个共享文件头部标注：
```java
// SYNC_SOURCE: codelens-java/src/main/java/com/codelens/XXX.java
// SYNC_VERSION: 2026-05-16-v2
```

- 格式：`YYYY-MM-DD-v序号`
- 改才升号：仅当文件内容有修改时升号
- 升号由维护方负责（见分工）

### 2.2 插件端处理
- 保留副本 + 版本号标注
- 收到版本更新通知后覆盖
- 不删除代码，只覆盖

## 3. 分工与决定权

| 领域 | 拍板人 |
|------|--------|
| prompt/校验器/共享规范 | 喵呜 |
| 引擎适配/UI/插件特有 | Hermes |
| 交叉争议 | 默默 |

## 4. 接口契约三原则

1. **零 IntelliJ SDK 依赖**：所有共享代码禁止引用 `com.intellij.*`
2. **纯文本处理**：输入输出都是 String
3. **CLI 单测可覆盖**：确保 CLI 可以独立测试

## 5. JSON Schema 规范 (v2)

> 更新版本：2026-05-16-v2
> 变更：keyMethods 增强、risks 新增 type/suggestion、dependencies 新增 type、新增 confidence 字段

### 5.1 完整 Schema 定义

```json
{
  "summary": "string (类功能概述，必填)",
  "confidence": "string (CERTAIN|HIGH|MEDIUM|LOW，可选)",
  "dependencies": [
    {
      "name": "string (依赖类/变量名，必填)",
      "type": "string (import|field|method_call)",
      "line": "string (行号，数字转字符串)",
      "description": "string (1-2句描述)"
    }
  ],
  "risks": [
    {
      "type": "string (SECURITY|PERFORMANCE|MAINTAINABILITY)",
      "severity": "string (HIGH|MEDIUM|LOW)",
      "description": "string (风险描述)",
      "line": "string (行号)",
      "suggestion": "string (修复建议)"
    }
  ],
  "keyMethods": [
    {
      "name": "string (方法名，必填)",
      "line": "string (行号)",
      "signature": "string (方法签名，如 public User createUser(String name))",
      "visibility": "string (public|private|protected)",
      "complexity": "string (LOW|MEDIUM|HIGH)",
      "calls": "string (调用次数，数字转字符串)",
      "description": "string (方法功能描述)"
    }
  ]
}
```

### 5.2 字段说明

#### 顶层字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| summary | string | 是 | 类功能概述 |
| confidence | string | 否 | 置信度：CERTAIN/HIGH/MEDIUM/LOW |
| dependencies | array | 否 | 依赖项列表 |
| risks | array | 否 | 风险项列表 |
| keyMethods | array | 否 | 关键方法列表 |

#### dependencies 子字段

| 字段 | 类型 | 可选值 | 说明 |
|------|------|--------|------|
| name | string | - | 依赖类/变量名 |
| type | string | import / field / method_call | 依赖类型 |
| line | string | - | 行号 |
| description | string | - | 1-2句描述 |

#### risks 子字段

| 字段 | 类型 | 可选值 | 说明 |
|------|------|--------|------|
| type | string | SECURITY / PERFORMANCE / MAINTAINABILITY | 风险类型 |
| severity | string | HIGH / MEDIUM / LOW | 严重程度 |
| description | string | - | 风险描述 |
| line | string | - | 行号 |
| suggestion | string | - | 修复建议 |

#### keyMethods 子字段

| 字段 | 类型 | 可选值 | 说明 |
|------|------|--------|------|
| name | string | - | 方法名 |
| line | string | - | 行号 |
| signature | string | - | 方法签名（如 `public User createUser(String name)`） |
| visibility | string | public / private / protected | 可见性 |
| complexity | string | LOW / MEDIUM / HIGH | 复杂度 |
| calls | string | - | 调用次数 |
| description | string | - | 方法功能描述 |

### 5.3 Schema 版本差异

| 版本 | 变更内容 |
|------|----------|
| v1 | 初始版本 |
| v2 | keyMethods 字段名统一为 camelCase；新增 signature、visibility、calls 字段；risks 新增 type、suggestion 字段；dependencies.type 改为 import/field/method_call；新增 confidence 字段 |

## 6. 标签规范

### 6.1 解析标签
| 标签 | 含义 | 使用方 |
|------|------|--------|
| `[PSI_SAME_FILE]` | PSI 同文件解析 | 插件 |
| `[PSI_CROSS_FILE]` | PSI 跨文件解析 | 插件 |
| `[CODELENS_JP_UNRESOLVED]` | JavaParser 无法解析 | CLI/插件 |
| `[CODELENS_JP_FALLBACK]` | JavaParser 回退模式 | CLI |

### 6.2 校验标签
| 标签 | 含义 | 使用方 |
|------|------|--------|
| `[L1_PASSED]` | L1 证据校验通过 | 两端 |
| `[L1_FAILED]` | L1 证据校验失败 | 两端 |
| `[L1_SKIPPED]` | L1 校验跳过（无行号引用） | 两端 |

### 6.3 置信度标签
| 标签 | 含义 | 触发条件 |
|------|------|----------|
| `[CERTAIN]` | 确定 | L1 通过 + 行号精确 + 低风险 |
| `[HIGH]` | 高 | L1 通过 + 中风险或行号偏差1-2行 |
| `[MEDIUM]` | 中 | L1 通过但行号偏移>2 或 L1 未覆盖 |
| `[LOW]` | 低 | L1 失败（行号超出/名称不匹配） |

### 6.4 特殊标记
| 标签 | 含义 | 使用方 |
|------|------|--------|
| `[HALLUCINATION]` | 疑似幻觉 | 两端 |
| `[NEED_REVIEW]` | 需要人工审核 | 两端 |

## 7. 分歧记录

### 7.1 LLMClient 实现差异
| 端 | 实现 | 依赖 |
|---|------|------|
| CLI | HttpURLConnection | JDK 标准库 |
| 插件 | Retrofit + OpenAI SDK | okhttp + openai-java |

**决定**：各自保留实现，接口契约统一
- CLI LLMClient：静态方法 `analyze(apiKey, systemPrompt, userPrompt)`
- 插件 LLMClient：实例方法 `createCompletion(messages)` 通过 OpenAiService

### 7.2 解析引擎选择
| 端 | 引擎 | 特点 |
|---|------|------|
| CLI | JavaParser | 轻量级，独立运行，无需 IDE |
| 插件 | PSI | 精确，依赖 IntelliJ Platform |

**决定**：各自保留，输出格式统一按 JSON Schema

### 7.3 缓存策略（待定）
- CLI：SummaryCache（基于文件内容 hash）
- 插件：待实现

### 7.4 JSON Schema 差异（v1）
| 端 | 字段命名 | 说明 |
|---|----------|------|
| CLI | key_methods + description | 原始版本 |
| 插件 | keyMethods + reason/suggestion | 插件扩展版本 |

**决定（v2）**：已合并为 JSON Schema v2，取并集

## 8. Action Items

| # | Action | 负责人 | 状态 |
|---|--------|--------|------|
| 1 | 确认本规范文档 | Hermes | ✅ 已确认 |
| 2 | 插件端创建 codelens-common 副本 | Hermes | ✅ 已完成 |
| 3 | 更新插件端 EvidenceValidator 使用 common 版本 | Hermes | ✅ 已完成 |
| 4 | 更新插件端 LLMClient 接口适配 | 喵呜 | ✅ 已完成 |
| 5 | CLI 端调整包名引用 common | 喵呜 | ✅ 已完成 |
| 6 | 验证两端 L1 校验结果一致性 | 默默 | ✅ 已验证 |
| 7 | CLI 端 prompt 更新为 JSON Schema v2 | 喵呜 | ⏳ 待执行 |
| 8 | 插件端 prompt 更新为 JSON Schema v2 | Hermes | ⏳ 待执行 |
| 9 | 两端 L1 校验结果一致性验证（基于新 Schema） | 默默 | ⏳ 待验证 |
