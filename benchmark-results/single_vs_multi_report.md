# CodeLens Benchmark: single vs multi comparison

**Date**: 2026-05-29
**JAR**: codelens-0.4.0
**Model**: deepseek-v4-flash (--no-cache)

## 耗时对比

| Case | Single | Multi | 加速比 | 说明 |
|------|--------|-------|--------|------|
| C1 | 115s | 38s | **3.0x** | EcsBillDataSaveHandler（10个方法） |
| C2 | 60s | 32s | **1.9x** | CreateTravelSettleAccountsHandler |
| C3 | 74s | 22s | **3.4x** | SysDimenController（3个方法并行） |
| C4 | 50s | 27s | **1.9x** | LoginController |
| C5 | 13s | 15s | 0.9x | CommonBillInfoController（仅1个方法，无并行收益） |
| C6 | 40s | 47s | 0.9x | AmsBillDataSaveHandler |
| C8 | 73s | 32s | **2.3x** | ProcurementApprovalServiceImpl |
| C9 | 35s | 25s | 1.4x | SysUserServiceImpl |
| C10 | 32s | 28s | 1.1x | KingdeeDataServiceImpl |
| **合计** | **492s** | **266s** | **1.85x** | 多方法文件收益显著 |

## 输出大小对比

| Case | Single | Multi | 差异 |
|------|--------|-------|------|
| C1 | 81KB | 92KB | multi 含完整 JSON 结构化输出 |
| C2 | 28KB | 14KB | single 含框架分析文本 |
| C3 | 15KB | 32KB | multi 含 methods 数组 + executionTrace |
| C4 | 8KB | 14KB | |
| C5 | 2KB | 2KB | 基本一致（仅1个方法） |
| C6 | 15KB | 37KB | |
| C8 | 19KB | 47KB | |
| C9 | 5KB | 9KB | |
| C10 | 8KB | 10KB | |

## 关键发现

1. **并行加速有效**: 多方法文件（C1 10个方法, C3 3个方法）加速比 2-3x。C1 从 115s 降到 38s（3x）。
2. **单方法文件无收益**: C5 仅 1 个方法，multi 模式增加了 SUMMARY 开销（+2s）。
3. **C6 异常**: multi 比 single 慢 7s，可能原因是方法间调用链分析存在依赖等待。
4. **输出格式差异**: single 输出人类可读分析文本，multi 输出结构化 JSON（含 executionTrace 元数据）。
5. **总加速比 1.85x**: 9 个文件合计从 492s 降到 266s。

## 原始输出

- [single outputs](single/)
- [multi outputs](multi/)
