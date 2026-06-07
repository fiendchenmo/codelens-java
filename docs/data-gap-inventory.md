# 数据缺失清单 — Common 端 POJO 字段增强

> 生成日期: 2026-06-04
> 目标: 补充 AggregateSummaryOutput / ImpactSummary / ChangedFile&ChangedMethod / V3L2Item 缺失字段

---

## 1. AggregateSummaryOutput — 需新增字段

| 字段 | 类型 | 用途 | 状态 |
|------|------|------|------|
| `avgComplexity` | double | 包分析统计条"平均复杂度" | ❌ 无 |
| `l1PassRate` | double | 包分析统计条"L1通过率" | ❌ 无 |
| `analysisElapsedMs` | long | 包分析Header"分析耗时" | ❌ 无 |
| `lowRiskCount` | int | 风险分布"低风险"数字 | ❌ 无 |
| `classEntries` | `List<ClassEntry>` | 类卡片列表（类名/方法数/风险统计） | ❌ 无 |
| `internalDeps` | `List<InternalDep>` | 包内依赖（源类→目标类/调用次数） | ❌ 无 |
| `complexityDistribution` | `Map<String,Integer>` | 复杂度分布（`"1-2"`/`"3-5"`/`"6+"` → 方法数） | ❌ 无 |
| `visibilityDistribution` | `Map<String,Integer>` | 可见性分布（public/protected/private → 方法数） | ❌ 无 |

### 需新增内部类

```java
class ClassEntry {
    String className;
    int methodCount;
    int highRiskCount;
    int mediumRiskCount;
    double avgComplexity;
    String filePath;
}

class InternalDep {
    String sourceClass;
    String targetClass;
    int callCount;
}
```

---

## 2. ImpactSummary — 需新增字段

| 字段 | 类型 | 用途 | 状态 |
|------|------|------|------|
| `addedRiskCount` | int | Diff统计条"新增风险" | ❌ 无 |
| `eliminatedRiskCount` | int | Diff统计条"消除风险" | ❌ 无 |
| `addedLines` | int | Diff统计条"新增行数" | ❌ 无 |
| `deletedLines` | int | Diff统计条"删除行数" | ❌ 无 |
| `modifiedLines` | int | Diff统计条"修改行数" | ❌ 无 |
| `riskChanges` | `List<RiskChange>` | 风险变更对比（消除/新增/未变更） | ❌ 无 |

### 需新增内部类

```java
class RiskChange {
    String riskDescription;
    String severity;
    int line;
    ChangeStatus changeStatus;  // ELIMINATED / NEW / UNCHANGED / IMPROVED
    String version;
    String suggestion;
}

enum ChangeStatus {
    ELIMINATED, NEW, UNCHANGED, IMPROVED
}
```

---

## 3. ChangedFile / ChangedMethod — 需新增字段

| 类 | 字段 | 类型 | 用途 | 状态 |
|----|------|------|------|------|
| `ChangedMethod` | `changeType` | `ChangeType` | 方法级变更类型（ADDED/MODIFIED/DELETED） | ❌ 只有文件级 |
| `ChangedMethod` | `impactScope` | String | 影响范围描述（如 `"Calls影响: 2个调用变更"`） | ❌ 无 |

---

## 4. V3L2Item — 确认已有

| 字段 | 类型 | 用途 | 状态 |
|------|------|------|------|
| `claim` | String | L2分项评分的评估项名 | ✅ 已有 |
| `score` | double | L2分项评分分数 | ✅ 已有 |

无需改动。
