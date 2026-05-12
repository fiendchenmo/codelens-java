# CodeLens

CodeLens 是一款 Java 遗留代码智能分析 CLI 工具，支持代码索引、反向依赖查询和 LLM 辅助分析。

## 功能特性

- **代码索引**：使用 JavaParser 对 Java 源码进行精确解析，支持 CLASS/IMPORT/METHOD/CALLEE 等索引项
- **反向依赖查询**：快速查找调用指定类的所有代码位置
- **LLM 辅助分析**：结合 DeepSeek 等大语言模型进行代码结构化分析

## 构建方式

```bash
mvn clean package -DskipTests
```

构建产物：`target/codelens-0.1.0.jar`

## 使用示例

### 1. 分析单个 Java 文件

```bash
java -jar target/codelens-0.1.0.jar analyze src/main/java/MyService.java
```

### 2. 使用 API Key 分析

```bash
java -jar target/codelens-0.1.0.jar analyze src/main/java/MyService.java YOUR_API_KEY
```

### 3. 建立代码索引

```bash
java -jar target/codelens-0.1.0.jar index src/main/java
```

### 4. 查询类被谁调用

```bash
java -jar target/codelens-0.1.0.jar callers UserService
```

### 5. 一键分析（索引 + 查询 + LLM 分析）

```bash
java -jar target/codelens-0.1.0.jar full src/main/java/MyService.java YOUR_API_KEY
```

## 环境变量

| 变量名 | 说明 | 优先级 |
|--------|------|--------|
| `CODELENS_API_KEY` | DeepSeek API Key | 低于命令行参数 |

设置环境变量后，分析命令可以省略 API Key 参数：

```bash
export CODELENS_API_KEY=your_api_key
java -jar target/codelens-0.1.0.jar analyze src/main/java/MyService.java
```

## 索引存储

索引数据库位于项目根目录的 `.codelens/code_index.db`。

首次运行 `index` 命令时会自动创建该目录。

## 技术栈

- **JavaParser 3.26.3**：Java 代码 AST 解析
- **SQLite JDBC 3.47.2**：索引存储
- **JUnit 5.9.3**：单元测试
- **DeepSeek API**：LLM 分析（兼容 OpenAI 格式）
