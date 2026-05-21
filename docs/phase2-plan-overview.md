# CodeLens 文档体系 & Phase 2 分工（同步给喵呜）

> 2026-05-22 定版

---

## 一、文档体系

所有项目文档统一管理在各自仓库的 `docs/` 目录下，git 版本控制。

### 插件端 (`codelens-plugin/docs/`)
```
docs/
├── README.md                   # 索引入口
├── requirements/               # 需求文档
│   ├── 001-cross-file-deps.md  # P0 跨文件依赖追踪（当前）
│   └── ...                     # 后续新增
├── test-cases/                 # 测试用例（需求评审后补充）
└── architecture/               # 架构文档
```

### CLI 端 (`codelens-java/docs/`) — 等喵呜确认是否也建
建议喵呜那边也建同构的 `docs/` 目录，统一管理。

---

## 二、Phase 2 任务清单（含 common 拆分 + 责任人）

| # | 任务 | 优先级 | common 部分 | 插件端部分 | 责任人 |
|---|------|--------|-------------|------------|--------|
| 1 | **跨文件依赖追踪** | 🔴 P0 | ❌ 纯 PSI 解析，不涉及 common | PSI 类型解析 → import 匹配 → 文件路径 → 注入 Prompt → ToolWindow 展示 | 嗷呜 + Cline |
| 2 | **影响范围分析** | 🔴 P0 | ⚠️ 反向查询接口可抽 common | FTS 索引查"谁引用了这个类"→ 结果排序 → 展示 | 嗷呜 + Cline |
| 3 | **完整 L3 多轮验证** | 🔴 P0 | ✅ 多轮校验算法 → common | 编排层（触发多次分析 → 收集结果 → 传给 common 校验器） | **common: 喵呜** / **插件端: 嗷呜+Cline** |
| 4 | **隐式依赖检测** | 🔴 P0 | ✅ 检测算法（文件契约、接口实现关系） | PSI 增强（从 PSI 索引补充检测结果） | **common: 喵呜** / **插件端: 嗷呜+Cline** |
| 5 | **CallIndex 迁移 Phase 2** | 🟡 P1 | ✅ SQLite 表结构 + CRUD 方法迁 common | 插件端 FtsIndexer 适配 common 接口 | **common: 喵呜** / **插件端: 嗷呜+Cline** |
| 6 | **ProviderPreset 温度锁定** | 🟡 P1 | ✅ temperature=0.1 逻辑在 common | 插件端验证温度生效 + UI 可能需调整 | **common: 喵呜** / **插件端: 嗷呜+Cline** |
| 7 | **模型特性标记 Phase 2** | 🟡 P1 | ✅ 模型特性枚举 + Schema → common | 插件端集成 UI（下拉框筛选模型） | **common: 喵呜** / **插件端: 嗷呜+Cline** |
| 8 | **代码引用点击跳转** | 🟢 P2 | ❌ 纯 IntelliJ SDK（PsiElement 导航） | ToolWindow 行号 → PsiElement → navigate() | 嗷呜 + Cline |
| 9 | **多文件分析 Tab 页** | 🟢 P2 | ❌ 纯 IntelliJ UI | ToolWindow 多 Tab 展示 | 嗷呜 + Cline |
| 10 | **基准结果落盘保存** | 🟢 P2 | ❌ 插件端文件 I/O | 分析结果写入本地文件 | 嗷呜 + Cline |
| 11 | **keyMethods 增强 L1 校验** | 🟢 P2 | ✅ Schema 加 `line` 字段 + 校验器加强 | 插件端适配新 Schema | **common: 喵呜** / **插件端: 嗷呜+Cline** |

---

## 三、责任划分总表

| 方面 | 责任人 | 说明 |
|------|--------|------|
| 插件端 PSI 解析 / UI / 交互 | **嗷呜 + Cline** | 纯插件端能力，零 common 依赖 |
| common 校验器 / Schema / Prompt | **喵呜** | 定规范，两端共用 |
| common 数据库 / 缓存层 | **喵呜** | CallIndex、缓存逻辑 |
| common 与插件端适配层 | **嗷呜** | 插件端调用 common 的桥接代码 |
| 需求文档 | **嗷呜** | 插件端需求文档我写，common 部分你来定 |
| 文档评审 | **默默** | 最终确认 |

---

## 四、工作流程（新）

```
嗷呜写需求文档 → 默默评审 → 喵呜确认 common 部分
    → 测试用例 → Cline 开发 → Cline 自测 → 嗷呜审查 → 默默验收
```

每份需求文档分两个 section：
- **🔵 插件端实现**（嗷呜这边做）
- **🟠 common 变更**（喵呜那边做，如有）

两份都确认后才开干。
