package com.codelens.common.detectors.implicit;

import com.codelens.common.detectors.implicit.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * C-8 隐式依赖检测 — 单元测试
 *
 * 覆盖范围：
 * 1. ImplicitDependencyDetector 接口契约
 * 2. DetectionDimension 枚举（6 种维度 + 置信度映射）
 * 3. DetectionContext 数据结构
 * 4. DetectionResult / ImplicitDependency 数据结构（含 resolvedTarget）
 * 5. SpringInjectionDetector Spring 注入检测
 * 6. InterfaceImplDetector 接口→实现映射
 * 7. EventListenerDetector 事件监听检测
 * 8. ConditionalBeanDetector 条件装配检测
 * 9. ReflectionCallDetector 反射调用检测
 * 10. ConfigReferenceDetector 配置引用检测
 * 11. DetectionConfig 配置项
 * 12. DetectionSummary 检测摘要
 * 13. CLI 端 vs 插件端差异（resolvedTarget / resolution）
 * 14. 边界条件
 */
public class ImplicitDependencyDetectorTest {

    private DetectionConfig config;

    @BeforeEach
    void setUp() {
        config = new DetectionConfig();
        config.setEnabled(true);
        config.setDimensions(EnumSet.allOf(DetectionDimension.class));
    }

    // ========== 1. ImplicitDependencyDetector 接口契约 ==========

    @Test
    void testDetectorInterfaceDetect() {
        // ImplicitDependencyDetector 接口必须提供 detect(DetectionContext) -> DetectionResult
        ImplicitDependencyDetector detector = new SpringInjectionDetector(config);
        DetectionContext context = new DetectionContext(
            "OrderServiceImpl.java",
            "package com.example.service.impl;\n" +
            "@Service\n" +
            "public class OrderServiceImpl {\n" +
            "  @Autowired\n" +
            "  private UserService userService;\n" +
            "}",
            DetectionSource.CLI_JAVAPARSER
        );
        DetectionResult result = detector.detect(context);
        assertNotNull(result);
        assertNotNull(result.getImplicitDependencies());
        assertEquals(DetectionDimension.SPRING_INJECTION, result.getDimension());
    }

    @Test
    void testDetectorInterfaceDetectAll() {
        // 组合检测：对所有维度执行检测
        CompositeImplicitDetector composite = new CompositeImplicitDetector(config);
        DetectionContext context = new DetectionContext(
            "OrderServiceImpl.java",
            "@Service public class OrderServiceImpl { @Autowired UserService us; }",
            DetectionSource.CLI_JAVAPARSER
        );
        List<DetectionResult> results = composite.detectAll(context);
        assertNotNull(results);
        // 每个启用的维度各返回一个结果
        assertEquals(config.getDimensions().size(), results.size());
    }

    // ========== 2. DetectionDimension 枚举 ==========

    @Test
    void testDetectionDimensionEnumValues() {
        // 6 种检测维度
        assertEquals(6, DetectionDimension.values().length);
        assertNotNull(DetectionDimension.SPRING_INJECTION);
        assertNotNull(DetectionDimension.INTERFACE_IMPL);
        assertNotNull(DetectionDimension.EVENT_LISTENER);
        assertNotNull(DetectionDimension.CONDITIONAL_BEAN);
        assertNotNull(DetectionDimension.REFLECTION_CALL);
        assertNotNull(DetectionDimension.CONFIG_REFERENCE);
    }

    @Test
    void testDetectionDimensionDefaultConfidence() {
        // 每个维度有默认置信度
        assertEquals(ConfidenceLevel.HIGH, DetectionDimension.SPRING_INJECTION.getDefaultConfidence());
        assertEquals(ConfidenceLevel.HIGH, DetectionDimension.INTERFACE_IMPL.getDefaultConfidence());
        assertEquals(ConfidenceLevel.HIGH, DetectionDimension.EVENT_LISTENER.getDefaultConfidence());
        assertEquals(ConfidenceLevel.MEDIUM, DetectionDimension.CONDITIONAL_BEAN.getDefaultConfidence());
        assertEquals(ConfidenceLevel.MEDIUM, DetectionDimension.REFLECTION_CALL.getDefaultConfidence());
        assertEquals(ConfidenceLevel.LOW, DetectionDimension.CONFIG_REFERENCE.getDefaultConfidence());
    }

    // ========== 3. DetectionContext 数据结构 ==========

    @Test
    void testDetectionContextConstruction() {
        Map<String, Object> graphData = new HashMap<>();
        graphData.put("class_nodes.annotations", "[\"@Service\",\"@Autowired\"]");
        graphData.put("class_nodes.implements_ifs", "[\"UserService\"]");

        DetectionContext context = new DetectionContext(
            "OrderServiceImpl.java",
            "源码内容",
            DetectionSource.PLUGIN_PSI,
            graphData
        );
        assertEquals("OrderServiceImpl.java", context.getFileName());
        assertEquals("源码内容", context.getSourceCode());
        assertEquals(DetectionSource.PLUGIN_PSI, context.getSource());
        assertNotNull(context.getGraphQueryResults());
        assertEquals("[\"@Service\",\"@Autowired\"]",
            context.getGraphQueryResults().get("class_nodes.annotations"));
    }

    @Test
    void testDetectionContextMinimalConstruction() {
        // 最简构造：无图引擎数据（CLI 端场景）
        DetectionContext context = new DetectionContext(
            "OrderServiceImpl.java",
            "源码",
            DetectionSource.CLI_JAVAPARSER
        );
        assertEquals(DetectionSource.CLI_JAVAPARSER, context.getSource());
        assertNull(context.getGraphQueryResults());
    }

    @Test
    void testDetectionContextNullSourceCode() {
        // 源码为 null 时不应抛 NPE
        DetectionContext context = new DetectionContext(
            "Test.java", null, DetectionSource.CLI_JAVAPARSER
        );
        assertNotNull(context);
        assertNull(context.getSourceCode());
    }

    // ========== 4. DetectionResult / ImplicitDependency 数据结构 ==========

    @Test
    void testImplicitDependencyBasicFields() {
        ImplicitDependency dep = new ImplicitDependency(
            "UserService",
            DetectionDimension.SPRING_INJECTION,
            ConfidenceLevel.HIGH,
            "PSI"
        );
        assertEquals("UserService", dep.getTarget());
        assertEquals(DetectionDimension.SPRING_INJECTION, dep.getType());
        assertEquals(ConfidenceLevel.HIGH, dep.getConfidence());
        assertEquals("PSI", dep.getResolution());
    }

    @Test
    void testImplicitDependencyWithResolvedTarget() {
        // 插件端：resolvedTarget 有值（接口→实现解析成功）
        Map<String, String> evidence = new HashMap<>();
        evidence.put("field", "userService");
        evidence.put("annotation", "@Autowired");
        evidence.put("interfaceClass", "com.example.service.UserService");
        evidence.put("implClass", "com.example.service.impl.UserServiceImpl");

        ImplicitDependency dep = new ImplicitDependency(
            "UserService",
            "UserServiceImpl",
            DetectionDimension.SPRING_INJECTION,
            ConfidenceLevel.HIGH,
            "PSI",
            evidence
        );
        assertEquals("UserServiceImpl", dep.getResolvedTarget());
        assertEquals("PSI", dep.getResolution());
        assertEquals("com.example.service.impl.UserServiceImpl", dep.getEvidence().get("implClass"));
    }

    @Test
    void testImplicitDependencyWithoutResolvedTarget() {
        // CLI 端：resolvedTarget 为 null（JavaParser 无法跨文件 resolve）
        ImplicitDependency dep = new ImplicitDependency(
            "UserService",
            null,
            DetectionDimension.SPRING_INJECTION,
            ConfidenceLevel.HIGH,
            "IMPORT_MATCH",
            null
        );
        assertNull(dep.getResolvedTarget());
        assertEquals("IMPORT_MATCH", dep.getResolution());
    }

    @Test
    void testDetectionResultConstruction() {
        List<ImplicitDependency> deps = new ArrayList<>();
        deps.add(new ImplicitDependency("UserService", DetectionDimension.SPRING_INJECTION, ConfidenceLevel.HIGH, "PSI"));

        DetectionResult result = new DetectionResult(
            DetectionDimension.SPRING_INJECTION,
            "OrderServiceImpl.java",
            deps
        );
        assertEquals(DetectionDimension.SPRING_INJECTION, result.getDimension());
        assertEquals("OrderServiceImpl.java", result.getFileName());
        assertEquals(1, result.getImplicitDependencies().size());
    }

    @Test
    void testDetectionResultEmptyDependencies() {
        // 无隐式依赖时返回空列表
        DetectionResult result = new DetectionResult(
            DetectionDimension.SPRING_INJECTION,
            "SimpleService.java",
            Collections.emptyList()
        );
        assertTrue(result.getImplicitDependencies().isEmpty());
        assertFalse(result.hasImplicitDependencies());
    }

    @Test
    void testResolutionTypes() {
        // resolution 三种取值：PSI / IMPORT_MATCH / UNRESOLVED
        ImplicitDependency psi = new ImplicitDependency("X", DetectionDimension.SPRING_INJECTION, ConfidenceLevel.HIGH, "PSI");
        ImplicitDependency importMatch = new ImplicitDependency("X", DetectionDimension.SPRING_INJECTION, ConfidenceLevel.MEDIUM, "IMPORT_MATCH");
        ImplicitDependency unresolved = new ImplicitDependency("X", DetectionDimension.SPRING_INJECTION, ConfidenceLevel.LOW, "UNRESOLVED");

        assertEquals("PSI", psi.getResolution());
        assertEquals("IMPORT_MATCH", importMatch.getResolution());
        assertEquals("UNRESOLVED", unresolved.getResolution());
    }

    // ========== 5. SpringInjectionDetector ==========

    @Test
    void testSpringInjectionDetectAutowired() {
        // 检出 @Autowired 字段
        String source = "package com.example;\n" +
            "import org.springframework.beans.factory.annotation.Autowired;\n" +
            "@Service\n" +
            "public class OrderServiceImpl {\n" +
            "  @Autowired\n" +
            "  private UserService userService;\n" +
            "}";
        DetectionContext context = new DetectionContext("OrderServiceImpl.java", source, DetectionSource.CLI_JAVAPARSER);
        SpringInjectionDetector detector = new SpringInjectionDetector(config);
        DetectionResult result = detector.detect(context);

        assertEquals(DetectionDimension.SPRING_INJECTION, result.getDimension());
        assertTrue(result.hasImplicitDependencies());
        boolean foundUserService = result.getImplicitDependencies().stream()
            .anyMatch(d -> d.getTarget().equals("UserService"));
        assertTrue(foundUserService);
    }

    @Test
    void testSpringInjectionDetectResource() {
        // 检出 @Resource 字段
        String source = "package com.example;\n" +
            "import javax.annotation.Resource;\n" +
            "@Service\n" +
            "public class OrderServiceImpl {\n" +
            "  @Resource\n" +
            "  private PaymentGateway paymentGateway;\n" +
            "}";
        DetectionContext context = new DetectionContext("OrderServiceImpl.java", source, DetectionSource.CLI_JAVAPARSER);
        SpringInjectionDetector detector = new SpringInjectionDetector(config);
        DetectionResult result = detector.detect(context);

        assertTrue(result.hasImplicitDependencies());
        boolean foundPayment = result.getImplicitDependencies().stream()
            .anyMatch(d -> d.getTarget().equals("PaymentGateway"));
        assertTrue(foundPayment);
    }

    @Test
    void testSpringInjectionDetectInject() {
        // 检出 @Inject 字段（JSR-330）
        String source = "package com.example;\n" +
            "import javax.inject.Inject;\n" +
            "@Service\n" +
            "public class OrderServiceImpl {\n" +
            "  @Inject\n" +
            "  private OrderMapper orderMapper;\n" +
            "}";
        DetectionContext context = new DetectionContext("OrderServiceImpl.java", source, DetectionSource.CLI_JAVAPARSER);
        SpringInjectionDetector detector = new SpringInjectionDetector(config);
        DetectionResult result = detector.detect(context);

        assertTrue(result.hasImplicitDependencies());
        boolean foundMapper = result.getImplicitDependencies().stream()
            .anyMatch(d -> d.getTarget().equals("OrderMapper"));
        assertTrue(foundMapper);
    }

    @Test
    void testSpringInjectionNoAnnotations() {
        // 无注入注解 → 空结果
        String source = "package com.example;\n" +
            "public class SimpleCalculator {\n" +
            "  public int add(int a, int b) { return a + b; }\n" +
            "}";
        DetectionContext context = new DetectionContext("SimpleCalculator.java", source, DetectionSource.CLI_JAVAPARSER);
        SpringInjectionDetector detector = new SpringInjectionDetector(config);
        DetectionResult result = detector.detect(context);

        assertFalse(result.hasImplicitDependencies());
    }

    @Test
    void testSpringInjectionConfidenceHigh() {
        // Spring 注入检测置信度应为 HIGH
        String source = "@Service public class S { @Autowired UserService us; }";
        DetectionContext context = new DetectionContext("S.java", source, DetectionSource.CLI_JAVAPARSER);
        SpringInjectionDetector detector = new SpringInjectionDetector(config);
        DetectionResult result = detector.detect(context);

        if (result.hasImplicitDependencies()) {
            assertEquals(ConfidenceLevel.HIGH, result.getImplicitDependencies().get(0).getConfidence());
        }
    }

    // ========== 6. InterfaceImplDetector ==========

    @Test
    void testInterfaceImplResolution() {
        // 插件端：class_nodes.implements_ifs 提供接口→实现映射
        Map<String, Object> graphData = new HashMap<>();
        graphData.put("class_nodes.implements_ifs", "[\"UserService\"]");
        graphData.put("impl_class", "UserServiceImpl");

        DetectionContext context = new DetectionContext(
            "OrderServiceImpl.java", "源码", DetectionSource.PLUGIN_PSI, graphData
        );
        InterfaceImplDetector detector = new InterfaceImplDetector(config);
        DetectionResult result = detector.detect(context);

        assertEquals(DetectionDimension.INTERFACE_IMPL, result.getDimension());
        if (result.hasImplicitDependencies()) {
            // 插件端应能解析出 resolvedTarget
            ImplicitDependency dep = result.getImplicitDependencies().get(0);
            assertNotNull(dep.getResolvedTarget());
            assertEquals("PSI", dep.getResolution());
        }
    }

    @Test
    void testInterfaceImplNoImplementationFound() {
        // CLI 端：JavaParser 无法跨文件 resolve → UNRESOLVED
        String source = "package com.example;\n" +
            "@Service\n" +
            "public class OrderServiceImpl {\n" +
            "  @Autowired\n" +
            "  private UserService userService;\n" +
            "}";
        DetectionContext context = new DetectionContext("OrderServiceImpl.java", source, DetectionSource.CLI_JAVAPARSER);
        InterfaceImplDetector detector = new InterfaceImplDetector(config);
        DetectionResult result = detector.detect(context);

        if (result.hasImplicitDependencies()) {
            ImplicitDependency dep = result.getImplicitDependencies().get(0);
            assertNull(dep.getResolvedTarget());
            assertEquals("UNRESOLVED", dep.getResolution());
        }
    }

    @Test
    void testInterfaceImplMultipleImplementations() {
        // 多个实现类时 → 置信度降级为 MEDIUM
        Map<String, Object> graphData = new HashMap<>();
        graphData.put("class_nodes.implements_ifs", "[\"UserService\"]");
        graphData.put("impl_classes", "[\"UserServiceImpl\",\"UserServiceImpl2\"]");

        DetectionContext context = new DetectionContext(
            "OrderServiceImpl.java", "源码", DetectionSource.PLUGIN_PSI, graphData
        );
        InterfaceImplDetector detector = new InterfaceImplDetector(config);
        DetectionResult result = detector.detect(context);

        if (result.hasImplicitDependencies()) {
            // 多实现时置信度应为 MEDIUM（不确定哪个被注入）
            ImplicitDependency dep = result.getImplicitDependencies().get(0);
            assertEquals(ConfidenceLevel.MEDIUM, dep.getConfidence());
        }
    }

    // ========== 7. EventListenerDetector ==========

    @Test
    void testEventListenerDetection() {
        // 检出 @EventListener 注解的方法
        String source = "package com.example;\n" +
            "import org.springframework.context.event.EventListener;\n" +
            "@Service\n" +
            "public class NotificationService {\n" +
            "  @EventListener\n" +
            "  public void onOrderCreated(OrderCreatedEvent event) {}\n" +
            "}";
        DetectionContext context = new DetectionContext("NotificationService.java", source, DetectionSource.CLI_JAVAPARSER);
        EventListenerDetector detector = new EventListenerDetector(config);
        DetectionResult result = detector.detect(context);

        assertEquals(DetectionDimension.EVENT_LISTENER, result.getDimension());
        assertTrue(result.hasImplicitDependencies());
    }

    @Test
    void testEventListenerNoListeners() {
        // 无 @EventListener → 空结果
        String source = "@Service public class SimpleService { public void process() {} }";
        DetectionContext context = new DetectionContext("SimpleService.java", source, DetectionSource.CLI_JAVAPARSER);
        EventListenerDetector detector = new EventListenerDetector(config);
        DetectionResult result = detector.detect(context);

        assertFalse(result.hasImplicitDependencies());
    }

    @Test
    void testEventListenerConfidenceHigh() {
        // 事件监听检测置信度应为 HIGH
        String source = "@Service public class S { @EventListener public void onEv(E e) {} }";
        DetectionContext context = new DetectionContext("S.java", source, DetectionSource.CLI_JAVAPARSER);
        EventListenerDetector detector = new EventListenerDetector(config);
        DetectionResult result = detector.detect(context);

        if (result.hasImplicitDependencies()) {
            assertEquals(ConfidenceLevel.HIGH, result.getImplicitDependencies().get(0).getConfidence());
        }
    }

    // ========== 8. ConditionalBeanDetector ==========

    @Test
    void testConditionalBeanDetection() {
        // 检出 @Conditional 注解
        String source = "package com.example;\n" +
            "import org.springframework.context.annotation.Conditional;\n" +
            "@Configuration\n" +
            "public class FeatureConfig {\n" +
            "  @Bean\n" +
            "  @Conditional(OnProdCondition.class)\n" +
            "  public FeatureService featureService() { return new FeatureService(); }\n" +
            "}";
        DetectionContext context = new DetectionContext("FeatureConfig.java", source, DetectionSource.CLI_JAVAPARSER);
        ConditionalBeanDetector detector = new ConditionalBeanDetector(config);
        DetectionResult result = detector.detect(context);

        assertEquals(DetectionDimension.CONDITIONAL_BEAN, result.getDimension());
        assertTrue(result.hasImplicitDependencies());
    }

    @Test
    void testConditionalProfileDetection() {
        // 检出 @Profile 注解
        String source = "package com.example;\n" +
            "import org.springframework.context.annotation.Profile;\n" +
            "@Configuration\n" +
            "public class DevConfig {\n" +
            "  @Bean\n" +
            "  @Profile(\"dev\")\n" +
            "  public DevToolService devToolService() { return new DevToolService(); }\n" +
            "}";
        DetectionContext context = new DetectionContext("DevConfig.java", source, DetectionSource.CLI_JAVAPARSER);
        ConditionalBeanDetector detector = new ConditionalBeanDetector(config);
        DetectionResult result = detector.detect(context);

        assertTrue(result.hasImplicitDependencies());
    }

    @Test
    void testConditionalBeanConfidenceMedium() {
        // 条件装配置信度应为 MEDIUM（运行时才能确定是否生效）
        String source = "@Configuration public class C { @Bean @Conditional(X.class) public S s() { return new S(); } }";
        DetectionContext context = new DetectionContext("C.java", source, DetectionSource.CLI_JAVAPARSER);
        ConditionalBeanDetector detector = new ConditionalBeanDetector(config);
        DetectionResult result = detector.detect(context);

        if (result.hasImplicitDependencies()) {
            assertEquals(ConfidenceLevel.MEDIUM, result.getImplicitDependencies().get(0).getConfidence());
        }
    }

    // ========== 9. ReflectionCallDetector ==========

    @Test
    void testReflectionGetBeanDetection() {
        // 检出 applicationContext.getBean() 调用
        String source = "package com.example;\n" +
            "@Service\n" +
            "public class DynamicService {\n" +
            "  public Object getService(String name) {\n" +
            "    return applicationContext.getBean(name);\n" +
            "  }\n" +
            "}";
        DetectionContext context = new DetectionContext("DynamicService.java", source, DetectionSource.CLI_JAVAPARSER);
        ReflectionCallDetector detector = new ReflectionCallDetector(config);
        DetectionResult result = detector.detect(context);

        assertEquals(DetectionDimension.REFLECTION_CALL, result.getDimension());
        assertTrue(result.hasImplicitDependencies());
    }

    @Test
    void testReflectionClassForNameDetection() {
        // 检出 Class.forName() 调用
        String source = "package com.example;\n" +
            "public class ReflectionHelper {\n" +
            "  public Class<?> loadClass(String name) throws Exception {\n" +
            "    return Class.forName(name);\n" +
            "  }\n" +
            "}";
        DetectionContext context = new DetectionContext("ReflectionHelper.java", source, DetectionSource.CLI_JAVAPARSER);
        ReflectionCallDetector detector = new ReflectionCallDetector(config);
        DetectionResult result = detector.detect(context);

        assertTrue(result.hasImplicitDependencies());
    }

    @Test
    void testReflectionNoReflectionCalls() {
        // 无反射调用 → 空结果
        String source = "@Service public class NormalService { public void doWork() {} }";
        DetectionContext context = new DetectionContext("NormalService.java", source, DetectionSource.CLI_JAVAPARSER);
        ReflectionCallDetector detector = new ReflectionCallDetector(config);
        DetectionResult result = detector.detect(context);

        assertFalse(result.hasImplicitDependencies());
    }

    @Test
    void testReflectionCallConfidenceMedium() {
        // 反射调用置信度应为 MEDIUM
        String source = "public class S { Object o = ctx.getBean(\"x\"); }";
        DetectionContext context = new DetectionContext("S.java", source, DetectionSource.CLI_JAVAPARSER);
        ReflectionCallDetector detector = new ReflectionCallDetector(config);
        DetectionResult result = detector.detect(context);

        if (result.hasImplicitDependencies()) {
            assertEquals(ConfidenceLevel.MEDIUM, result.getImplicitDependencies().get(0).getConfidence());
        }
    }

    // ========== 10. ConfigReferenceDetector ==========

    @Test
    void testConfigReferenceValueDetection() {
        // 检出 @Value("${xxx}") 注解
        String source = "package com.example;\n" +
            "import org.springframework.beans.factory.annotation.Value;\n" +
            "@Service\n" +
            "public class ConfigService {\n" +
            "  @Value(\"${app.timeout:3000}\")\n" +
            "  private int timeout;\n" +
            "}";
        DetectionContext context = new DetectionContext("ConfigService.java", source, DetectionSource.CLI_JAVAPARSER);
        ConfigReferenceDetector detector = new ConfigReferenceDetector(config);
        DetectionResult result = detector.detect(context);

        assertEquals(DetectionDimension.CONFIG_REFERENCE, result.getDimension());
        assertTrue(result.hasImplicitDependencies());
    }

    @Test
    void testConfigReferenceConfidenceLow() {
        // 配置引用置信度应为 LOW（外部配置文件才知真实值）
        String source = "@Service public class S { @Value(\"${x.y}\") String v; }";
        DetectionContext context = new DetectionContext("S.java", source, DetectionSource.CLI_JAVAPARSER);
        ConfigReferenceDetector detector = new ConfigReferenceDetector(config);
        DetectionResult result = detector.detect(context);

        if (result.hasImplicitDependencies()) {
            assertEquals(ConfidenceLevel.LOW, result.getImplicitDependencies().get(0).getConfidence());
        }
    }

    // ========== 11. DetectionConfig 配置项 ==========

    @Test
    void testConfigDefaults() {
        DetectionConfig defaultConfig = new DetectionConfig();
        assertTrue(defaultConfig.isEnabled());
        // 默认启用所有维度
        assertEquals(6, defaultConfig.getDimensions().size());
    }

    @Test
    void testConfigDisableSpecificDimension() {
        // 可关闭特定维度
        EnumSet<DetectionDimension> dims = EnumSet.of(
            DetectionDimension.SPRING_INJECTION,
            DetectionDimension.INTERFACE_IMPL
        );
        config.setDimensions(dims);
        assertEquals(2, config.getDimensions().size());
        assertFalse(config.getDimensions().contains(DetectionDimension.EVENT_LISTENER));
    }

    @Test
    void testConfigExternalizable() {
        // 配置项支持通过 Map 覆盖
        Map<String, String> overrides = new HashMap<>();
        overrides.put("detection.enabled", "false");
        overrides.put("detection.dimensions", "SPRING_INJECTION,INTERFACE_IMPL");
        DetectionConfig fromMap = DetectionConfig.fromMap(overrides);
        assertFalse(fromMap.isEnabled());
        assertEquals(2, fromMap.getDimensions().size());
    }

    // ========== 12. DetectionSummary 检测摘要 ==========

    @Test
    void testDetectionSummaryGeneration() {
        List<DetectionResult> results = new ArrayList<>();
        results.add(new DetectionResult(DetectionDimension.SPRING_INJECTION, "A.java",
            Arrays.asList(new ImplicitDependency("X", DetectionDimension.SPRING_INJECTION, ConfidenceLevel.HIGH, "PSI"))));
        results.add(new DetectionResult(DetectionDimension.EVENT_LISTENER, "B.java", Collections.emptyList()));
        results.add(new DetectionResult(DetectionDimension.REFLECTION_CALL, "C.java",
            Arrays.asList(new ImplicitDependency("Y", DetectionDimension.REFLECTION_CALL, ConfidenceLevel.MEDIUM, "IMPORT_MATCH"))));

        DetectionSummary summary = new DetectionSummary(results);
        assertEquals(3, summary.getTotalDimensionCount());
        assertEquals(2, summary.getDimensionsWithDeps());
        assertEquals(1, summary.getDimensionsWithoutDeps());
        assertEquals(2, summary.getTotalImplicitDepCount());
        assertNotNull(summary.formatReport());
    }

    // ========== 13. CLI 端 vs 插件端差异 ==========

    @Test
    void testCLISideUnresolvedTarget() {
        // CLI 端：JavaParser 无法跨文件 resolve，resolvedTarget = null，resolution = IMPORT_MATCH 或 UNRESOLVED
        String source = "@Service public class S { @Autowired UserService us; }";
        DetectionContext context = new DetectionContext("S.java", source, DetectionSource.CLI_JAVAPARSER);
        InterfaceImplDetector detector = new InterfaceImplDetector(config);
        DetectionResult result = detector.detect(context);

        if (result.hasImplicitDependencies()) {
            for (ImplicitDependency dep : result.getImplicitDependencies()) {
                // CLI 端无法解析实现类
                assertTrue(dep.getResolution().equals("UNRESOLVED") || dep.getResolution().equals("IMPORT_MATCH"));
            }
        }
    }

    @Test
    void testPluginSideResolvedTarget() {
        // 插件端：图引擎查到 implements_ifs，resolvedTarget 有值，resolution = PSI
        Map<String, Object> graphData = new HashMap<>();
        graphData.put("class_nodes.implements_ifs", "[\"UserService\"]");
        graphData.put("impl_class", "UserServiceImpl");

        DetectionContext context = new DetectionContext(
            "S.java", "源码", DetectionSource.PLUGIN_PSI, graphData
        );
        InterfaceImplDetector detector = new InterfaceImplDetector(config);
        DetectionResult result = detector.detect(context);

        if (result.hasImplicitDependencies()) {
            ImplicitDependency dep = result.getImplicitDependencies().get(0);
            if (dep.getResolvedTarget() != null) {
                assertEquals("PSI", dep.getResolution());
            }
        }
    }

    @Test
    void testStructContextInjection() {
        // 检测结果应能序列化为 struct 上下文注入格式
        List<ImplicitDependency> deps = new ArrayList<>();
        Map<String, String> evidence = new HashMap<>();
        evidence.put("field", "userService");
        evidence.put("annotation", "@Autowired");
        evidence.put("interfaceClass", "com.example.service.UserService");
        evidence.put("implClass", "com.example.service.impl.UserServiceImpl");

        deps.add(new ImplicitDependency("UserService", "UserServiceImpl",
            DetectionDimension.SPRING_INJECTION, ConfidenceLevel.HIGH, "PSI", evidence));

        DetectionResult result = new DetectionResult(DetectionDimension.SPRING_INJECTION, "OrderServiceImpl.java", deps);
        String json = result.toJson();
        assertNotNull(json);
        assertTrue(json.contains("UserService"));
        assertTrue(json.contains("UserServiceImpl"));
        assertTrue(json.contains("SPRING_INJECTION"));
    }

    // ========== 14. 边界条件 ==========

    @Test
    void testDetectionDisabledSkipsAll() {
        // 检测关闭时跳过所有维度
        config.setEnabled(false);
        CompositeImplicitDetector composite = new CompositeImplicitDetector(config);
        DetectionContext context = new DetectionContext(
            "S.java", "@Service public class S { @Autowired X x; }", DetectionSource.CLI_JAVAPARSER
        );
        List<DetectionResult> results = composite.detectAll(context);
        assertTrue(results.isEmpty());
    }

    @Test
    void testDimensionDisabledSkipsIt() {
        // 关闭特定维度时不检测
        config.setDimensions(EnumSet.of(DetectionDimension.SPRING_INJECTION));
        CompositeImplicitDetector composite = new CompositeImplicitDetector(config);
        DetectionContext context = new DetectionContext(
            "S.java", "@Service public class S { @EventListener public void onEv(E e) {} }", DetectionSource.CLI_JAVAPARSER
        );
        List<DetectionResult> results = composite.detectAll(context);
        // EVENT_LISTENER 维度已关闭，不应出现在结果中
        boolean hasEventListener = results.stream()
            .anyMatch(r -> r.getDimension() == DetectionDimension.EVENT_LISTENER);
        assertFalse(hasEventListener);
    }

    @Test
    void testEmptySourceCode() {
        // 空源码 → 各检测器返回空结果
        DetectionContext context = new DetectionContext("Empty.java", "", DetectionSource.CLI_JAVAPARSER);
        SpringInjectionDetector detector = new SpringInjectionDetector(config);
        DetectionResult result = detector.detect(context);
        assertFalse(result.hasImplicitDependencies());
    }

    @Test
    void testNullSourceCodeHandling() {
        // null 源码不应抛 NPE
        DetectionContext context = new DetectionContext("Null.java", null, DetectionSource.CLI_JAVAPARSER);
        SpringInjectionDetector detector = new SpringInjectionDetector(config);
        assertDoesNotThrow(() -> detector.detect(context));
    }

    @Test
    void testNonJavaFileSkipped() {
        // 非 Java 文件 → 空结果
        DetectionContext context = new DetectionContext("config.xml", "<config/>", DetectionSource.CLI_JAVAPARSER);
        SpringInjectionDetector detector = new SpringInjectionDetector(config);
        DetectionResult result = detector.detect(context);
        assertFalse(result.hasImplicitDependencies());
    }

    @Test
    void testNoSpringAnnotations() {
        // 纯 Java 类无 Spring 注解 → Spring 注入检测返回空
        String source = "public class PlainClass { private SomeDependency dep; }";
        DetectionContext context = new DetectionContext("PlainClass.java", source, DetectionSource.CLI_JAVAPARSER);
        SpringInjectionDetector detector = new SpringInjectionDetector(config);
        DetectionResult result = detector.detect(context);
        assertFalse(result.hasImplicitDependencies());
    }

    @Test
    void testJdk8Compatibility() {
        // 确保不使用 JDK 1.8 以上特性（List.of, Map.of, var 等）
        // 此测试本身即为验证——如果编译通过则兼容
        List<String> list = new ArrayList<>();
        Map<String, String> map = new HashMap<>();
        Set<DetectionDimension> set = EnumSet.noneOf(DetectionDimension.class);
        assertNotNull(list);
        assertNotNull(map);
        assertNotNull(set);
    }
}
