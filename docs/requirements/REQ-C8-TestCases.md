# REQ-C8 测试用例 — 隐式依赖检测

> 需求编号：REQ-C8
> 需求文档：`docs/requirements/REQ-C8.md`
> 测试源码：`codelens-common/src/test/java/com/codelens/common/detectors/implicit/ImplicitDependencyDetectorTest.java`
> 创建日期：2026-05-24

---

## 一、需求验收标准 → 测试用例映射

| # | 需求验收标准 | 对应测试用例 | 覆盖状态 |
|---|-------------|-------------|---------|
| A1 | 7 个维度检测器接口在 common 模块中定义，通过单元测试 | testDetectorInterfaceDetect, testDetectorInterfaceDetectAll, testDetectionDimensionEnumValues | ✅ |
| A2 | CLI 端实现 Spring 注入 + 事件监听 + 反射调用检测 | testSpringInjectionDetectAutowired, testEventListenerDetection, testReflectionGetBeanDetection | ✅ |
| A3 | C6 测试用例：隐式依赖检出率 > 80% | （集成测试，不在本文件范围） | ⏳ 集成阶段 |
| A4 | 误报率 < 20% | （集成测试，不在本文件范围） | ⏳ 集成阶段 |
| A5 | DetectionResult 含 resolvedTarget 字段（CLI 可为 null，插件端有值） | testImplicitDependencyWithResolvedTarget, testImplicitDependencyWithoutResolvedTarget, testCLISideUnresolvedTarget, testPluginSideResolvedTarget | ✅ |
| A6 | 检测结果成功注入 struct 上下文，LLM 可感知 | testStructContextInjection | ✅ |
| A7 | mvn test 全部通过 | 全部测试通过即满足 | ✅ |
| A8 | JDK 1.8 语法 | testJdk8Compatibility | ✅ |

---

## 二、需求设计方案 → 测试用例映射

### 2.1 检测维度 → 测试

| 需求维度 | 置信度 | 对应测试 | 说明 |
|---------|--------|---------|------|
| Spring 注入 | HIGH | testSpringInjectionDetectAutowired, testSpringInjectionDetectResource, testSpringInjectionDetectInject, testSpringInjectionNoAnnotations, testSpringInjectionConfidenceHigh | @Autowired/@Resource/@Inject |
| 接口→实现映射 | HIGH | testInterfaceImplResolution, testInterfaceImplNoImplementationFound, testInterfaceImplMultipleImplementations | P-1 新增维度 |
| 事件监听 | HIGH | testEventListenerDetection, testEventListenerNoListeners, testEventListenerConfidenceHigh | @EventListener |
| 条件装配 | MEDIUM | testConditionalBeanDetection, testConditionalProfileDetection, testConditionalBeanConfidenceMedium | @Conditional/@Profile |
| 反射调用 | MEDIUM | testReflectionGetBeanDetection, testReflectionClassForNameDetection, testReflectionNoReflectionCalls, testReflectionCallConfidenceMedium | getBean()/Class.forName() |
| 配置引用 | LOW | testConfigReferenceValueDetection, testConfigReferenceConfidenceLow | @Value("${xxx}") |

### 2.2 类设计 → 测试

| 需求中的类 | 对应测试 | 覆盖行为 |
|-----------|---------|---------|
| ImplicitDependencyDetector (接口) | testDetectorInterfaceDetect, testDetectorInterfaceDetectAll | detect / detectAll 契约 |
| CompositeImplicitDetector | testDetectorInterfaceDetectAll, testDetectionDisabledSkipsAll, testDimensionDisabledSkipsIt | 组合检测+维度开关 |
| DetectionDimension (枚举) | testDetectionDimensionEnumValues, testDetectionDimensionDefaultConfidence | 6 种维度 + 默认置信度 |
| DetectionContext | testDetectionContextConstruction, testDetectionContextMinimalConstruction, testDetectionContextNullSourceCode | 完整/最简/null 构造 |
| ImplicitDependency | testImplicitDependencyBasicFields, testImplicitDependencyWithResolvedTarget, testImplicitDependencyWithoutResolvedTarget, testResolutionTypes | 基础字段/resolvedTarget/resolution |
| DetectionResult | testDetectionResultConstruction, testDetectionResultEmptyDependencies | 构造/空结果 |
| SpringInjectionDetector | testSpringInjectionDetectAutowired, testSpringInjectionDetectResource, testSpringInjectionDetectInject, testSpringInjectionNoAnnotations, testSpringInjectionConfidenceHigh | 三种注解/无注解/置信度 |
| InterfaceImplDetector | testInterfaceImplResolution, testInterfaceImplNoImplementationFound, testInterfaceImplMultipleImplementations | 解析/未解析/多实现 |
| EventListenerDetector | testEventListenerDetection, testEventListenerNoListeners, testEventListenerConfidenceHigh | 检出/无监听/置信度 |
| ConditionalBeanDetector | testConditionalBeanDetection, testConditionalProfileDetection, testConditionalBeanConfidenceMedium | @Conditional/@Profile/置信度 |
| ReflectionCallDetector | testReflectionGetBeanDetection, testReflectionClassForNameDetection, testReflectionNoReflectionCalls, testReflectionCallConfidenceMedium | getBean/forName/无反射/置信度 |
| ConfigReferenceDetector | testConfigReferenceValueDetection, testConfigReferenceConfidenceLow | @Value/置信度 |
| DetectionConfig | testConfigDefaults, testConfigDisableSpecificDimension, testConfigExternalizable | 默认值/维度开关/外部化 |
| DetectionSummary | testDetectionSummaryGeneration | 统计 + formatReport |

### 2.3 CLI vs 插件端差异 → 测试

| 差异点 | 对应测试 | 说明 |
|--------|---------|------|
| CLI resolvedTarget=null | testCLISideUnresolvedTarget, testImplicitDependencyWithoutResolvedTarget | JavaParser 无法跨文件 |
| 插件端 resolvedTarget 有值 | testPluginSideResolvedTarget, testImplicitDependencyWithResolvedTarget | 图引擎查 implements_ifs |
| resolution: PSI vs IMPORT_MATCH vs UNRESOLVED | testResolutionTypes | 三级解析可信度 |
| 插件端全维度检测 | testInterfaceImplResolution | CLI 无法做接口→实现 |

### 2.4 集成方式 → 测试

| 集成点 | 对应测试 | 说明 |
|--------|---------|------|
| 检测结果 → struct 上下文注入 | testStructContextInjection | JSON 序列化含 resolvedTarget |
| 方式 A+ 插件端增强 | testPluginSideResolvedTarget | 注入点+实现类+方法列表 |
| 方式 A CLI 基础版 | testCLISideUnresolvedTarget | 仅注入声明 |

### 2.5 风险与约束 → 测试

| 风险/约束 | 对应测试 | 说明 |
|----------|---------|------|
| 检测默认关闭？否，默认启用 | testConfigDefaults | enabled=true |
| JavaParser 无法跨文件 resolve | testInterfaceImplNoImplementationFound | CLI UNRESOLVED |
| 不改现有 L1/L2 校验逻辑 | 独立 detectors 包，无 L1/L2 import | 物理隔离 |
| JDK 1.8 语法 | testJdk8Compatibility | 不用 List.of/Map.of/var |
| null 输入不崩溃 | testNullSourceCodeHandling, testDetectionContextNullSourceCode | 不抛 NPE |
| 非 Java 文件 | testNonJavaFileSkipped | 空结果 |
| 图引擎 annotations JSON 字符串 | testInterfaceImplResolution | graphData 模拟 |

---

## 三、完整测试用例清单

| # | 测试方法 | 所属维度 | 对应需求点 |
|---|---------|---------|-----------|
| 1 | testDetectorInterfaceDetect | 接口契约 | A1 ImplicitDependencyDetector 接口 |
| 2 | testDetectorInterfaceDetectAll | 接口契约 | A1 组合检测 |
| 3 | testDetectionDimensionEnumValues | 枚举 | 6 种维度 |
| 4 | testDetectionDimensionDefaultConfidence | 枚举 | 维度默认置信度 |
| 5 | testDetectionContextConstruction | 数据结构 | DetectionContext 完整构造 |
| 6 | testDetectionContextMinimalConstruction | 数据结构 | CLI 最简构造 |
| 7 | testDetectionContextNullSourceCode | 数据结构 | null 源码不崩溃 |
| 8 | testImplicitDependencyBasicFields | 数据结构 | ImplicitDependency 基础字段 |
| 9 | testImplicitDependencyWithResolvedTarget | 数据结构 | A5 插件端 resolvedTarget |
| 10 | testImplicitDependencyWithoutResolvedTarget | 数据结构 | A5 CLI 端 resolvedTarget=null |
| 11 | testDetectionResultConstruction | 数据结构 | DetectionResult 构造 |
| 12 | testDetectionResultEmptyDependencies | 数据结构 | 空结果 |
| 13 | testResolutionTypes | 数据结构 | PSI/IMPORT_MATCH/UNRESOLVED |
| 14 | testSpringInjectionDetectAutowired | Spring 注入 | A2 @Autowired |
| 15 | testSpringInjectionDetectResource | Spring 注入 | A2 @Resource |
| 16 | testSpringInjectionDetectInject | Spring 注入 | A2 @Inject |
| 17 | testSpringInjectionNoAnnotations | Spring 注入 | 无注解→空结果 |
| 18 | testSpringInjectionConfidenceHigh | Spring 注入 | 置信度 HIGH |
| 19 | testInterfaceImplResolution | 接口实现 | P-1 新增维度 |
| 20 | testInterfaceImplNoImplementationFound | 接口实现 | CLI 无法解析 |
| 21 | testInterfaceImplMultipleImplementations | 接口实现 | 多实现→MEDIUM |
| 22 | testEventListenerDetection | 事件监听 | A2 @EventListener |
| 23 | testEventListenerNoListeners | 事件监听 | 无监听→空结果 |
| 24 | testEventListenerConfidenceHigh | 事件监听 | 置信度 HIGH |
| 25 | testConditionalBeanDetection | 条件装配 | @Conditional |
| 26 | testConditionalProfileDetection | 条件装配 | @Profile |
| 27 | testConditionalBeanConfidenceMedium | 条件装配 | 置信度 MEDIUM |
| 28 | testReflectionGetBeanDetection | 反射调用 | A2 getBean() |
| 29 | testReflectionClassForNameDetection | 反射调用 | Class.forName() |
| 30 | testReflectionNoReflectionCalls | 反射调用 | 无反射→空结果 |
| 31 | testReflectionCallConfidenceMedium | 反射调用 | 置信度 MEDIUM |
| 32 | testConfigReferenceValueDetection | 配置引用 | @Value |
| 33 | testConfigReferenceConfidenceLow | 配置引用 | 置信度 LOW |
| 34 | testConfigDefaults | 配置 | 默认值 |
| 35 | testConfigDisableSpecificDimension | 配置 | 维度开关 |
| 36 | testConfigExternalizable | 配置 | 外部化 |
| 37 | testDetectionSummaryGeneration | 检测摘要 | 统计 + formatReport |
| 38 | testCLISideUnresolvedTarget | CLI/插件差异 | CLI resolvedTarget=null |
| 39 | testPluginSideResolvedTarget | CLI/插件差异 | 插件端 resolvedTarget 有值 |
| 40 | testStructContextInjection | 集成 | A6 struct 上下文 JSON |
| 41 | testDetectionDisabledSkipsAll | 边界条件 | 全局关闭 |
| 42 | testDimensionDisabledSkipsIt | 边界条件 | 维度关闭 |
| 43 | testEmptySourceCode | 边界条件 | 空源码 |
| 44 | testNullSourceCodeHandling | 边界条件 | null 不崩溃 |
| 45 | testNonJavaFileSkipped | 边界条件 | 非 Java 文件 |
| 46 | testNoSpringAnnotations | 边界条件 | 无 Spring 注解 |
| 47 | testJdk8Compatibility | 边界条件 | JDK 1.8 语法 |

---

## 四、待实现类清单（给 Claude Code）

测试引用的类尚未实现，需按以下顺序创建：

```
com.codelens.common.detectors.implicit.DetectionSource         — 枚举: CLI_JAVAPARSER / PLUGIN_PSI
com.codelens.common.detectors.implicit.DetectionDimension      — 枚举: 6 种维度 + getDefaultConfidence()
com.codelens.common.detectors.implicit.DetectionConfig         — 配置类（enabled + dimensions + fromMap 外部化）
com.codelens.common.detectors.implicit.DetectionContext        — 检测上下文（fileName + sourceCode + source + graphQueryResults）
com.codelens.common.detectors.implicit.ImplicitDependency      — 隐式依赖 POJO（target + resolvedTarget + type + confidence + resolution + evidence）
com.codelens.common.detectors.implicit.DetectionResult         — 检测结果（dimension + fileName + implicitDependencies + toJson()）
com.codelens.common.detectors.implicit.DetectionSummary        — 检测摘要（统计 + formatReport）
com.codelens.common.detectors.implicit.ImplicitDependencyDetector  — 接口（detect + detectAll）
com.codelens.common.detectors.implicit.CompositeImplicitDetector   — 组合检测器（遍历所有启用的维度）
com.codelens.common.detectors.implicit.SpringInjectionDetector     — Spring 注入检测实现
com.codelens.common.detectors.implicit.InterfaceImplDetector       — 接口→实现映射检测实现
com.codelens.common.detectors.implicit.EventListenerDetector       — 事件监听检测实现
com.codelens.common.detectors.implicit.ConditionalBeanDetector     — 条件装配检测实现
com.codelens.common.detectors.implicit.ReflectionCallDetector      — 反射调用检测实现
com.codelens.common.detectors.implicit.ConfigReferenceDetector     — 配置引用检测实现
```

### 关键实现要点

1. **DetectionDimension.getDefaultConfidence()**：返回需求文档中各维度的默认置信度（HIGH/HIGH/HIGH/MEDIUM/MEDIUM/LOW）
2. **ImplicitDependency.resolvedTarget**：CLI 端为 null，插件端通过图引擎查到实现类后有值
3. **ImplicitDependency.resolution**：PSI（插件端精确解析）/ IMPORT_MATCH（CLI 端 import 推断）/ UNRESOLVED（无法推断）
4. **DetectionContext.graphQueryResults**：仅插件端有值（Map<String, Object>），CLI 端为 null
5. **DetectionResult.toJson()**：序列化为 struct 上下文注入格式，需含 resolvedTarget 和 evidence
6. **CompositeImplicitDetector**：遍历 config.getDimensions()，每个维度创建对应检测器并调用 detect()
7. **JDK 1.8 兼容**：不用 List.of / Map.of / var / EnumSet.copyOf 等高于 JDK 1.8 的 API

### ConfidenceLevel 复用

C-7 已在 `com.codelens.common.validators.l3.ConfidenceLevel` 定义了 HIGH/MEDIUM/LOW 枚举。C-8 应直接复用，不重复定义。
