# CodeLens CLI 端文档

> 维护人：喵呜
> 最后更新：2026-05-21

---

## 目录结构

```
docs/
├── README.md                   # 本文件（索引入口）
├── requirements/               # 需求文档
│   └── ...                     # 按编号管理
├── phase2-plan-overview.md     # Phase 2 分工总览（与插件端同步）
└── architecture/               # 架构文档（待建）
```

## 与插件端文档的关系

- 插件端文档：`codelens-plugin/docs/`
- 文档体系同构，需求文档分 🔵插件端 / 🟠common变更 / 📋Schema 三色标签
- common 变更部分由喵呜在 CLI 端 docs/ 下补充，与插件端需求文档双向关联

## 当前工作流

```
嗷呜写需求文档 → 默默评审 → 喵呜确认 common 部分
    → 测试用例 → Claude Code 开发 → 喵呜审查 → 默默验收
```

## 当前进度

- **Phase 1（L1 基准测试）**：Round 0-3 进行中，C6 deps过拟合修复中（v0.2.5）
- **Phase 2**：11项任务已规划，等Phase 1收敛后启动

## Phase 2 common 归属任务（喵呜负责）

| # | 任务 | 优先级 | 产出 |
|---|------|--------|------|
| 3 | 完整 L3 多轮验证 | 🔴 P0 | 校验算法 → common |
| 4 | 隐式依赖检测 | 🔴 P0 | 检测算法 → common |
| 5 | CallIndex 迁移 Phase 2 | 🟡 P1 | SQLite+CRUD → common |
| 6 | ProviderPreset 温度锁定 | 🟡 P1 | 温度逻辑 → common |
| 7 | 模型特性标记 | 🟡 P1 | 枚举+Schema → common |
| 11 | keyMethods 增强 | 🟢 P2 | Schema+校验器 → common |

建议执行顺序：#6 → #3 → #4 → #5 → #7 → #11
