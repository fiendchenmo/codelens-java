# 需求 — 隐式依赖检测

> 编号：REQ-C8
> 优先级：🔴 P0
> 工作量：2d
> 前置依赖：无
> 责任人：喵呜
> 交付日期：5/26
> 变更归属：🟠 common变更

## 目的

当前 LLM 分析依赖关系时，只能识别显式调用（方法调用、字段引用）。Java 项目中大量依赖通过反射、Spring IoC、配置文件、注解等方式隐式注入，LLM 基于源码静态分析无法感知这些依赖。

目标：提供隐式依赖检测算法，作为 LLM 分析的补充信息源，帮助 LLM 输出更完整的依赖列表。

## 背景

- C6 AmsBillDataSaveHandler 有 25+ 个 `@Autowired` 字段，LLM 只能识别 1 个
- Spring 的 `@Autowired`/`@Resource`/`@Value` 注解标记的依赖是隐式的，不体现在方法调用链中
- 反射调用（`Class.forName()`、`Method.invoke()`）完全绕过静态分析
- XML/配置文件中的 Bean 引用关系无法从源码直接推导

## 设计方案

### 检测维度

| 维度 | 检测方式 | 置信度 | 示例 |
|------|---------|--------|------|
| Spring 注入 | 扫描 `@Autowired`/`@Resource`/`@Value`/`@Inject` 注解字段 | HIGH | `@Autowired private UserService userService` |
| 事件监听 | 扫描 `@EventListener`/`@TransactionalEventListener` 方法 | HIGH | `@EventListener public void onOrderCreated(OrderEvent e)` |
| 条件装配 | 扫描 `@Conditional`/`@Profile`/`@ConditionalOnProperty` | MEDIUM | `@ConditionalOnProperty(name="feature.enabled")` |
| 反射调用 | 扫描 `Class.forName()`/`getBean()`/`Method.invoke()` 模式 | MEDIUM | `context.getBean("paymentService")` |
| 配置引用 | 扫描 `@Value("${xxx}")` + 关联配置文件 | LOW | `@Value("${payment.gateway.url}")` |
| SPI 加载 | 扫描 `ServiceLoader.load()` 调用 | LOW | `ServiceLoader.load(Plugin.class)` |

### 类设计（→ common）

```
ImplicitDependencyDetector (接口)
├── DetectionContext       # 检测上下文（源码+配置+类路径信息）
├── DetectionResult        # 检测结果（依赖列表+置信度+来源位置）
├── DetectionDimension     # 检测维度枚举
│
├── SpringInjectionDetector   # Spring 注入检测
├── EventListenerDetector     # 事件监听检测
├── ConditionalBeanDetector   # 条件装配检测
├── ReflectionCallDetector    # 反射调用检测
├── ConfigReferenceDetector   # 配置引用检测
└── ServiceProviderDetector   # SPI 加载检测
```

### 检测流程

```
输入：源码文件列表 + 类路径信息
  ↓
按维度并行检测
  ↓
合并 DetectionResult
  ↓
去重 + 置信度排序
  ↓
输出：ImplicitDependencyReport
  - 显式依赖（LLM 已识别的）
  - 隐式依赖（检测器发现的）
  - 疑似依赖（低置信度，需人工确认）
```

### 与现有流程的集成方式

**方式 A（推荐）：检测结果注入 struct 上下文**
- 检测结果作为 struct 的一部分传给 LLM
- LLM 在已有信息基础上补全依赖
- 优点：LLM 可自行判断哪些隐式依赖是真实的

**方式 B：检测结果作为后校验**
- LLM 正常输出，检测器独立运行
- 比对两份结果，标记 LLM 遗漏的隐式依赖
- 优点：不增加 LLM 上下文长度

> 初期采用方式 A，后续可叠加方式 B 做 L3 验证。

### 输出格式

```json
{
  "implicitDependencies": [
    {
      "target": "UserService",
      "type": "SPRING_INJECTION",
      "confidence": "HIGH",
      "evidence": {
        "field": "userService",
        "annotation": "@Autowired",
        "line": 42,
        "sourceFile": "OrderServiceImpl.java"
      }
    }
  ]
}
```

## 变更范围

| 模块 | 变更内容 |
|------|---------|
| codelens-common | 新增 ImplicitDependencyDetector 接口 + 6 个维度实现 |
| codelens-common | 新增 DetectionResult/DetectionContext 等数据类 |
| codelens-cli | JavaParser 层面实现各维度检测器的具体逻辑 |
| codelens-cli | 将检测结果注入 struct 上下文 |

> 注：接口和数据类在 common，具体的 JavaParser 实现在 CLI 端。插件端用 PSI 引擎实现同名接口。

## 验收标准

- [ ] 6 个维度检测器接口在 common 模块中定义
- [ ] CLI 端至少实现 Spring 注入检测 + 事件监听检测
- [ ] C6 测试用例：隐式依赖检出率 > 80%（25+ 个 @Autowired 字段中检出 20+ 个）
- [ ] 误报率 < 20%（检出的依赖中 80%+ 为真实依赖）
- [ ] 检测结果成功注入 struct 上下文，LLM 可感知
- [ ] `mvn test` 全部通过
- [ ] JDK 1.8 语法

## 风险与约束

- JavaParser 无法解析编译后的字节码，仅限源码可见的隐式依赖
- 父类中的 `@Autowired` 需要递归解析继承链（与 REQ-CLI-STRUCT-FIX 相关）
- 检测耗时随文件数量线性增长，大项目需考虑增量检测
- 不改现有 L1/L2 校验逻辑
- JDK 1.8 语法
