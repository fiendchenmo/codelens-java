# 需求 — EvidenceValidator 加 methodRanges

> 编号：REQ-C2
> 优先级：🟡 P1
> 工作量：0.5d
> 前置依赖：无
> 责任人：喵呜
> 交付日期：5/27
> 变更归属：🟠 common变更

## 目的

当前 EvidenceValidator 校验 risks.line 时，只检查行号是否在文件总行数范围内。无法判断风险项是否落在对应方法体内，导致行号偏差校验粒度过粗。

目标：新增 methodRanges 参数，支持方法级行号校验。当提供 methodRanges 时，校验 risks.line 是否在对应方法范围内；不提供时降级为整文件校验。

## 变更范围

`codelens-common` 的 `EvidenceValidator.java`

## 设计方案

### 新增数据类

```java
public class MethodRange {
    private final String methodName;
    private final int startLine;
    private final int endLine;
    
    // constructor, getters
    public boolean contains(int line) {
        return line >= startLine && line <= endLine;
    }
}
```

### 重载 validateRisks 方法

```java
// 原有方法（降级为整文件校验）
public ValidationResult validateRisks(List<RiskItem> risks, int totalLines);

// 新增重载（方法级校验）
public ValidationResult validateRisks(List<RiskItem> risks, List<MethodRange> methodRanges);
```

### 校验逻辑

- 传入 `methodRanges`：每个 risk 的 line 必须落在某个 MethodRange 内，否则标记偏差
- 不传或传入空列表：降级为原有整文件行号范围校验
- 一个 risk 的 line 可能落在多个方法范围内（嵌套/内部类），匹配第一个即可

## 验收标准

- [ ] MethodRange 类在 common 模块中
- [ ] validateRisks 重载方法在 common 模块中
- [ ] 不传 methodRanges 时行为与现有逻辑完全一致（降级）
- [ ] 传入 methodRanges 时，risks.line 超出所有方法范围的项被标记为偏差
- [ ] `mvn test` 全部通过
- [ ] JDK 1.8 语法

## 约束

- 不改现有 validateRisks(List, int) 方法签名
- MethodRange 是不可变类（final 字段 + 无 setter）
- JDK 1.8 语法
