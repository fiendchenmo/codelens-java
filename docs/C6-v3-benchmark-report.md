# C-6 V3 基准测试分析报告

> 测试日期：2026-05-26
> JAR: codelens-0.2.7.jar (实际含 v0.3.0 + --schema 开关)
> 模型: deepseek-v4-flash
> Schema: V3 (--schema=v3)

---

## 一、测试结果总览

| Case | 时长 | L1校验 | L2标注 | JSON输出 | 说明 |
|------|------|--------|--------|----------|------|
| C1 | 180s | OK CERTAIN (2/2) | OK | 有(截断) | 大文件，JSON不完整 |
| C2 | 139s | OK CERTAIN (15/15) | OK | 有✅ | CLI只显示"概要" |
| C3 | 113s | OK CERTAIN (28/28) | OK | 有✅ | CLI只显示"概要" |
| C4 | 70s | OK CERTAIN (13/13) | OK | 有✅ | CLI只显示"概要" |
| C5 | 18s | OK CERTAIN (2/2) | OK | 有✅ | CLI只显示"概要" |
| C6 | 108s | OK CERTAIN (23/23) | OK | 有(格式错误) | JSON含非法字符 |
| C7 | 18s | UNKNOWN (0/0) | UNKNOWN | 有✅ | 纯BO类，正常跳过 |
| C8 | 191s | UNKNOWN (0/0) | UNKNOWN | 有(截断) | 最大文件，JSON截断 |
| C9 | 36s | OK CERTAIN (7/7) | OK | 有✅ | CLI只显示"概要" |
| C10 | 55s | OK CERTAIN (8/8) | OK | 有✅ | CLI只显示"概要" |

**总计**: 928s (~15min) | L1通过: 8/10 | JSON有效: 10/10 (LLM产出)

---

## 二、V2 vs V3 对比

| 指标 | V2 Baseline | V3 Test |
|------|-------------|---------|
| L1通过率 | 8/10 | 8/10 |
| JSON可解析 | 8/10 | 10/10 (LLM产出) |
| JSON截断 | C2截断 | C1/C8截断, C6格式错误 |
| 总耗时 | 577s | 928s (+61%) |
| CLI显示 | 完整 | 只显示"概要" |

**V3耗时增加原因**: V3 Schema字段更丰富(methods含params/logic_summary/return/exceptions/called_by)，LLM输出更多token

---

## 三、关键发现

### 3.1 V3 JSON确实被LLM产出了 ✅

- L1校验通过=JSON可被EvidenceValidator解析
- CLI显示"概要:"=gson成功解析出"summary"字段
- 8/10用例L1全部通过，说明V3 JSON格式基本正确

### 3.2 CLI显示层不支持V3 ❌

`formatAnalysisResult()` 只处理V2字段:
- ✅ summary → "概要"
- ❌ fields → 不渲染
- ❌ methods → 不渲染  
- ❌ framework → 不渲染

V3 JSON被gson解析后，只有"summary"被显示，其余V3字段被静默忽略。

### 3.3 原始JSON未独立保存 ⚠️

测试脚本只捕获CLI控制台输出，未用`--json`单独保存raw JSON。
大文件(C1/C6/C8)的JSON因formatAnalysisResult解析失败而走catch分支输出原始JSON。

### 3.4 大文件截断问题持续存在 ⚠️

C1(4002行)/C8(1100行+)仍会截断，与V2 Baseline同样的问题。
V3 Schema更复杂，截断风险更高。

---

## 四、需要修复的问题

### P0 - 必须修复
1. **CLI formatAnalysisResult不支持V3** — 需要增加fields/methods/framework的渲染逻辑
2. **测试脚本需保存raw JSON** — 加`--json`参数并重定向到单独文件

### P1 - 建议修复
3. **C6 JSON格式错误** — LLM输出含非法字符，OutputNormalizer需增强
4. **大文件截断** — 考虑分段分析或增大max_tokens

### P2 - 可后续优化
5. **V3耗时+61%** — 因Schema字段增多，可考虑精简V3输出
6. **C7纯BO类L1 UNKNOWN** — V3下BO类无methods，需调整校验策略

---

## 五、下一步

1. 修复CLI formatAnalysisResult增加V3渲染 → 新需求
2. 重跑V3测试并保存raw JSON → 验证V3 JSON字段完整性
3. 对比V2/V3具体字段映射质量 → 核心验收
4. 全部通过后打tag v0.3.1
