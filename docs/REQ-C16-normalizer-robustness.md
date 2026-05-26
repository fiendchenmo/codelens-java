# REQ-C16: OutputNormalizer 截断/特殊字符修复

## 背景

V3 Round4 全量基准测试中，4个大文件（C1/C3/C6/C8）的 JSON 因 LLM 输出质量问题无法解析：

| 用例 | 失败原因 | 具体表现 |
|---|---|---|
| C1 | JSON截断 | 方法数组中间断开，`description` 字段值被截断 |
| C3 | 引号未转义 | `BaseResponse.fail("未登录")` 中的引号破坏 JSON 结构 |
| C6 | 引号未转义 | `cardData.replace(\"AUTO\", assetNum)` 中的转义引号被二次破坏 |
| C8 | 控制字符 | description 值中含换行符 `\n` 未转义 |

这些文件的 L1 校验实际通过了（说明 Normalizer 修复了部分问题），但 `formatAnalysisResult()` 拿到的 JSON 仍有问题导致渲染失败走 catch 分支。

## 目标

增强 OutputNormalizer 的 JSON 修复能力，使大文件 V3 输出的渲染成功率从 6/10 提升到 ≥9/10。

## 需修复的问题模式

### 1. 截断 JSON 修复
- 方法数组在中间截断时，尝试闭合未完成的数组/对象
- 保留已解析的完整 methods，丢弃末尾不完整的方法
- 补全顶层 `}` 闭合

### 2. 字符串内未转义引号
- LLM 在 description/suggestion 等字符串值中输出 `"未登录"` 等未转义引号
- 修复策略：在 JSON 字符串值内部，将独立的 `"` 替换为 `\"`
- 需要区分 JSON 结构引号和内容引号

### 3. 字符串内控制字符
- LLM 在字符串值中输出换行符 `\n`、制表符 `\t` 等
- 修复策略：将字符串值内的裸换行替换为 `\\n`，裸制表符替换为 `\\t`

### 4. 截断位置修复流程
```
原始LLM输出 → 尝试解析 → 失败
  → 修复控制字符 → 尝试解析 → 失败
  → 修复未转义引号 → 尝试解析 → 失败  
  → 截断修复（闭合括号） → 尝试解析 → 成功/部分成功
```

每步修复后立即尝试解析，成功就停止，不做多余修复。

## 改动清单

### 1. `OutputNormalizer.java` — 新增修复方法

```java
/**
 * 尝试修复LLM输出的损坏JSON
 * 修复顺序：控制字符 → 未转义引号 → 截断闭合
 */
static String repairJson(String json)

/**
 * 修复JSON字符串值内的控制字符（裸换行/制表符）
 */
static String fixControlChars(String json)

/**
 * 修复JSON字符串值内的未转义引号
 * 策略：逐字符扫描，跟踪是否在字符串值内，值内的裸"替换为\"
 */
static String fixUnescapedQuotes(String json)

/**
 * 尝试闭合截断的JSON
 * 策略：从末尾找最后一个完整的 } 或 ]，丢弃不完整的尾部，补全缺失的闭合符号
 */
static String fixTruncation(String json)
```

在 `normalize(String json)` 方法中，首次 `JsonParser.parseString` 失败时调用 `repairJson`。

### 2. `OutputNormalizerTest.java` — 新增测试

覆盖上述4种问题模式，每种至少2个测试用例（简单场景 + 复杂嵌套场景）。

### 3. V2/V3 都受益

修复逻辑不区分 V2/V3，两种格式的损坏 JSON 都能修复。

## 验收标准

1. `mvn test` 全绿
2. Round4 中 C3/C6/C8 三个文件（引号/控制字符问题）能被修复并正确渲染
3. C1（截断问题）至少能修复到显示已解析的完整方法
4. 修复后的 JSON 不丢失已成功解析的内容
5. 不影响正常 JSON 的处理（空操作验证）

## 分支

`feature/REQ-C16-normalizer-robustness`

## 关联

- 数据支撑：V3 Round4 基准测试（4/10 大文件截断）
- 前置：REQ-C15（已合并）
