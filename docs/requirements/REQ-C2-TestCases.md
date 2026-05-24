# REQ-C2 测试用例 — EvidenceValidator 加 methodRanges

> 需求编号：REQ-C2
> 需求文档：`docs/requirements/REQ-C2.md`
> 测试源码：`codelens-common/src/test/java/com/codelens/common/validators/MethodRangeTest.java`
> 创建日期：2026-05-24

---

## 一、需求验收标准 → 测试用例映射

| # | 需求验收标准 | 对应测试用例 | 覆盖状态 |
|---|-------------|-------------|---------|
| A1 | MethodRange 类在 common 模块中 | testMethodRangeConstruction, testMethodRangeContainsLine, testMethodRangeSingleLine | ✅ |
| A2 | validateRisks 重载方法在 common 模块中 | testValidateRisksWithMethodRanges | ✅ |
| A3 | 不传 methodRanges 时行为与现有逻辑完全一致（降级） | testValidateRisksNullMethodRanges, testValidateRisksEmptyMethodRanges, testValidateRisksDegradedLineOutOfFile | ✅ |
| A4 | 传入 methodRanges 时，risks.line 超出所有方法范围的项被标记为偏差 | testValidateRisksLineOutsideAllMethods, testValidateRisksMultipleRisks | ✅ |
| A5 | mvn test 全部通过 | 全部测试通过即满足 | ✅ |
| A6 | JDK 1.8 语法 | testJdk8Compatibility | ✅ |

---

## 二、需求设计方案 → 测试用例映射

### 2.1 MethodRange 数据类 → 测试

| 需求设计点 | 对应测试 | 说明 |
|-----------|---------|------|
| methodName + startLine + endLine | testMethodRangeConstruction | 三字段构造 |
| contains(int line) | testMethodRangeContainsLine | 含边界 |
| 单行方法 startLine == endLine | testMethodRangeSingleLine | 边界场景 |
| 不可变类（final 字段，无 setter） | testMethodRangeImmutability | 编译验证 |

### 2.2 validateRisks 重载 → 测试

| 需求设计点 | 对应测试 | 说明 |
|-----------|---------|------|
| 传入 methodRanges → 方法级校验 | testValidateRisksWithMethodRanges | line 在方法内→通过 |
| line 不在任何方法内 → 偏差 | testValidateRisksLineOutsideAllMethods | 方法间隙 |
| 多个 risk 混合结果 | testValidateRisksMultipleRisks | 部分通过部分偏差 |
| 嵌套方法匹配第一个 | testValidateRisksLineMatchesFirstMethod | 多方法重叠 |

### 2.3 降级行为 → 测试

| 需求设计点 | 对应测试 | 说明 |
|-----------|---------|------|
| null → 整文件校验 | testValidateRisksNullMethodRanges | 向后兼容 |
| 空列表 → 整文件校验 | testValidateRisksEmptyMethodRanges | 向后兼容 |
| 降级时行号超文件 → 偏差 | testValidateRisksDegradedLineOutOfFile | 原逻辑不变 |

### 2.4 不改现有方法签名 → 测试

| 需求设计点 | 对应测试 | 说明 |
|-----------|---------|------|
| 原有 validate(String, String, String[]) 不变 | testExistingValidateMethodStillWorks | 签名兼容 |

---

## 三、完整测试用例清单

| # | 测试方法 | 所属维度 | 对应需求点 |
|---|---------|---------|-----------|
| 1 | testMethodRangeConstruction | MethodRange | A1 三字段 |
| 2 | testMethodRangeContainsLine | MethodRange | A1 contains() |
| 3 | testMethodRangeSingleLine | MethodRange | A1 单行方法 |
| 4 | testMethodRangeImmutability | MethodRange | 不可变类 |
| 5 | testValidateRisksWithMethodRanges | 重载方法 | A2 方法级校验 |
| 6 | testValidateRisksLineOutsideAllMethods | 重载方法 | A4 方法间隙→偏差 |
| 7 | testValidateRisksMultipleRisks | 重载方法 | A4 混合结果 |
| 8 | testValidateRisksLineMatchesFirstMethod | 重载方法 | 嵌套匹配 |
| 9 | testValidateRisksNullMethodRanges | 降级 | A3 null降级 |
| 10 | testValidateRisksEmptyMethodRanges | 降级 | A3 空列表降级 |
| 11 | testValidateRisksDegradedLineOutOfFile | 降级 | A3 降级超范围 |
| 12 | testExistingValidateMethodStillWorks | 兼容 | 不改现有签名 |
| 13 | testMethodRangeLineAtStartBoundary | 边界 | startLine 边界 |
| 14 | testMethodRangeLineAtEndBoundary | 边界 | endLine 边界 |
| 15 | testMethodRangeLineJustBeforeStart | 边界 | startLine-1 |
| 16 | testMethodRangeLineJustAfterEnd | 边界 | endLine+1 |
| 17 | testNoRisksInJson | 边界 | 无 risks 数组 |
| 18 | testInvalidLineNumber | 边界 | 无效行号 |
| 19 | testLargeMethodRange | 边界 | 整文件级方法 |
| 20 | testJdk8Compatibility | 边界 | JDK1.8 |

---

## 四、待实现类清单（给 Claude Code）

```
com.codelens.common.validators.MethodRange  — 方法范围数据类
```

### 同时需修改的现有类

```
com.codelens.common.validators.EvidenceValidator  — 新增 validateRisks 重载方法
```

### 关键实现要点

1. **MethodRange**：不可变类，final 字段，构造 `(String methodName, int startLine, int endLine)`
2. **MethodRange.contains(int line)**：`return line >= startLine && line <= endLine`
3. **EvidenceValidator 新增重载**：
   ```java
   public static ValidationResult validateRisks(String json, List<MethodRange> methodRanges, int totalLines)
   ```
4. **校验逻辑**：
   - methodRanges 为 null 或空 → 降级为整文件范围校验（line ∈ [1, totalLines]）
   - methodRanges 非空 → 每个 risk 的 line 必须落在某个 MethodRange 内，否则标记偏差
   - 嵌套方法匹配第一个
5. **不改现有 `validateRisks(String json, String[] sourceLines, ValidationResult result)` 私有方法签名**，新增公共重载
6. **偏差标记**：行号不在任何方法范围内时，issue 描述 "行号不在任何方法范围内"，confidence = LOW
7. **JDK 1.8 兼容**：不用 List.of / Map.of / var
