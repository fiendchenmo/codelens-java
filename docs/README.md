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

- **Phase 1（L1 基准测试）**：Round 0-3 完成，v0.2.5
- **Phase 2**：需求文档已编写，待评审后进入开发

## Phase 2 需求文档索引

| 编号 | 需求 | 优先级 | 交付日期 | 状态 |
|------|------|--------|----------|------|
| REQ-C1 | 移除 architecture_issues 独立规则 | 🔴 立即 | 5/24 | 📝 待评审 |
| REQ-C7 | L3 多轮验证 | 🔴 P0 | 5/25 | 📝 待评审 |
| REQ-C8 | 隐式依赖检测 | 🔴 P0 | 5/26 | 📝 待评审 |
| REQ-C2 | EvidenceValidator 加 methodRanges | 🟡 P1 | 5/27 | 📝 待评审 |
| REQ-C3 | SchemaVersion 枚举 + JSON_SCHEMA 版本化 | 🟡 P1 | 5/27 | 📝 待评审 |
| REQ-C4 | SystemPrompt 双模板 + 两阶段填充说明 | 🟡 P1 | 5/28 | 📝 待评审 |
| REQ-C5 | Normalizer V3 分支 | 🟡 P1 | 5/28 | 📝 待评审 |
| REQ-C10 | ProviderPreset 温度锁定 | 🟡 P1 | 5/26 | 📝 待评审 |
| REQ-C11 | 模型特性标记 | 🟡 P1 | 5/29 | 📝 待评审 |
| REQ-C6 | 双版测试 | 🟡 P1 | 5/29 | 📝 待评审 |
| REQ-C9 | CallIndex 迁移 Phase 2 | 🟢 P2 | 5/30~31 | 📝 待评审 |

排期：5/24 C-1+C-7 → 5/25 C-7 → 5/26 C-8+C-10 → 5/27 C-2+C-3 → 5/28 C-4+C-5 → 5/29 C-6+C-11 → 5/30~31 C-9
