package com.codelens.common.normalizers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Disabled;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OutputNormalizer V3 分支测试
 * 覆盖 C-5: V2修dependencies截断, V3修calls截断
 *
 * 标注 @Disabled 的测试是 C-5 实现前的规范测试，实现完成后应去掉 @Disabled 并确认通过。
 *
 * @since 0.2.9
 */
class OutputNormalizerV3Test {

    // ==================== C-5: V3 格式归一化 ====================

    @Nested
    class V3BasicStructureTests {

        @Test
        void testNormalizeV3KeepsTopLevelFields() {
            String json = "{"
                + "\"summary\":\"支付处理服务\","
                + "\"framework\":\"Spring Boot + MyBatis\","
                + "\"fields\":[{\"name\":\"paymentMapper\",\"type\":\"PaymentMapper\",\"line\":15,\"injectType\":\"AUTOWIRED\",\"description\":\"支付数据访问\"}],"
                + "\"methods\":[{\"name\":\"processPayment\",\"line\":30,\"complexity\":\"MEDIUM\"}]"
                + "}";
            String result = OutputNormalizer.normalize(json);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();

            assertTrue(root.has("summary"), "V3 should preserve summary");
            assertTrue(root.has("framework"), "V3 should preserve framework");
            assertTrue(root.has("fields"), "V3 should preserve fields");
            assertTrue(root.has("methods"), "V3 should preserve methods");
        }

        @Test
        void testNormalizeV3RemovesArchitectureIssues() {
            String json = "{"
                + "\"summary\":\"test\","
                + "\"framework\":\"Spring\","
                + "\"fields\":[],"
                + "\"methods\":[],"
                + "\"architecture_issues\":[{\"issue\":\"test\"}]"
                + "}";
            String result = OutputNormalizer.normalize(json);
            assertFalse(result.contains("architecture_issues"), "architecture_issues should always be removed");
        }
    }

    @Nested
    class V3CallsTruncationTests {

        @Test
        void testV3CallsObjectArrayNormalization() {
            // C-5 验收: V3修calls截断
            String json = "{"
                + "\"summary\":\"test\","
                + "\"framework\":\"Spring\","
                + "\"fields\":[],"
                + "\"methods\":[{"
                + "  \"name\":\"processPayment\","
                + "  \"line\":30,"
                + "  \"calls\":[\"paymentMapper.selectById()\",\"orderService.createOrder()\"]"
                + "}]"
                + "}";
            String result = OutputNormalizer.normalize(json);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            JsonArray calls = root.getAsJsonArray("methods")
                .get(0).getAsJsonObject().getAsJsonArray("calls");

            assertTrue(calls.get(0).isJsonObject(), "calls should be normalized to objects");
            assertEquals("paymentMapper.selectById",
                calls.get(0).getAsJsonObject().get("method").getAsString());
        }

        @Test
        void testV3CallsTruncationRepair() {
            // 模拟截断场景: OutputNormalizer应gracefully处理
            String truncatedJson = "{"
                + "\"summary\":\"test\","
                + "\"framework\":\"Spring\","
                + "\"fields\":[],"
                + "\"methods\":[{"
                + "  \"name\":\"processPayment\","
                + "  \"line\":30,"
                + "  \"calls\":["
                + "    {\"target\":\"paymentMapper.selectById\",\"line\":35,\"type\":\"cross_file\"},"
                + "    {\"target\":\"orderService.createOr";
            String result = OutputNormalizer.normalize(truncatedJson);
            assertNotNull(result, "Should handle truncated JSON gracefully");
        }

        @Test
        void testV3CallsComplexObjectFormat() {
            // V3 calls 已是对象格式时应保持
            String json = "{"
                + "\"summary\":\"test\","
                + "\"framework\":\"Spring\","
                + "\"fields\":[],"
                + "\"methods\":[{"
                + "  \"name\":\"processPayment\","
                + "  \"line\":30,"
                + "  \"calls\":["
                + "    {\"target\":\"paymentMapper.selectById\",\"line\":35,\"type\":\"cross_file\"},"
                + "    {\"target\":\"validateAmount\",\"line\":40,\"type\":\"same_file\"}"
                + "  ]"
                + "}]"
                + "}";
            String result = OutputNormalizer.normalize(json);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            JsonArray calls = root.getAsJsonArray("methods")
                .get(0).getAsJsonObject().getAsJsonArray("calls");

            assertEquals(2, calls.size());
            assertTrue(calls.get(0).getAsJsonObject().has("target"));
            assertEquals("paymentMapper.selectById",
                calls.get(0).getAsJsonObject().get("target").getAsString());
            assertEquals(35, calls.get(0).getAsJsonObject().get("line").getAsInt());
            assertEquals("cross_file",
                calls.get(0).getAsJsonObject().get("type").getAsString());
        }
    }

    @Nested
    class V3FieldsNormalizationTests {

        @Test
        void testV3FieldsInjectTypeValidation() {
            // V3 fields[].injectType 枚举值保留
            String json = "{"
                + "\"summary\":\"test\","
                + "\"framework\":\"Spring\","
                + "\"fields\":["
                + "  {\"name\":\"paymentMapper\",\"type\":\"PaymentMapper\",\"line\":15,\"injectType\":\"AUTOWIRED\",\"description\":\"支付DAO\"},"
                + "  {\"name\":\"orderService\",\"type\":\"OrderService\",\"line\":18,\"injectType\":\"RESOURCE\",\"description\":\"订单服务\"}"
                + "],"
                + "\"methods\":[]"
                + "}";
            String result = OutputNormalizer.normalize(json);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            JsonArray fields = root.getAsJsonArray("fields");

            assertEquals(2, fields.size());
            assertEquals("AUTOWIRED",
                fields.get(0).getAsJsonObject().get("injectType").getAsString());
            assertEquals("RESOURCE",
                fields.get(1).getAsJsonObject().get("injectType").getAsString());
        }

        @Test
        void testV3FieldsToolClassFilter() {
            // V3 fields 也应过滤工具类（与 V2 dependencies 同理）
            String json = "{"
                + "\"summary\":\"test\","
                + "\"framework\":\"Spring\","
                + "\"fields\":["
                + "  {\"name\":\"paymentMapper\",\"type\":\"PaymentMapper\",\"line\":15,\"injectType\":\"AUTOWIRED\"},"
                + "  {\"name\":\"stringUtil\",\"type\":\"StringUtil\",\"line\":20,\"injectType\":\"STATIC\"},"
                + "  {\"name\":\"dateUtils\",\"type\":\"DateUtils\",\"line\":22,\"injectType\":\"STATIC\"}"
                + "],"
                + "\"methods\":[]"
                + "}";
            String result = OutputNormalizer.normalize(json);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            JsonArray fields = root.getAsJsonArray("fields");

            // 工具类字段应被过滤，只剩 paymentMapper
            assertEquals(1, fields.size(), "Tool class fields should be filtered");
            assertEquals("paymentMapper",
                fields.get(0).getAsJsonObject().get("name").getAsString());
        }
    }

    @Nested
    class V3MethodsRisksNormalizationTests {

        @Test
        void testV3MethodsRisksTypeNormalization() {
            // V3 methods[].risks 的 type 也应做枚举校验
            String json = "{"
                + "\"summary\":\"test\","
                + "\"framework\":\"Spring\","
                + "\"fields\":[],"
                + "\"methods\":[{"
                + "  \"name\":\"processPayment\","
                + "  \"line\":30,"
                + "  \"risks\":["
                + "    {\"type\":\"SECURITY\",\"description\":\"SQL注入风险\",\"line\":35,\"severity\":\"HIGH\"},"
                + "    {\"type\":\"performance\",\"description\":\"慢查询\",\"line\":40,\"severity\":\"MEDIUM\"}"
                + "  ]"
                + "}]"
                + "}";
            String result = OutputNormalizer.normalize(json);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            JsonArray risks = root.getAsJsonArray("methods")
                .get(0).getAsJsonObject().getAsJsonArray("risks");

            assertEquals("SECURITY", risks.get(0).getAsJsonObject().get("type").getAsString());
            // 小写 "performance" 应归一化
            String normalizedType = risks.get(1).getAsJsonObject().get("type").getAsString();
            assertTrue(
                "MAINTAINABILITY".equals(normalizedType) || "PERFORMANCE".equals(normalizedType),
                "Invalid risk type should be normalized, got: " + normalizedType
            );
        }
    }

    // ==================== REQ-C15: V3 顶层 risks 归一化 ====================

    @Nested
    class V3TopLevelRisksNormalizationTests {

        @Test
        void testV3TopLevelRisksTypeNormalization() {
            // V3 顶层 risks[].type 应做枚举校验
            String json = "{"
                + "\"summary\":\"test\","
                + "\"framework\":\"Spring\","
                + "\"fields\":[],"
                + "\"methods\":[],"
                + "\"risks\":["
                + "  {\"type\":\"SECURITY\",\"description\":\"SQL注入\",\"line\":35,\"severity\":\"HIGH\"},"
                + "  {\"type\":\"performance\",\"description\":\"慢查询\",\"line\":40,\"severity\":\"MEDIUM\"}"
                + "]}";
            String result = OutputNormalizer.normalize(json);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            JsonArray risks = root.getAsJsonArray("risks");

            assertEquals("SECURITY", risks.get(0).getAsJsonObject().get("type").getAsString());
            String normalizedType = risks.get(1).getAsJsonObject().get("type").getAsString();
            assertTrue(
                "MAINTAINABILITY".equals(normalizedType) || "PERFORMANCE".equals(normalizedType),
                "Invalid risk type should be normalized, got: " + normalizedType
            );
        }

        @Test
        void testV3TopLevelRisksConfidenceNormalization() {
            // V3 顶层 risks[].confidence 归一化
            String json = "{"
                + "\"summary\":\"test\","
                + "\"framework\":\"Spring\","
                + "\"fields\":[],"
                + "\"methods\":[],"
                + "\"risks\":["
                + "  {\"type\":\"SECURITY\",\"description\":\"r1\",\"line\":10,\"severity\":\"HIGH\",\"confidence\":\"CERTAIN\"},"
                + "  {\"type\":\"MAINTAINABILITY\",\"description\":\"r2\",\"line\":20,\"severity\":\"MEDIUM\",\"confidence\":\"POSSIBLE\"},"
                + "  {\"type\":\"PERFORMANCE\",\"description\":\"r3\",\"line\":30,\"severity\":\"LOW\",\"confidence\":\"INVALID\"}"
                + "]}";
            String result = OutputNormalizer.normalize(json);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            JsonArray risks = root.getAsJsonArray("risks");

            assertEquals("CERTAIN", risks.get(0).getAsJsonObject().get("confidence").getAsString());
            assertEquals("POSSIBLE", risks.get(1).getAsJsonObject().get("confidence").getAsString());
            assertEquals("POSSIBLE", risks.get(2).getAsJsonObject().get("confidence").getAsString(),
                "Invalid confidence should be downgraded to POSSIBLE");
        }

        @Test
        void testV3TopLevelRisksWithV2FormatUnchanged() {
            // V2 格式输出含 risks 时不做 confidence 归一化
            String json = "{"
                + "\"summary\":\"test\","
                + "\"risks\":["
                + "  {\"type\":\"SECURITY\",\"description\":\"r1\",\"line\":10,\"severity\":\"HIGH\"}"
                + "],"
                + "\"keyMethods\":[]"
                + "}";
            String result = OutputNormalizer.normalize(json);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            JsonArray risks = root.getAsJsonArray("risks");

            assertEquals("SECURITY", risks.get(0).getAsJsonObject().get("type").getAsString());
            assertFalse(risks.get(0).getAsJsonObject().has("confidence"),
                "V2 risks should not have confidence field added");
        }

        @Test
        void testV3TopLevelRisksNormalizeIdempotent() {
            String json = "{"
                + "\"summary\":\"test\","
                + "\"framework\":\"Spring\","
                + "\"fields\":[],"
                + "\"methods\":[],"
                + "\"risks\":["
                + "  {\"type\":\"SECURITY\",\"description\":\"r1\",\"line\":10,\"severity\":\"HIGH\",\"confidence\":\"CERTAIN\"},"
                + "  {\"type\":\"MAINTAINABILITY\",\"description\":\"r2\",\"line\":20,\"severity\":\"MEDIUM\",\"confidence\":\"POSSIBLE\"}"
                + "]}";
            String first = OutputNormalizer.normalize(json);
            String second = OutputNormalizer.normalize(first);
            assertEquals(first, second, "V3 top-level risks normalize should be idempotent");
        }
    }

    // ==================== C-5: V2 dependencies 截断修复 ====================

    @Nested
    class V2DependenciesTruncationTests {

        @Test
        void testV2DependenciesNotTruncated() {
            // C-5 验收: V2修dependencies截断 — 确保归一化后 dependencies 数组完整
            String json = "{"
                + "\"summary\":\"test\","
                + "\"dependencies\":["
                + "  {\"name\":\"svc1\",\"type\":\"field\",\"line\":\"10\",\"description\":\"服务1\"},"
                + "  {\"name\":\"svc2\",\"type\":\"field\",\"line\":\"20\",\"description\":\"服务2\"},"
                + "  {\"name\":\"svc3\",\"type\":\"field\",\"line\":\"30\",\"description\":\"服务3\"}"
                + "],"
                + "\"keyMethods\":[]"
                + "}";
            String result = OutputNormalizer.normalize(json);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            JsonArray deps = root.getAsJsonArray("dependencies");
            assertEquals(3, deps.size(), "All dependencies should be preserved");
        }

        @Test
        void testV2DependenciesWithMethodCallMigration() {
            // method_call 应从 dependencies 移除并迁移到 keyMethods.calls
            String json = "{"
                + "\"summary\":\"test\","
                + "\"dependencies\":["
                + "  {\"name\":\"svc1\",\"type\":\"field\",\"line\":\"10\"},"
                + "  {\"name\":\"svc2\",\"type\":\"method_call\",\"line\":\"25\"}"
                + "],"
                + "\"keyMethods\":[{\"name\":\"process\",\"line\":\"20\"}]"
                + "}";
            String result = OutputNormalizer.normalize(json);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            JsonArray deps = root.getAsJsonArray("dependencies");

            assertEquals(1, deps.size(), "method_call should be removed from dependencies");
            assertEquals("svc1", deps.get(0).getAsJsonObject().get("name").getAsString());
        }

        @Test
        void testV2EmptyDependenciesHandled() {
            String json = "{\"summary\":\"test\",\"dependencies\":[],\"keyMethods\":[]}";
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertEquals(0, root.getAsJsonArray("dependencies").size());
        }
    }

    // ==================== 版本感知归一化 ====================

    @Nested
    class VersionAwareNormalizationTests {

        @Test
        void testV2FormatNormalizedAsV2() {
            // 包含 dependencies/keyMethods 的 V2 格式应按 V2 逻辑处理
            String v2Json = "{"
                + "\"summary\":\"test\","
                + "\"dependencies\":[{\"name\":\"svc\",\"type\":\"injection\",\"line\":\"1\"}],"
                + "\"keyMethods\":[{\"name\":\"process\",\"line\":\"10\",\"calls\":[\"doX()\"]}]"
                + "}";
            String result = OutputNormalizer.normalize(v2Json);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();

            assertTrue(root.has("dependencies"));
            assertEquals("field",
                root.getAsJsonArray("dependencies").get(0).getAsJsonObject().get("type").getAsString());
            JsonObject km = root.getAsJsonArray("keyMethods").get(0).getAsJsonObject();
            assertTrue(km.getAsJsonArray("calls").get(0).isJsonObject());
        }

        @Test
        void testV3FormatNormalizedAsV3() {
            // 包含 fields/methods 的 V3 格式应按 V3 逻辑处理
            String v3Json = "{"
                + "\"summary\":\"test\","
                + "\"framework\":\"Spring\","
                + "\"fields\":[{\"name\":\"svc\",\"type\":\"Service\",\"line\":1,\"injectType\":\"AUTOWIRED\"}],"
                + "\"methods\":[{\"name\":\"process\",\"line\":10,\"calls\":[\"doX()\"]}]"
                + "}";
            String result = OutputNormalizer.normalize(v3Json);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();

            assertTrue(root.has("fields"));
            assertTrue(root.has("methods"));
            // V3 methods.calls 应归一化
            JsonObject method = root.getAsJsonArray("methods").get(0).getAsJsonObject();
            assertTrue(method.getAsJsonArray("calls").get(0).isJsonObject(),
                "V3 methods[].calls should be normalized to objects");
        }

        @Test
        void testMixedFormatNoError() {
            // 同时包含 V2 和 V3 字段不应崩溃
            String mixedJson = "{"
                + "\"summary\":\"test\","
                + "\"dependencies\":[{\"name\":\"svc\",\"type\":\"field\",\"line\":\"1\"}],"
                + "\"fields\":[{\"name\":\"svc\",\"type\":\"Service\",\"line\":1}],"
                + "\"keyMethods\":[{\"name\":\"process\",\"line\":\"10\"}],"
                + "\"methods\":[{\"name\":\"process\",\"line\":10}]"
                + "}";
            String result = OutputNormalizer.normalize(mixedJson);
            assertNotNull(result, "Should handle mixed format without error");
        }
    }

    // ==================== 幂等性 ====================

    @Nested
    class IdempotencyTests {

        @Test
        void testV2NormalizeIdempotent() {
            String json = "{\"summary\":\"test\",\"dependencies\":[{\"name\":\"svc\",\"type\":\"field\",\"line\":\"1\",\"description\":\"d\"}],\"keyMethods\":[{\"name\":\"m\",\"line\":\"10\",\"calls\":[{\"method\":\"doX\",\"line\":15,\"type\":\"cross_file\"}]}]}";
            String first = OutputNormalizer.normalize(json);
            String second = OutputNormalizer.normalize(first);
            assertEquals(first, second, "V2 normalize should be idempotent");
        }

        @Test
        void testV3NormalizeIdempotent() {
            String json = "{\"summary\":\"test\",\"framework\":\"Spring\",\"fields\":[{\"name\":\"svc\",\"type\":\"Service\",\"line\":1,\"injectType\":\"AUTOWIRED\"}],\"methods\":[{\"name\":\"m\",\"line\":10,\"calls\":[{\"target\":\"doX\",\"line\":15,\"type\":\"cross_file\"}]}]}";
            String first = OutputNormalizer.normalize(json);
            String second = OutputNormalizer.normalize(first);
            assertEquals(first, second, "V3 normalize should be idempotent");
        }
    }
}
