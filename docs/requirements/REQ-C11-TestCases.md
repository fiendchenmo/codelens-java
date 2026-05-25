# REQ-C11 测试用例 — 模型特性标记

> 编号：REQ-C11-TestCases
> 对应需求：REQ-C11
> 测试目标：ModelCapability 枚举 + ModelProfile 数据类 + 预设 Profile

---

## 一、测试维度

| 维度 | 覆盖点 | 用例数 |
|------|--------|--------|
| A1 ModelCapability 枚举 | 枚举值完整性 + hasCapability | 3 |
| A2 ModelProfile 构造 | 字段正确性 + 不可变性 | 4 |
| A3 预设 Profile | of() 工厂方法 + 5个已知模型 | 6 |
| A4 Fallback | 未知模型处理 | 3 |
| A5 边界条件 | null / 空能力集 / 极端值 | 3 |

**总计：19**

---

## 二、完整测试用例清单

### A1 — ModelCapability 枚举

| # | 测试方法 | 测试内容 | 验证点 |
|---|----------|----------|--------|
| 1 | testModelCapability_allValues | 枚举包含6个值 | LONG_CONTEXT, STABLE_JSON, HIGH_ACCURACY, FAST_RESPONSE, LIMITED_OUTPUT, MULTI_TURN 全部存在 |
| 2 | testModelCapability_noDuplicates | 枚举值不重复 | values().length == 6 |
| 3 | testHasCapability_withCapability | ModelProfile 包含某能力时 | hasCapability(STABLE_JSON) == true |
| 4 | testHasCapability_withoutCapability | ModelProfile 不包含某能力时 | hasCapability(LONG_CONTEXT) == false |

### A2 — ModelProfile 构造

| # | 测试方法 | 测试内容 | 验证点 |
|---|----------|----------|--------|
| 5 | testModelProfile_fields | 构造后字段正确 | provider/model/capabilities/maxContextTokens/maxOutputTokens/recommendedTemperature 全部匹配 |
| 6 | testModelProfile_immutability | 尝试修改 capabilities 集合 | 返回的 Set 是不可变的（抛 UnsupportedOperationException） |
| 7 | testModelProfile_multipleCapabilities | 一个 Profile 有多个能力 | hasCapability 对每个能力分别返回 true/false |
| 8 | testModelProfile_emptyCapabilities | 能力集为空 | hasCapability 对任何能力返回 false，不抛异常 |

### A3 — 预设 Profile

| # | 测试方法 | 测试内容 | 验证点 |
|---|----------|----------|--------|
| 9 | testPreset_doubaoFlash | ModelProfile.of("doubao", "flash") | capabilities = {FAST_RESPONSE, STABLE_JSON}, maxContext=32768, maxOutput=4096 |
| 10 | testPreset_doubaoPro | ModelProfile.of("doubao", "pro") | capabilities = {HIGH_ACCURACY, LONG_CONTEXT, MULTI_TURN}, maxContext=131072 |
| 11 | testPreset_openaiGpt4 | ModelProfile.of("openai", "gpt-4") | capabilities = {HIGH_ACCURACY, LONG_CONTEXT, STABLE_JSON, MULTI_TURN} |
| 12 | testPreset_qwenPlus | ModelProfile.of("qwen", "qwen-plus") | capabilities = {FAST_RESPONSE}, maxContext=32768 |
| 13 | testPreset_deepseekChat | ModelProfile.of("deepseek", "deepseek-chat") | capabilities = {HIGH_ACCURACY, MULTI_TURN}, maxContext=65536 |
| 14 | testPreset_caseInsensitive | ModelProfile.of("Doubao", "Flash") | 全部转小写匹配，返回 flash profile |

### A4 — Fallback（未知模型）

| # | 测试方法 | 测试内容 | 验证点 |
|---|----------|----------|--------|
| 15 | testFallback_unknownModel | ModelProfile.of("unknown", "model-x") | 不抛异常，返回默认 Profile |
| 16 | testFallback_defaultValues | 未知模型的默认值 | capabilities 非空（至少含 FAST_RESPONSE 或空集），maxContextTokens > 0，maxOutputTokens > 0 |
| 17 | testFallback_unknownProvider | ModelProfile.of(null, "flash") | 不抛异常（provider 为 null 时处理方式需明确：NPE 或 fallback） |

### A5 — 边界条件

| # | 测试方法 | 测试内容 | 验证点 |
|---|----------|----------|--------|
| 18 | testModelProfile_zeroTokens | maxContextTokens=0, maxOutputTokens=0 | 构造不报错，hasCapability 正常工作 |
| 19 | testModelProfile_negativeTemperature | recommendedTemperature = -0.1 | 构造不报错（温度校验不在 ModelProfile 职责内） |
| 20 | testModelProfile_ofSameModelTwice | 两次 of("doubao", "flash") | 返回的 Profile 字段值一致（不要求同一实例） |

---

## 三、测试数据

### 预设 Profile 验证数据

| Provider | Model | Capabilities | Max Context | Max Output | Temp |
|----------|-------|-------------|-------------|------------|------|
| doubao | flash | FAST_RESPONSE, STABLE_JSON | 32768 | 4096 | 0.0 |
| doubao | pro | HIGH_ACCURACY, LONG_CONTEXT, MULTI_TURN | 131072 | 16384 | 0.0 |
| openai | gpt-4 | HIGH_ACCURACY, LONG_CONTEXT, STABLE_JSON, MULTI_TURN | 131072 | 16384 | 0.0 |
| qwen | qwen-plus | FAST_RESPONSE | 32768 | 8192 | 0.0 |
| deepseek | deepseek-chat | HIGH_ACCURACY, MULTI_TURN | 65536 | 8192 | 0.0 |

### Fallback 默认值建议

```
capabilities = {} (空集)
maxContextTokens = 16384
maxOutputTokens = 4096
recommendedTemperature = 0.0
```

---

## 四、验收标准映射

| 验收标准 | 对应用例 |
|----------|----------|
| ModelCapability 枚举在 common 模块中 | #1~#2 |
| ModelProfile 数据类在 common 模块中 | #5~#8 |
| of(provider, model) 至少支持5个模型 | #9~#13 |
| 未知模型返回带默认值的 fallback Profile | #15~#16 |
| JDK 1.8 语法 | 代码审查（无 var/record/text block） |

---

## 五、注意事项

- ModelProfile 是不可变数据类，所有字段 final，capabilities 返回不可修改的 Set
- of() 工厂方法全部转小写匹配（provider.toLowerCase() + model.toLowerCase()）
- 未知模型不抛异常是硬性要求，与 C-10 温度联动但互不依赖
- JDK 1.8 语法：Set.of() 不可用，改用 Collections.unmodifiableSet(new HashSet<>(...))
