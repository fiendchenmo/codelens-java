package com.codelens.common.analyzer;

import com.codelens.common.models.ArchitectureLayer;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ArchitectureLayerDetector} 单元测试
 */
class ArchitectureLayerDetectorTest {

    // ========================================================================
    // detectByAnnotation — 注解检测（9 个用例）
    // ========================================================================

    @Test
    void detectByAnnotation_restController() {
        assertEquals(ArchitectureLayer.CONTROLLER,
                ArchitectureLayerDetector.detectByAnnotation(
                        Arrays.asList("RestController"), "TestController"));
    }

    @Test
    void detectByAnnotation_controller() {
        assertEquals(ArchitectureLayer.CONTROLLER,
                ArchitectureLayerDetector.detectByAnnotation(
                        Arrays.asList("Controller"), "TestController"));
    }

    @Test
    void detectByAnnotation_service() {
        assertEquals(ArchitectureLayer.SERVICE,
                ArchitectureLayerDetector.detectByAnnotation(
                        Arrays.asList("Service"), "UserService"));
    }

    @Test
    void detectByAnnotation_repository() {
        assertEquals(ArchitectureLayer.REPOSITORY,
                ArchitectureLayerDetector.detectByAnnotation(
                        Arrays.asList("Repository"), "UserRepository"));
    }

    @Test
    void detectByAnnotation_configuration() {
        assertEquals(ArchitectureLayer.CONFIG,
                ArchitectureLayerDetector.detectByAnnotation(
                        Arrays.asList("Configuration"), "AppConfig"));
    }

    @Test
    void detectByAnnotation_configurationProperties() {
        assertEquals(ArchitectureLayer.CONFIG,
                ArchitectureLayerDetector.detectByAnnotation(
                        Arrays.asList("ConfigurationProperties"), "AppProperties"));
    }

    @Test
    void detectByAnnotation_componentWithHandlerKeyword() {
        assertEquals(ArchitectureLayer.HANDLER,
                ArchitectureLayerDetector.detectByAnnotation(
                        Arrays.asList("Component"), "OrderHandler"));
    }

    @Test
    void detectByAnnotation_multiAnnotation_respectsPriority() {
        // 多注解时按优先级匹配：@Controller 优先于 @Service
        assertEquals(ArchitectureLayer.CONTROLLER,
                ArchitectureLayerDetector.detectByAnnotation(
                        Arrays.asList("Service", "Controller"), "TestController"));
    }

    @Test
    void detectByAnnotation_noMatch() {
        assertNull(ArchitectureLayerDetector.detectByAnnotation(
                Arrays.asList("Component"), "PlainClass"));
    }

    // ========================================================================
    // detectByClassName — 类名后缀检测（18 个用例）
    // ========================================================================

    @Test
    void detectByClassName_controller() {
        assertEquals(ArchitectureLayer.CONTROLLER,
                ArchitectureLayerDetector.detectByClassName("UserController"));
    }

    @Test
    void detectByClassName_service() {
        assertEquals(ArchitectureLayer.SERVICE,
                ArchitectureLayerDetector.detectByClassName("UserService"));
    }

    @Test
    void detectByClassName_serviceImpl() {
        assertEquals(ArchitectureLayer.SERVICE,
                ArchitectureLayerDetector.detectByClassName("UserServiceImpl"));
    }

    @Test
    void detectByClassName_repository() {
        assertEquals(ArchitectureLayer.REPOSITORY,
                ArchitectureLayerDetector.detectByClassName("UserRepository"));
    }

    @Test
    void detectByClassName_dao() {
        assertEquals(ArchitectureLayer.REPOSITORY,
                ArchitectureLayerDetector.detectByClassName("UserDao"));
    }

    @Test
    void detectByClassName_mapper() {
        assertEquals(ArchitectureLayer.REPOSITORY,
                ArchitectureLayerDetector.detectByClassName("UserMapper"));
    }

    @Test
    void detectByClassName_handler() {
        assertEquals(ArchitectureLayer.HANDLER,
                ArchitectureLayerDetector.detectByClassName("OrderHandler"));
    }

    @Test
    void detectByClassName_listener() {
        assertEquals(ArchitectureLayer.HANDLER,
                ArchitectureLayerDetector.detectByClassName("MessageListener"));
    }

    @Test
    void detectByClassName_consumer() {
        assertEquals(ArchitectureLayer.HANDLER,
                ArchitectureLayerDetector.detectByClassName("EventConsumer"));
    }

    @Test
    void detectByClassName_config() {
        assertEquals(ArchitectureLayer.CONFIG,
                ArchitectureLayerDetector.detectByClassName("AppConfig"));
    }

    @Test
    void detectByClassName_configuration() {
        assertEquals(ArchitectureLayer.CONFIG,
                ArchitectureLayerDetector.detectByClassName("DataSourceConfiguration"));
    }

    @Test
    void detectByClassName_feignClient() {
        assertEquals(ArchitectureLayer.CLIENT,
                ArchitectureLayerDetector.detectByClassName("OrderFeignClient"));
    }

    @Test
    void detectByClassName_client() {
        assertEquals(ArchitectureLayer.CLIENT,
                ArchitectureLayerDetector.detectByClassName("PaymentClient"));
    }

    @Test
    void detectByClassName_dto() {
        assertEquals(ArchitectureLayer.MODEL,
                ArchitectureLayerDetector.detectByClassName("UserDTO"));
    }

    @Test
    void detectByClassName_entity() {
        assertEquals(ArchitectureLayer.MODEL,
                ArchitectureLayerDetector.detectByClassName("OrderEntity"));
    }

    @Test
    void detectByClassName_utils() {
        assertEquals(ArchitectureLayer.UTIL,
                ArchitectureLayerDetector.detectByClassName("StringUtils"));
    }

    @Test
    void detectByClassName_helper() {
        assertEquals(ArchitectureLayer.UTIL,
                ArchitectureLayerDetector.detectByClassName("FileHelper"));
    }

    @Test
    void detectByClassName_noMatch() {
        assertNull(ArchitectureLayerDetector.detectByClassName("MyRandomClass"));
    }

    // ========================================================================
    // detectByPackageName — 包名检测（15 个用例）
    // ========================================================================

    @Test
    void detectByPackageName_controller() {
        assertEquals(ArchitectureLayer.CONTROLLER,
                ArchitectureLayerDetector.detectByPackageName("com.example.controller"));
    }

    @Test
    void detectByPackageName_api() {
        assertEquals(ArchitectureLayer.CONTROLLER,
                ArchitectureLayerDetector.detectByPackageName("com.example.api"));
    }

    @Test
    void detectByPackageName_web() {
        assertEquals(ArchitectureLayer.CONTROLLER,
                ArchitectureLayerDetector.detectByPackageName("com.example.web"));
    }

    @Test
    void detectByPackageName_service() {
        assertEquals(ArchitectureLayer.SERVICE,
                ArchitectureLayerDetector.detectByPackageName("com.example.service"));
    }

    @Test
    void detectByPackageName_biz() {
        assertEquals(ArchitectureLayer.SERVICE,
                ArchitectureLayerDetector.detectByPackageName("com.example.biz"));
    }

    @Test
    void detectByPackageName_repository() {
        assertEquals(ArchitectureLayer.REPOSITORY,
                ArchitectureLayerDetector.detectByPackageName("com.example.repository"));
    }

    @Test
    void detectByPackageName_dao() {
        assertEquals(ArchitectureLayer.REPOSITORY,
                ArchitectureLayerDetector.detectByPackageName("com.example.dao"));
    }

    @Test
    void detectByPackageName_mapper() {
        assertEquals(ArchitectureLayer.REPOSITORY,
                ArchitectureLayerDetector.detectByPackageName("com.example.mapper"));
    }

    @Test
    void detectByPackageName_handler() {
        assertEquals(ArchitectureLayer.HANDLER,
                ArchitectureLayerDetector.detectByPackageName("com.example.handler"));
    }

    @Test
    void detectByPackageName_listener() {
        assertEquals(ArchitectureLayer.HANDLER,
                ArchitectureLayerDetector.detectByPackageName("com.example.listener"));
    }

    @Test
    void detectByPackageName_config() {
        assertEquals(ArchitectureLayer.CONFIG,
                ArchitectureLayerDetector.detectByPackageName("com.example.config"));
    }

    @Test
    void detectByPackageName_client() {
        assertEquals(ArchitectureLayer.CLIENT,
                ArchitectureLayerDetector.detectByPackageName("com.example.client"));
    }

    @Test
    void detectByPackageName_feign() {
        assertEquals(ArchitectureLayer.CLIENT,
                ArchitectureLayerDetector.detectByPackageName("com.example.feign"));
    }

    @Test
    void detectByPackageName_model() {
        assertEquals(ArchitectureLayer.MODEL,
                ArchitectureLayerDetector.detectByPackageName("com.example.model.entity"));
    }

    @Test
    void detectByPackageName_noMatch() {
        assertNull(ArchitectureLayerDetector.detectByPackageName("com.example.common"));
    }

    // ========================================================================
    // detectClassLayer — 优先级组合（4 个用例）
    // ========================================================================

    @Test
    void detectClassLayer_annotationBeatsClassName() {
        // @Service 注解 → SERVICE，即使类名是 *Controller
        assertEquals(ArchitectureLayer.SERVICE,
                ArchitectureLayerDetector.detectClassLayer(
                        Arrays.asList("Service"),
                        "OrderController",
                        "com.example.controller"));
    }

    @Test
    void detectClassLayer_classNameBeatsPackageName() {
        // 无注解，类名 *Service → SERVICE，即使包名是 .controller
        assertEquals(ArchitectureLayer.SERVICE,
                ArchitectureLayerDetector.detectClassLayer(
                        null,
                        "OrderService",
                        "com.example.controller"));
    }

    @Test
    void detectClassLayer_packageNameFallback() {
        // 无注解、类名无匹配，包名 .dao → REPOSITORY
        assertEquals(ArchitectureLayer.REPOSITORY,
                ArchitectureLayerDetector.detectClassLayer(
                        null,
                        "OrderRandomStuff",
                        "com.example.dao"));
    }

    @Test
    void detectClassLayer_allUnknown() {
        // 全部无匹配 → UNKNOWN
        assertEquals(ArchitectureLayer.UNKNOWN,
                ArchitectureLayerDetector.detectClassLayer(
                        null,
                        "RandomClass",
                        "com.example.unknown"));
    }

    // ========================================================================
    // detectPackageLayer — 包级众数（5 个用例）
    // ========================================================================

    @Test
    void detectPackageLayer_purePackage() {
        // 全 SERVICE → SERVICE
        Map<ArchitectureLayer, Integer> dist = new HashMap<>();
        dist.put(ArchitectureLayer.SERVICE, 10);
        assertEquals(ArchitectureLayer.SERVICE,
                ArchitectureLayerDetector.detectPackageLayer(dist));
    }

    @Test
    void detectPackageLayer_mixedDominant() {
        // SERVICE 占比 60% > 50% → SERVICE
        Map<ArchitectureLayer, Integer> dist = new HashMap<>();
        dist.put(ArchitectureLayer.SERVICE, 6);
        dist.put(ArchitectureLayer.CONTROLLER, 4);
        assertEquals(ArchitectureLayer.SERVICE,
                ArchitectureLayerDetector.detectPackageLayer(dist));
    }

    @Test
    void detectPackageLayer_mixedNoDominant() {
        // 最大占比 40% < 50% → UNKNOWN
        Map<ArchitectureLayer, Integer> dist = new HashMap<>();
        dist.put(ArchitectureLayer.SERVICE, 4);
        dist.put(ArchitectureLayer.HANDLER, 4);
        dist.put(ArchitectureLayer.MODEL, 2);
        assertEquals(ArchitectureLayer.UNKNOWN,
                ArchitectureLayerDetector.detectPackageLayer(dist));
    }

    @Test
    void detectPackageLayer_singleClass() {
        Map<ArchitectureLayer, Integer> dist = new HashMap<>();
        dist.put(ArchitectureLayer.UTIL, 1);
        assertEquals(ArchitectureLayer.UTIL,
                ArchitectureLayerDetector.detectPackageLayer(dist));
    }

    @Test
    void detectPackageLayer_empty() {
        assertEquals(ArchitectureLayer.UNKNOWN,
                ArchitectureLayerDetector.detectPackageLayer(new HashMap<>()));
    }

    // ========================================================================
    // getLayerComposition（3 个用例）
    // ========================================================================

    @Test
    void getLayerComposition_purePackage() {
        // 众数占比 >= 50% → null
        Map<ArchitectureLayer, Integer> dist = new HashMap<>();
        dist.put(ArchitectureLayer.SERVICE, 6);
        dist.put(ArchitectureLayer.CONTROLLER, 4);
        assertNull(ArchitectureLayerDetector.getLayerComposition(dist));
    }

    @Test
    void getLayerComposition_mixedPackage() {
        // 众数占比 < 50% → 格式化字符串
        Map<ArchitectureLayer, Integer> dist = new LinkedHashMap<>();
        dist.put(ArchitectureLayer.SERVICE, 3);
        dist.put(ArchitectureLayer.HANDLER, 3);
        dist.put(ArchitectureLayer.MODEL, 2);
        String result = ArchitectureLayerDetector.getLayerComposition(dist);
        assertNotNull(result);
        assertTrue(result.contains("37.5% SERVICE"));
        assertTrue(result.contains("37.5% HANDLER"));
        assertTrue(result.contains("25.0% MODEL"));
    }

    @Test
    void getLayerComposition_empty() {
        assertNull(ArchitectureLayerDetector.getLayerComposition(new HashMap<>()));
    }
}
