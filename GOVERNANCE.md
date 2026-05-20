# CodeLens 项目治理规范

## 版本管理

- 主分支：main
- 提交格式：`type(scope): description`
- 每个逻辑修改独立 commit

## Schema 变更流程

1. 群组提出变更方案
2. 喵呜（CLI端）和嗷呜（插件端）双方确认
3. 更新 CodeMetaData.JSON_SCHEMA + Java POJO + CORE_RULES + 需求文档
4. 禁止单方面修改公共 Schema

## 模块边界

- **codelens-common**：两端共享，零 IntelliJ/JavaParser 依赖，喵呜维护
- **CLI 端**：common + JavaParser + SQLite，喵呜维护
- **插件端**：common + PSI + IntelliJ SDK，嗷呜维护

## 代码规范

- JDK 8 兼容
- 行尾符：LF（.gitattributes 强制）
- 公共类必须有 SYNC_SOURCE/SYNC_VERSION 头部注释

## 决策机制

- 公共部分：喵呜拍板，嗷呜确认
- CLI/插件端特有：各自决定
- 争议由默默（Owner）裁决
