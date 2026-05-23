# 需求 — 移除 architecture_issues 独立规则

> 编号：REQ-C1
> 优先级：🔴 立即
> 工作量：5min
> 责任人：喵呜
> 交付日期：5/24
> 变更归属：🟠 common变更

## 目的

当前 SystemPrompt 的 CORE_RULES 中有一条规则要求 LLM 必须输出 3 条 `architecture_issues`。这导致 LLM 在没有真实架构问题时"凑数"，输出低质量或虚假的架构问题，降低分析可信度。

## 变更范围

`codelens-common` 的 `SystemPrompt.java`（或对应常量类）中 CORE_RULES 部分，删除 `architecture_issues` 相关独立规则。

## 具体方案

1. 找到 CORE_RULES 中要求输出 `architecture_issues` 的规则条目
2. 删除该条目
3. 确认 JSON_SCHEMA 中 `architecture_issues` 字段仍保留为可选（LLM 仍可主动输出，但不强制数量）
4. 如 JSON_SCHEMA 中 `architecture_issues` 标记为 `required`，改为 `optional`

## 验收标准

- [ ] CORE_RULES 中不再有强制输出 N 条 `architecture_issues` 的规则
- [ ] JSON_SCHEMA 中 `architecture_issues` 为可选字段
- [ ] `mvn test` 全部通过
- [ ] 基准测试中 LLM 不再为凑数输出 3 条虚假 architecture_issues

## 约束

- 不改 JSON_SCHEMA 的其他字段
- 不改 Normalizer 逻辑
- JDK 1.8 语法
