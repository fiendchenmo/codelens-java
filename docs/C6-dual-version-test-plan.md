# C-6 双版测试计划

> 测试日期：2026-05-26
> 测试目标：验证 V2/V3 双 Schema 端到端可用性
> 验收标准：261 单测全过 + V2 输出不变 + V3 输出合规

---

## 一、前置检查

- [ ] `mvn test -pl codelens-common` 261 tests 全过
- [ ] CLI 端 `mvn package -pl codelens-cli` 构建成功
- [ ] API Key 可用（CODELENS_API_KEY 环境变量）

## 二、CLI 端测试

### 2.1 构建 JAR

```bash
cd codelens-java
mvn clean package -pl codelens-cli -DskipTests
# JAR 位置: codelens-cli/target/codelens-cli-0.2.7.jar
```

### 2.2 V2 Baseline（确认无回归）

对以下 7 个文件执行 V2 分析（默认模式，不加 --schema）：

| ID | 类名 | 行数 | 预期 |
|----|------|------|------|
| C1 | EcsBillDataSaveHandler | 4002 | 输出含 dependencies/keyMethods/design_intent |
| C2 | InvoiceOCRHandler | 3070 | 同上 |
| C3 | CreateTravelSettleAccounts | 498 | 同上 |
| C4 | SysDimenController | 497 | 同上 |
| C5 | KingdeeDataServiceImpl | 212 | 同上 |
| C6 | LoginController | 293 | 同上 |
| C7 | PaymentResponse | 99 | 同上 |

命令模板：
```bash
CODELENS_API_KEY=$KEY java -jar codelens-cli/target/codelens-cli-0.2.7.jar \
  analyze <源文件路径> --no-cache --json --no-color > raw/C6_V2_C<i>.json 2>&1
```

V2 验收项：
- [ ] 每个 JSON 含 `dependencies` + `keyMethods` + `design_intent` 字段
- [ ] 无 `fields` + `methods` 顶层字段
- [ ] L1 校验通过率 ≥ 90%

### 2.3 V3 测试

对同样 7 个文件执行 V3 分析（加 --schema=v3）：

命令模板：
```bash
CODELENS_API_KEY=$KEY java -jar codelens-cli/target/codelens-cli-0.2.7.jar \
  analyze <源文件路径> --schema=v3 --no-cache --json --no-color > raw/C6_V3_C<i>.json 2>&1
```

V3 验收项：
- [ ] 每个 JSON 含 `fields` + `methods` + `framework` + `summary` 顶层字段
- [ ] 无 `dependencies` + `keyMethods` + `design_intent` + `class_analysis` 字段
- [ ] `methods[].calls` 为对象数组（含 target/line/type）
- [ ] `methods[].risks` 含 type 枚举（SECURITY/PERFORMANCE/MAINTAINABILITY）
- [ ] Prompt 中含 "两阶段填充协议" + "[FACT]" + "[INFER]"
- [ ] L1 校验通过（行号范围检查）

### 2.4 V2 vs V3 对比

| 对比项 | V2 | V3 |
|--------|----|----|
| 顶层字段数 | 8 (summary,design_intent,class_analysis,deps,risks,km,framework_integration,arch_issues) | 4 (summary,framework,fields,methods) |
| 依赖表示 | dependencies[] | fields[] + methods[].calls |
| 风险位置 | 顶层 risks[] | methods[].risks |
| 方法列表 | keyMethods[] | methods[] |
| 框架分析 | framework_integration | framework |
| 填充协议 | 无 | [FACT]/[INFER] 两阶段 |

## 三、插件端测试（嗷呜负责）

### 3.1 前置条件

- [ ] 插件端已升级 common 依赖至 v0.3.0
- [ ] ReportBuilder 支持渲染 V3 输出格式
- [ ] Prompt 切换为 buildBase(SchemaVersion.V3)

### 3.2 测试流程

对 T1-T12 文件在插件端执行 V3 分析：

| ID | 类名 | 行数 |
|----|------|------|
| T1 | EcsBillDataSaveHandler | 4002 |
| T2 | InvoiceOCRHandler | 3070 |
| T3 | CreateTravelSettleAccounts | 498 |
| T4 | SysDimenController | 497 |
| T5 | PurchaseOrderInitHandler | 297 |
| T6 | LoginController | 293 |
| T7 | KingdeeDataServiceImpl | 212 |
| T8 | InterfaceNccLogServiceImpl | 171 |
| T9 | InvoiceOtherMethodsService | 130 |
| T10 | PmsBillButtonHandler | 152 |
| T11 | PaymentResponse | 99 |
| T12 | PmsPendReceiptAuthorize | 50 |

插件端 V3 验收项：
- [ ] UI 正确渲染 fields/methods/framework 面板
- [ ] methods 展开显示 calls/risks/exceptions 子面板
- [ ] [FACT]/[INFER] 标签可见
- [ ] V2 模式下 UI 不受影响（向后兼容）

## 四、结果归档

```
基准测试/benchmark-results/
├── raw/
│   └── C6_dual_version/          # 本次测试原始数据
│       ├── V2_C1.json ... V2_C7.json
│       ├── V3_C1.json ... V3_C7.json
│       └── plugin_V3_T1.txt ... plugin_V3_T12.txt
└── reports/
    └── 2026-05-26_C6_dual_version_report.md
```

## 五、Go/No-Go 检查点

| 检查点 | 通过条件 | 状态 |
|--------|---------|------|
| 单元测试 | 261 tests 0 fail | ✅ |
| V2 无回归 | 7/7 文件输出格式正确 | ⏳ |
| V3 Schema 合规 | 7/7 文件含 fields+methods | ⏳ |
| V3 两阶段标注 | prompt 含 FACT/INFER | ⏳ |
| 插件端 V3 渲染 | 12/12 文件 UI 正常 | ⏳ |
| 插件端 V2 兼容 | V2 模式 UI 不受影响 | ⏳ |

全部通过 → C-6 收工，打 tag v0.3.1
