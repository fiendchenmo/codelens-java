# Claude Code 需求 — struct上下文补齐继承字段，修复C6 deps过拟合

> 编号：REQ-CLI-STRUCT-FIX
> 优先级：🔴 P0
> 验证方式：单文件C6测试，确认后全量回归

## 目标

C6 AmsBillDataSaveHandler 有 25+ 个 @Autowired 字段注入，但只有1个（billNoCreateHandler）在当前类声明，其余全在父类。LLM 看不到父类字段，所以只输出1个dep。

通过增强 JavaParserStructExtractor 补齐继承的 @Autowired 字段，让 LLM 能"看到"完整字段列表。

## 背景

已尝试的修复（均无效）：
- v0.2.4: 规则23加"deps数量不做限制"脚注 → deps仍为1
- v0.2.5: 扩充规则23示例3个dep + 硬规则29 + 反面示例 → deps仍为1
- 去掉struct字段列表 → deps仍为1，L1项从28降到10（分析深度变浅）

结论：**问题不在prompt指令，在输入数据不完整。** LLM看不到父类字段，自然列不出来。

## 变更范围

`codelens-cli/src/main/java/com/codelens/JavaParserStructExtractor.java`

当前 struct 输出只包含当前类声明的字段。C6 的 25+ 个 @Autowired 在父类中，LLM 看不到，导致只输出1个dep。

需要让 LLM 能看到继承的 @Autowired/@Resource 字段。具体怎么做你来定。

## 验收标准

- [ ] `mvn test` 全部通过
- [ ] C6 单文件测试 deps ≥ 20（Round 1 为27）
- [ ] 不影响其他文件的 struct 输出（C1/C3/C8等没有继承字段的文件输出不变）
- [ ] 确认有效后全量10文件回归测试，L1 保持100%

## 约束

- 只改 JavaParserStructExtractor.java（及可能的测试文件）
- 不改 SystemPrompt.java
- 不改 pom.xml 版本号（验证通过后再统一升级）
- JDK 1.8 语法
- 父类解析失败时不阻塞（如父类不在源码路径中），graceful降级
