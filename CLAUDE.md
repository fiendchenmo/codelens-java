# CLAUDE.md — Claude Code 工作规范

> 本文件是 Claude Code 在本项目中的工作指南。每次被调用时必须遵守。

---

## 1. Git 工作流（强制）

### 每次开始工作前
```bash
git pull --rebase origin main
```
**绝不跳过。** 本地落后远程会导致 push 被拒。

### Push 前
```bash
git pull --rebase origin main   # 再次拉取，防止期间有人推了新commit
git push origin main
```
如果 rebase 有冲突，解决后再 push，绝不 force push。

### Commit 规范
- 格式：`type(scope): description`
- type: feat / fix / refactor / test / docs / chore
- scope: common / cli / cache / validator / prompt / build
- 一个逻辑改动一个 commit，不要把无关改动混在一起
- 示例：`fix(validator): operator precedence in validateDependencies`

---

## 2. 项目结构

```
codelens-java/
├── pom.xml                    # 父POM（packaging=pom）
├── codelens-common/           # 两端共享模块（唯一真相源）
│   ├── pom.xml
│   └── src/main/java/com/codelens/common/
│       ├── cache/             # 缓存（CacheEntry, CacheKeyGenerator, FileSystemCache, CacheConfig）
│       ├── models/            # 数据模型（CodeMetaData）
│       ├── normalizers/       # 输出归一化（OutputNormalizer, StructContext）
│       ├── prompts/           # Prompt模板（SystemPrompt）
│       ├── validators/        # 校验器（EvidenceValidator, ConfidenceAnnotator）
│       ├── providers/         # LLM Provider抽象
│       └── utils/             # 工具类（StringUtil, MethodFilter）
├── codelens-cli/              # CLI端（依赖common）
│   ├── pom.xml
│   └── src/main/java/com/codelens/
│       ├── CodeLensCli.java   # 入口
│       ├── LLMClient.java     # DeepSeek API调用
│       ├── AnalysisService.java
│       ├── JavaParserService.java
│       └── ...
├── benchmark.sh               # 基准测试脚本
├── parse_results.sh           # 结果解析脚本
└── GOVERNANCE.md              # 项目治理规范
```

### 模块边界（不可违反）
- **codelens-common**：零 IntelliJ SDK / JavaParser 依赖，纯文本处理
- **codelens-cli**：依赖 common + JavaParser + SQLite
- **common 是唯一真相源**：任何共享代码只能在 common 中修改，不允许在插件端拷贝副本

---

## 3. 构建与验证

### 构建
```bash
mvn clean package              # 构建（含测试）
mvn clean package -DskipTests  # 跳过测试构建
```

### Fat JAR 路径
```
codelens-cli/target/codelens-0.1.0.jar
```

### 每次改动后必须执行
```bash
mvn compile                    # 编译通过
mvn test                       # 测试通过
```
**两个都通过才能 commit。** 测试不过就修，不要跳过测试提交。

### 运行验证
```bash
export CODELENS_API_KEY=$API_KEY
java -jar codelens-cli/target/codelens-0.1.0.jar full <java_file>
```

---

## 4. 编码规范

### Java
- JDK 8 兼容（不用 var、List.of、Map.of 等 JDK 9+ 特性）
- 行尾符：LF（.gitattributes 强制）
- 中文注释允许，但代码/变量名/方法名必须英文
- 全角字符（""、（）、→、：、；）会导致编译错误，严禁使用

### 依赖管理
- 新增依赖需确认：是否真的需要？能否用已有依赖替代？
- common 模块只允许：Gson、JUnit
- cli 模块允许：JavaParser、SQLite JDBC、Gson、SLF4J
- **禁止引入**：openai-gpt3-java（当前是死依赖，待删除）、任何 Web 框架

### 安全
- **绝不硬编码 API Key / Token / 密码** — 使用环境变量
- `.env` 文件已在 .gitignore 中，但仍需注意不要把敏感信息写进代码
- 缓存文件可能包含代码片段，不要在日志中打印缓存内容

### 缓存规范
- CacheKeyGenerator 必须包含 promptHash + model 维度
- TTL 默认 7 天（CacheConfig.DEFAULT_TTL_DAYS）
- 写缓存前校验结果有效性（null / 空结果 / 解析失败 → 不写缓存）

---

## 5. 常见陷阱

| 陷阱 | 说明 |
|------|------|
| push 被拒 | 本地落后远程 → 先 `git pull --rebase origin main` |
| 编译报全角字符 | 检查中文注释中的""、（）、→等，替换为英文符号 |
| fat JAR 过大 | 检查是否引入了不必要的大依赖（如 openai-gpt3-java） |
| Maven 超时 | 沙箱环境 Maven 不稳定，单次会话内完成编译验证 |
| 测试文件路径 | 测试资源放在 `src/test/resources/`，不要用绝对路径 |
| 运算符优先级 | `&&` 优先级高于 `||`，复杂条件必须加括号 |

---

## 6. 当前待办

> 这部分由喵呜维护，Claude Code 按此执行

详细需求文档：`CodeLens/管理/Claude-Code需求-CLI端评审修复Batch2.md`

当前优先级：
1. JitPack 发布配置（pom.xml groupId 改为 com.github.fiendchenmo）
2. EvidenceValidator 运算符优先级 bug
3. 删 openai-gpt3-java 死依赖
4. architecture_issues Schema 统一（删枚举值）
5. Schema 越界校验
6. 正则 + Gson 双路解析
7. 重复代码提取
8. Cache 实例复用
9. LLMClient Gson 化
