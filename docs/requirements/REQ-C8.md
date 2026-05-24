# 需求 — 隐式依赖检测（v2，基于 PSI 图引擎）

> 编号：REQ-C8
> 优先级：🔴 P0
> 工作量：2d
> 前置依赖：P-1 PSI 图引擎（已完成）
> 责任人：喵呜
> 交付日期：5/26
> 变更归属：🟠 common变更 + 🔵插件端增强

## 目的

当前 LLM 分析依赖关系时，只能识别显式方法调用。Java 项目中大量依赖通过 Spring IoC 隐式注入，LLM 基于源码静态分析无法感知。

**P-1 完成后的能力跃升**：method_nodes 和 class_nodes 已有 annotations 字段，`@Autowired`/`@Resource` 等注解在索引时已提取并存入图引擎。隐式依赖检测不再需要逐文件重新解析，直接从图引擎查询即可。

## P-1 图引擎现有数据（可直接利用）

| 表 | 字段 | 隐式依赖检测用途 |
|---|---|---|
| class_nodes | annotations | 类级注解：`@Service`/`@Configuration` → 识别 Bean 定义 |
| class_nodes | stereotype | SERVICE/CONTROLLER/REPOSITORY → 推断 DI 角色 |
| class_nodes | implements_ifs | 接口实现关系 → `@Autowired X` 实际注入的是 XImpl |
| method_nodes | annotations | 方法级注解：`@EventListener`/`@Bean`/`@PostConstruct` |
| method_nodes | class_id | 方法→类关联 → 找到 `@Autowired` 字段所属的类 |
| method_calls_v2 | confidence | RESOLVED/LIKELY/UNRESOLVED → 区分显式和隐式调用 |

## 设计方案

### 检测维度（重新分级）

| 维度 | 数据来源 | CLI 端 | 插件端 | 置信度 |
|------|---------|--------|--------|--------|
| Spring 注入 | class_nodes.annotations + PSI 字段扫描 | JavaParser 扫描 `@Autowired`/`@Resource`/`@Inject` 字段 | 图引擎查 annotations + PSI 字段类型 → resolve 实现类 | HIGH |
| 接口→实现映射 | class_nodes.implements_ifs | JavaParser 扫描 implements | 图引擎查 class_nodes + PsiClass.resolve() | HIGH |
| 事件监听 | method_nodes.annotations | JavaParser 扫描 `@EventListener` | 图引擎查 annotations LIKE '%EventListener%' | HIGH |
| 条件装配 | class_nodes.annotations | JavaParser 扫描 `@Conditional`/`@Profile` | 图引擎查 annotations LIKE '%Conditional%' | MEDIUM |
| 反射/getBean | method_calls_v2 + 模式匹配 | JavaParser 扫描 `getBean()`/`Class.forName()` | 图引擎查 method_calls + PSI resolve | MEDIUM |
| 配置引用 | method_nodes.annotations | JavaParser 扫描 `@Value("${xxx}")` | 图引擎查 annotations LIKE '%Value%' | LOW |

### 两端能力差异

| 能力 | CLI 端（JavaParser） | 插件端（PSI + 图引擎） |
|------|---------------------|---------------------|
| `@Autowired` 字段检出 | ✅ 扫描字段声明+注解 | ✅ 图引擎 annotations 查询 |
| 接口→实现类解析 | ❌ 无法跨文件 resolve | ✅ class_nodes.implements_ifs + PSI resolve |
| `@Autowired X` 注入的实际类型 | ❌ 只知道 X 是接口 | ✅ 可查到 XImpl 并注入上下文 |
| 事件监听链路 | ❌ 只知道有 `@EventListener` | ✅ 可追踪事件发布者→监听者 |
| 检测耗时 | O(文件数)，无缓存 | 首次索引后 O(1)，直接 SQL |

### 类设计（→ common）

```
ImplicitDependencyDetector (接口)
├── DetectionContext       # 检测上下文（源码 + 图引擎查询结果 + 类路径信息）
├── DetectionResult        # 检测结果（依赖列表 + 置信度 + 来源位置 + 解析状态）
├── DetectionDimension     # 检测维度枚举（6 种）
│
├── SpringInjectionDetector   # Spring 注入检测
├── InterfaceImplDetector     # 接口→实现映射（P-1 后新增维度）
├── EventListenerDetector     # 事件监听检测
├── ConditionalBeanDetector   # 条件装配检测
├── ReflectionCallDetector    # 反射调用检测
└── ConfigReferenceDetector   # 配置引用检测
```

**新增**：`InterfaceImplDetector`——利用 class_nodes.implements_ifs 字段，将 `@Autowired OrderMapper` 解析到 `OrderMapperImpl`。这是 P-1 带来的核心能力提升。

### DetectionResult 增强字段

```json
{
  "implicitDependencies": [
    {
      "target": "UserService",
      "resolvedTarget": "UserServiceImpl",
      "type": "SPRING_INJECTION",
      "confidence": "HIGH",
      "resolution": "PSI",
      "evidence": {
        "field": "userService",
        "annotation": "@Autowired",
        "line": 42,
        "sourceFile": "OrderServiceImpl.java",
        "interfaceClass": "com.example.service.UserService",
        "implClass": "com.example.service.impl.UserServiceImpl"
      }
    }
  ]
}
```

**新增字段**：
- `resolvedTarget`：接口的实际实现类（仅插件端有值，CLI 端为 null）
- `resolution`：解析方式 PSI / IMPORT_MATCH / UNRESOLVED（与图引擎 confidence 对齐）
- `evidence.interfaceClass` / `evidence.implClass`：接口→实现映射证据

### 与现有流程的集成方式

#### 插件端（方式 A+，基于图引擎）

```
PsiGraphEngine 查询
  ├── class_nodes.annotations → 检出 @Autowired 字段
  ├── class_nodes.implements_ifs → 解析接口实现类
  ├── class_nodes.stereotype → 识别 DI 角色
  └── method_nodes.annotations → 检出 @EventListener 等
  ↓
检测结果注入 struct 上下文（external_context_nodes 区块）
  ↓
LLM 收到的信息：
  - @Autowired UserService userService  ← 注入声明
  - UserService → UserServiceImpl       ← 接口实现映射
  - UserServiceImpl 的方法列表           ← 来自 method_nodes
  ↓
LLM 输出包含完整依赖链
```

**与之前方式 A 的区别**：不再是"检测到注入点，让 LLM 猜实现类"，而是"检测到注入点 + 已解析实现类 + 实现类方法列表，LLM 直接用"。

#### CLI 端（方式 A 基础版）

```
JavaParser 扫描
  ├── 字段声明 + 注解 → 检出 @Autowired 字段
  └── import 声明 → 推断可能的实现类（IMPORT_MATCH）
  ↓
检测结果注入 struct 上下文
  ↓
LLM 收到的信息：
  - @Autowired UserService userService  ← 注入声明
  - import com.example.service.UserService ← import 线索
  ↓
LLM 自行推断实现类（准确度低于插件端）
```

#### 方式 B（L3 验证，P-1 后可落地）

```
LLM 正常输出依赖列表
  ↓
图引擎查询该类的 class_nodes.annotations
  ↓
比对：LLM 列出的依赖 vs 图引擎检出的 @Autowired 字段
  ↓
LLM 遗漏的 → 标记为 "MISSED_IMPLICIT_DEP"
  ↓
此结果作为 C-7 L3 ConstraintValidator 的约束源
```

**这是之前规划中"后续叠加方式 B"的具体落地路径**，P-1 完成后有了 ground truth 数据源，方式 B 可行。

### 与 C-7 L3 验证的联动

| 场景 | C-8 数据来源 | C-7 验证策略 |
|------|-------------|-------------|
| LLM 声称依赖 X | 图引擎 class_nodes | ConstraintValidator：X 是否在 class_nodes 中？ |
| LLM 遗漏 @Autowired Y | 图引擎 annotations | CrossValidator：二次提问"是否有隐式注入？" |
| 接口→实现不一致 | class_nodes.implements_ifs | ConstraintValidator：实现类是否匹配？ |

## 变更范围

| 模块 | 变更内容 |
|------|---------|
| codelens-common | 新增 ImplicitDependencyDetector 接口 + 7 个维度实现（含 InterfaceImplDetector） |
| codelens-common | 新增 DetectionResult/DetectionContext 等数据类（含 resolvedTarget 字段） |
| codelens-cli | JavaParser 实现 Spring 注入 + 事件监听 + 反射调用检测 |
| codelens-cli | 检测结果注入 struct 上下文 |
| 插件端 | 基于图引擎实现全维度检测（P-2 插件端切 V3 时集成） |

## 验收标准

- [ ] 7 个维度检测器接口在 common 模块中定义
- [ ] CLI 端实现 Spring 注入检测 + 事件监听检测 + 反射调用检测
- [ ] C6 测试用例：隐式依赖检出率 > 80%
- [ ] 误报率 < 20%
- [ ] DetectionResult 含 resolvedTarget 字段（CLI 端可为 null，插件端有值）
- [ ] 检测结果成功注入 struct 上下文，LLM 可感知
- [ ] `mvn test` 全部通过
- [ ] JDK 1.8 语法

## 风险与约束

- JavaParser 无法跨文件 resolve 接口实现，CLI 端 resolvedTarget 为 null
- 图引擎 annotations 字段为 JSON 数组字符串，查询需 LIKE 或应用层解析
- 不改现有 L1/L2 校验逻辑
- JDK 1.8 语法
