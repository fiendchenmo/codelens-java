# CLI端下一阶段开发规划 — Phase 1 收尾 + Phase 2 启动

> 创建时间：2026-05-21
> 维护人：喵呜
> 状态：待默默评审

---

## 一、当前状态盘点

### Phase 1 进度

| 任务 | 状态 | 说明 |
|------|------|------|
| L1 基准测试 Round 0-4 | 🔄 进行中 | Round 4 待跑（v0.2.5验证C6 deps） |
| P0 修复3项 | ✅ 完成 | km偏差0/Normalizer/max_tokens |
| Few-shot Prompt 优化 | 🔄 收尾中 | v0.2.5 扩展示例+硬规则29 |
| CI | ✅ 完成 | GitHub Actions |
| 项目治理+文档体系 | ✅ 完成 | GOVERNANCE.md + docs/ |

### 对照原始计划（v2.0 Week 5）

| 计划项 | 对应Phase 2任务 | 备注 |
|--------|----------------|------|
| Day 1-2 Few-shot优化 | ✅ 已完成（规则23-25+v0.2.5修复） | 超预期做了多轮迭代 |
| Day 3 EvidenceValidator ±5 | 🟡 可选 | L1已100%，收益不大 |
| Day 4-5 L3交叉验证 | Phase 2 #3 | 跨端一致性验证 |
| Week 4 代码引用跳转 | Phase 2 #8 | CLI端暂不需要（终端无跳转） |
| Week 4 分析结果缓存 | ✅ 已完成 | FileSystemCache已在common |

### 对照产品路线图（v2.0 V1→V2）

| V1功能 | CLI端状态 | 插件端状态 |
|--------|----------|-----------|
| 单文件分析 | ✅ 可用 | ✅ 可用 |
| 结构化结果展示 | ✅ JSON输出 | ✅ ToolWindow |
| 代码引用跳转 | ❌ 终端不支持 | Phase 2 #8 |
| 分析结果缓存 | ✅ FileSystemCache | ✅ FileSystemCache |

| V2功能 | 对应Phase 2任务 | 负责人 |
|--------|----------------|--------|
| 跨文件依赖分析 | #1 | 嗷呜（纯PSI） |
| 影响范围分析 | #2 | 嗷呜+喵呜（反向查询接口） |
| 批量文件分析 | CLI端需新增 | 喵呜 |
| 分析结果对比 | 暂缓 | — |

---

## 二、Phase 1 收尾任务

基准测试通过后，Phase 1 还需完成：

### 2.1 L3 多轮交叉验证（P0）

**目标**：CLI端 vs 插件端分析结果一致性验证

**产出**：
- L3一致性报告（10文件×2端=20组对比）
- 不一致项分类（deps差异/km差异/risk差异）
- 根因分析（JavaParser底图 vs PSI底图差异）

**依赖**：Phase 1 基准测试通过 + 插件端完成基准测试落盘

### 2.2 代码质量 Batch 2（P1，穿插做）

| 任务 | 优先级 | 说明 |
|------|--------|------|
| C2: EvidenceValidator运算符优先级bug | 🔴 P0 | `&&` 优先级高于 `||` |
| C5: Schema越界校验 | 🔴 P0 | 防止LLM输出越界字段 |
| C6: architecture_issues统一 | 🔴 P0 | 输出格式不一致 |
| C7: 正则+Gson双路解析 | 🔴 P0 | 提高解析鲁棒性 |
| W1: 40%重复代码提取 | 🟡 P1 | 减少冗余 |
| W2: 删openai-gpt3-java死依赖 | 🟡 P1 | 减小fat JAR |
| W3: Cache实例复用 | 🟡 P1 | 单例优化 |
| W4: LLMClient用Gson | 🟡 P1 | 统一JSON处理 |

---

## 三、Phase 2 CLI端 + Common 任务规划

按嗷呜Phase 2清单，common归属喵呜的6项 + CLI端自身1项：

### 3.1 任务排期

| 顺序 | # | 任务 | 优先级 | 产出 | 前置依赖 |
|------|---|------|--------|------|---------|
| 1 | #6 | ProviderPreset 温度锁定 | 🟡 P1 | temperature=0.1逻辑→common | 无（最小改动，先跑通流程） |
| 2 | #3 | L3 多轮验证 | 🔴 P0 | 多轮校验算法→common | Phase 1 基准测试通过 |
| 3 | #4 | 隐式依赖检测 | 🔴 P0 | 检测算法→common | #3完成后 |
| 4 | #5 | CallIndex迁移Phase 2 | 🟡 P1 | SQLite+CRUD→common | #6完成后（锁温度后再改Provider） |
| 5 | #7 | 模型特性标记 | 🟡 P1 | 枚举+Schema→common | #6完成后 |
| 6 | #11 | keyMethods增强 | 🟢 P2 | Schema加line+校验器加强 | #3完成后 |
| 7 | — | CLI端批量文件分析 | 🟡 P1 | 目录级分析能力 | 无 |

### 3.2 各任务需求文档规划

每个任务按新工作流走：**需求文档 → 评审 → 测试用例 → 开发 → 审查**

#### REQ-006: ProviderPreset 温度锁定
- **目标**：分析用temperature=0.1，确保结果可复现
- **🟠 common变更**：ProviderPreset新增temperature字段+默认值0.1
- **🔵 CLI端变更**：LLMClient使用ProviderPreset.temperature
- **验收标准**：同一文件连续2次分析，deps/km数量完全一致
- **预估**：半天

#### REQ-003: L3 多轮校验算法
- **目标**：多次分析同一文件，交叉验证结果一致性
- **🟠 common变更**：新增L3CrossValidator，输入多份分析结果，输出一致性评分+差异列表
- **🔵 CLI端变更**：AnalysisService支持多次分析+调用L3校验
- **验收标准**：10文件各跑3次，L3一致性≥90%
- **预估**：2天

#### REQ-004: 隐式依赖检测算法
- **目标**：检测文件契约（接口/抽象类实现关系）、配置注入等非@Autowired依赖
- **🟠 common变更**：新增ImplicitDepDetector，输入结构底图，输出隐式依赖列表
- **🔵 CLI端变更**：JavaParserStructExtractor补充接口实现提取
- **验收标准**：C6（26个@Autowired）+接口实现类能被检测到
- **预估**：2天

#### REQ-005: CallIndex迁移Phase 2
- **目标**：SQLite表结构+CRUD迁入common，插件端适配
- **🟠 common变更**：新增CallIndexStore接口+SQLite实现
- **🔵 CLI端变更**：CallIndex改为调用common接口
- **验收标准**：CLI端CallIndex功能不退化
- **预估**：1天

#### REQ-007: 模型特性标记
- **目标**：不同模型有不同能力标记（如max_tokens、支持function calling等）
- **🟠 common变更**：新增ModelCapability枚举+ProviderPreset关联
- **🔵 CLI端变更**：LLMClient根据ModelCapability调整请求参数
- **验收标准**：Flash模型标记max_tokens=16384，Pro标记推理能力
- **预估**：1天

#### REQ-011: keyMethods增强
- **目标**：Schema加line字段，校验器加强行号验证
- **🟠 common变更**：CodeMetaData.Schema的keyMethods加line字段，EvidenceValidator加行号范围校验
- **🔵 CLI端变更**：适配新Schema
- **验收标准**：L1基准测试不退化
- **预估**：1天

#### REQ-CLI-BATCH: CLI端批量文件分析
- **目标**：支持分析整个目录（而非单个文件）
- **🔵 CLI端变更**：CodeLensCli新增目录扫描+批量分析+汇总报告
- **验收标准**：分析codelens-java自身src/目录，输出所有文件分析汇总
- **预估**：2天

---

## 四、与原计划的对齐

### 原计划v2.0的V2功能 vs Phase 2任务

| 原计划V2功能 | Phase 2对应 | 差异说明 |
|-------------|------------|---------|
| 跨文件依赖分析 | #1（嗷呜做） | CLI端用JavaParser，插件端用PSI |
| 影响范围分析 | #2（嗷呜+喵呜） | 反向查询接口先在CLI端用SQLite FTS实现 |
| 批量文件分析 | REQ-CLI-BATCH | 原计划Week 4提到，Phase 2落地 |
| 分析结果对比 | 暂缓 | 依赖批量分析+版本管理，Phase 3 |

### 原计划三阶段重构 vs 实际进展

| Phase | 原计划 | 实际 |
|-------|--------|------|
| Phase 1 | 双端独立修复 | ✅ 已完成（CLI端9项+插件端M1） |
| Phase 2 | Common目录清理+公共工具类抽取 | 🔄 进行中（Prompt已迁common，Phase 2任务11项已规划） |
| Phase 3 | 独立Git仓库+Maven artifact | ⏳ 暂不急，当前子目录模式够用 |

---

## 五、时间线预估

```
5/21  ─── Phase 1 收尾（基准测试+L3验证）───
          ↓
5/22-23 ── REQ-006 温度锁定（半天）───
          ↓
5/23-25 ── REQ-003 L3多轮校验（2天）───
          ↓
5/26-27 ── REQ-004 隐式依赖检测（2天）───
          ↓
5/28   ── REQ-005 CallIndex迁移（1天）───
          ↓
5/29   ── REQ-007 模型特性标记（1天）───
          ↓
5/30   ── REQ-011 keyMethods增强（1天）───
          ↓
6/1-2  ── REQ-CLI-BATCH 批量文件分析（2天）───
```

穿插代码质量Batch 2（P0的4项在REQ-003之前修完）。

---

## 六、待确认事项

1. **L3验证的判定标准**：CLI端和插件端分析结果差异多大算"一致"？deps数量差1-2个可接受？
2. **#2影响范围分析**：嗷呜标了"可抽common"，我建议先不抽，等插件端稳定再评估。是否同意？
3. **CLI端批量分析优先级**：产品路线图V2要求，但嗷呜的Phase 2没列。要不要提前？
4. **EvidenceValidator ±5**：L1已100%，这个还做不做？
5. **Phase 3独立仓库**：当前子目录模式能撑多久？文件数>30再拆？

---

*本规划待默默评审后生效，按新工作流执行：需求文档→评审→测试用例→开发→审查→验收*
