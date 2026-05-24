# REQ-C10 测试用例 — ProviderPreset 温度锁定

> 需求编号：REQ-C10
> 需求文档：`docs/requirements/REQ-C10.md`
> 测试源码：`codelens-common/src/test/java/com/codelens/common/preset/ProviderPresetTest.java`
> 创建日期：2026-05-24

---

## 一、需求验收标准 → 测试用例映射

| # | 需求验收标准 | 对应测试用例 | 覆盖状态 |
|---|-------------|-------------|---------|
| A1 | ProviderPreset 包含 temperature 和 locked 字段 | testPresetConstruction, testPresetUnlocked | ✅ |
| A2 | of(provider, model) 返回对应预设 | testOfFactoryMethod, testOfReturnsCorrectPreset, testOfProModel, testOfGpt4Model | ✅ |
| A3 | locked=true 时用户传入温度被忽略 | testLockedTemperatureIgnoresUserOverride | ✅ |
| A4 | locked=false 时用户温度生效，但输出 warn 日志 | testUnlockedTemperatureAllowsUserOverride, testIsTemperatureOverridden | ✅ |
| A5 | mvn test 全部通过 | 全部测试通过即满足 | ✅ |
| A6 | JDK 1.8 语法 | testJdk8Compatibility | ✅ |

---

## 二、需求设计方案 → 测试用例映射

### 2.1 数据结构 → 测试

| 需求设计点 | 对应测试 | 说明 |
|-----------|---------|------|
| provider + model + temperature + locked | testPresetConstruction | 四字段构造 |
| locked=false | testPresetUnlocked | 解锁状态 |
| 不可变类（无 setter） | testPresetImmutability | final 字段 |

### 2.2 工厂方法 → 测试

| 需求设计点 | 对应测试 | 说明 |
|-----------|---------|------|
| of(provider, model) | testOfFactoryMethod | 基本工厂方法 |
| Flash/Doubao: 0.1 | testOfReturnsCorrectPreset | 免费模型低温 |
| Pro/DeepSeek: 0.0 | testOfProModel | 精准模式 |
| GPT-4: 0.1 | testOfGpt4Model | 默认 |

### 2.3 温度锁定逻辑 → 测试

| 需求设计点 | 对应测试 | 说明 |
|-----------|---------|------|
| locked=true 忽略用户温度 | testLockedTemperatureIgnoresUserOverride | 返回预设值 |
| locked=false 用户温度生效 | testUnlockedTemperatureAllowsUserOverride | 返回用户值 |
| locked=false 无用户值用默认 | testUnlockedNoUserValueUsesDefault | -1 → 预设 |
| 温度被覆盖检测 | testIsTemperatureOverridden | 日志辅助 |

### 2.4 配置外化 → 测试

| 需求设计点 | 对应测试 | 说明 |
|-----------|---------|------|
| fromMap 覆盖温度 | testFromMapOverrides | 0.2 / unlocked |
| fromMap 无覆盖用默认 | testFromMapNoOverrides | 空 map |
| fromMap 非法值容错 | testFromMapInvalidTemperature | 降级默认 |

---

## 三、完整测试用例清单

| # | 测试方法 | 所属维度 | 对应需求点 |
|---|---------|---------|-----------|
| 1 | testPresetConstruction | 数据结构 | A1 四字段 |
| 2 | testPresetUnlocked | 数据结构 | A1 locked=false |
| 3 | testPresetImmutability | 数据结构 | 不可变类 |
| 4 | testOfFactoryMethod | 工厂方法 | A2 of() |
| 5 | testOfReturnsCorrectPreset | 工厂方法 | A2 Flash预设 |
| 6 | testOfProModel | 工厂方法 | A2 Pro预设 |
| 7 | testOfGpt4Model | 工厂方法 | A2 GPT-4预设 |
| 8 | testLockedTemperatureIgnoresUserOverride | 锁定逻辑 | A3 locked=true |
| 9 | testUnlockedTemperatureAllowsUserOverride | 锁定逻辑 | A4 locked=false |
| 10 | testUnlockedNoUserValueUsesDefault | 锁定逻辑 | A4 默认值 |
| 11 | testIsTemperatureOverridden | 锁定逻辑 | A4 覆盖检测 |
| 12 | testKnownPresetsComplete | 预设映射 | 已知模型完整性 |
| 13 | testAnalysisTemperatureAlwaysLow | 预设映射 | 分析场景低温 |
| 14 | testUnknownModelReturnsDefault | 默认行为 | 未知模型 |
| 15 | testUnknownProviderReturnsDefault | 默认行为 | 未知provider |
| 16 | testFromMapOverrides | 配置外化 | 配置覆盖 |
| 17 | testFromMapNoOverrides | 配置外化 | 默认值 |
| 18 | testFromMapInvalidTemperature | 配置外化 | 容错 |
| 19 | testNullProviderModel | 边界条件 | null输入 |
| 20 | testEmptyProviderModel | 边界条件 | 空字符串 |
| 21 | testTemperatureBoundaryZero | 边界条件 | 0.0边界 |
| 22 | testTemperatureBoundaryOne | 边界条件 | 1.0边界 |
| 23 | testGetKey | 边界条件 | provider:model |
| 24 | testJdk8Compatibility | 边界条件 | JDK1.8 |

---

## 四、待实现类清单（给 Claude Code）

```
com.codelens.common.preset.ProviderPreset  — 预设类（provider + model + temperature + locked + getKey）
```

### 关键实现要点

1. **ProviderPreset 是不可变类**：所有字段 final，无 setter
2. **of(provider, model)**：静态工厂方法，内部维护已知预设映射表
3. **已知预设**：
   - `doubao:flash` / `qwen:flash` → temperature=0.1, locked=true
   - `doubao:pro` / `deepseek:chat` → temperature=0.0, locked=true
   - `openai:gpt-4` → temperature=0.1, locked=true
   - 未知 → temperature=0.1, locked=true（安全默认）
4. **getEffectiveTemperature(userTemp)**：locked=true 返回预设温度；locked=false 且 userTemp 有效(≥0) 返回 userTemp，否则返回预设
5. **isTemperatureOverridden(userTemp)**：locked=true 且 userTemp ≠ presetTemperature 时返回 true
6. **fromMap(provider, model, overrides)**：从 Map 读取 `codelens.provider.{model}.temperature` 和 `.locked` 覆盖预设值
7. **getKey()**：返回 `provider:model` 格式字符串
8. **JDK 1.8 兼容**：不用 List.of / Map.of / var
