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

    // ==================== REQ-C16: JSON 修复 ====================

    @Nested
    class JsonRepairTests {

        // ------ 问题模式1: 控制字符 ------

        @Test
        void testFixControlCharsBareNewlineInString() {
            // description 值内包含裸换行符
            String json = "{\"summary\":\"test\",\"description\":\"line1\nline2\",\"methods\":[]}";
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertTrue(root.has("summary"));
            assertEquals("test", root.get("summary").getAsString());
        }

        @Test
        void testFixControlCharsBareTabInString() {
            // description 值内包含裸制表符
            String json = "{\"summary\":\"test\",\"description\":\"col1\tcol2\",\"methods\":[]}";
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertTrue(root.has("description"));
        }

        @Test
        void testFixControlCharsEmptyString() {
            assertNull(OutputNormalizer.fixControlChars(null));
            assertEquals("", OutputNormalizer.fixControlChars(""));
        }

        // ------ 问题模式2: 未转义引号 ------

        @Test
        void testFixUnescapedQuotesInSuggestion() {
            // suggestion 中包含中文引号场景：BaseResponse.fail("未登录")
            String json = "{"
                + "\"summary\":\"test\","
                + "\"suggestion\":\"返回BaseResponse.fail(\\\"未登录\\\")\","
                + "\"methods\":[]"
                + "}";
            // 注意：Java 字符串中 \" 是合法转义，对 JSON 无影响
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertTrue(root.has("suggestion"));
        }

        @Test
        void testFixUnescapedQuotesBareQuotesInValue() {
            // 字符串值内直接含有未转义引号
            String json = "{\"msg\":\"他说\"好的\"就走\",\"methods\":[]}";
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertTrue(root.has("msg"));
        }

        @Test
        void testFixUnescapedQuotesMultipleBareQuotes() {
            // 多个未转义引号
            String json = "{\"text\":\"a\"b\"c\"d\",\"methods\":[]}";
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertTrue(root.has("text"));
        }

        // ------ 问题模式3: 截断修复 ------

        @Test
        void testFixTruncationTruncatedArray() {
            // 方法数组在中间截断
            String json = "{"
                + "\"summary\":\"test\","
                + "\"methods\":["
                + "  {\"name\":\"method1\",\"line\":10},"
                + "  {\"name\":\"method2\",\"line\":20}";
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            // 修复后应可解析
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertTrue(root.has("summary"));
            // 至少保留第一个方法
            assertTrue(root.has("methods"));
        }

        @Test
        void testFixTruncationTruncatedObject() {
            // 顶层对象在中间截断（无完整闭合）
            String json = "{\"summary\":\"test\",\"framework\":\"Spring\",\"methods\":[]";
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertEquals("test", root.get("summary").getAsString());
        }

        @Test
        void testFixTruncationNestedTruncation() {
            // 嵌套结构截断
            String json = "{"
                + "\"summary\":\"test\","
                + "\"methods\":[{"
                + "  \"name\":\"m1\","
                + "  \"calls\":[{\"method\":\"a\",\"line\":1},{\"method\":\"b\"";
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            // 修复后可解析
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertTrue(root.has("summary"));
        }

        // ------ 问题模式4: 组合修复 ------

        @Test
        void testFixCombinedControlCharsAndUnescapedQuotes() {
            // 同时含控制字符和未转义引号
            String json = "{\"msg\":\"他说\"好的\"\n就走\",\"methods\":[]}";
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertTrue(root.has("msg"));
        }

        @Test
        void testFixCombinedAllThreeIssues() {
            // 同时含控制字符、未转义引号、截断
            String json = "{"
                + "\"summary\":\"test\nsummary\","
                + "\"desc\":\"他说\"好的\"\","
                + "\"methods\":[{\"name\":\"m1\",\"line\":1}]";
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            // 截断修复后至少可解析
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertTrue(root.has("summary"));
        }

        // ------ 正常 JSON 空操作验证 ------

        @Test
        void testValidJsonUnchanged() {
            String json = "{"
                + "\"summary\":\"test\","
                + "\"framework\":\"Spring\","
                + "\"fields\":[],"
                + "\"methods\":[]"
                + "}";
            String first = OutputNormalizer.normalize(json);
            String second = OutputNormalizer.normalize(first);
            assertEquals(first, second, "Valid JSON normalize should be idempotent");
        }

        @Test
        void testNormalJsonWithEscapedQuotesUnchanged() {
            // 已正确转义的 JSON 不应被破坏
            String json = "{"
                + "\"summary\":\"BaseResponse.fail(\\\"未登录\\\")\","
                + "\"methods\":[]"
                + "}";
            // 注意这里用 Java 字符串转义：\\\\\" 在 Java 中表示 \\" , 在 JSON 中表示 \"
            // 正确的做法：在 JSON 字符串值中用 \" 表示字面引号
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertTrue(root.has("summary"));
        }

        // ------ 验收测试：模拟 Round4 C3/C6/C8 实际截断场景 ------

        @Test
        void testAcceptanceC3StyleTruncation() {
            // C3: 含未转义引号(BaseResponse.fail("未登录")) + 尾部截断
            // 用 char 拼接来模拟 JSON 中的裸引号，避免 Java 编译器将其视为字符串边界
            char Q = '"';
            String json = "{"
                + "\"summary\":\"维度控制器\","
                + "\"frameworks\":\"Spring\","
                + "\"risks\":[{"
                + "  \"type\":\"SECURITY\",\"description\":\"NPE风险\",\"line\":67,\"severity\":\"HIGH\","
                + "  \"suggestion\":\"需判空，如BaseResponse.fail(" + Q + "未登录" + Q + ")\","
                + "  \"confidence\":\"CERTAIN\""
                + "}],"
                + "\"fields\":[{"
                + "  \"name\":\"sysDimenTypeManager\",\"type\":\"ISysDimenTypeManager\",\"line\":36,\"injectType\":\"AUTOWIRED\""
                + "}],"
                + "\"methods\":[{"
                + "  \"name\":\"querySysDimenType\",\"line\":67,\"complexity\":\"LOW\","
                + "  \"calls\":[{\"target\":\"sysDimenTypeManager.query()\",\"line\":68,\"type\":\"cross_file\"}],"
                + "  \"risks\":[{\"type\":\"MAINTAINABILITY\",\"description\":\"JSON解析未捕获异常\","
                + "    \"line\":82,\"severity\":\"LOW\",\"suggestion\":\"try-catch\",\"confidence\":\"CERTAIN\"}]"
                + "}";
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            // 修复后应可解析
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertTrue(root.has("summary"));
            assertTrue(root.has("methods"));
            // methods 至少保留一个方法
            assertTrue(root.getAsJsonArray("methods").size() >= 1);
        }

        @Test
        void testAcceptanceC6StyleUnescapedQuotes() {
            // C6: cardData.replace("AUTO", assetNum) 中引号破坏 JSON 结构
            String json = "{"
                + "\"summary\":\"AMS单据处理\","
                + "\"methods\":[{"
                + "  \"name\":\"processCardData\",\"line\":50,"
                + "  \"description\":\"cardData.replace(\\\"AUTO\\\", assetNum)格式修正\","
                + "  \"calls\":[],\"risks\":[]"
                + "}]"
                + "}";
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertTrue(root.has("methods"));
        }

        @Test
        void testAcceptanceC8StyleControlChars() {
            // C8: description 值内含换行符
            String json = "{\"summary\":\"采购审批\",\"description\":\"line1\nline2\nline3\",\"methods\":[]}";
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertTrue(root.has("summary"));
        }

        @Test
        void testAcceptanceC1StyleTruncationLarge() {
            // C1: 大文件 JSON 中间截断
            String json = "{"
                + "\"summary\":\"单据处理器\","
                + "\"methods\":["
                + "  {\"name\":\"saveBill\",\"line\":68,\"complexity\":\"HIGH\",\"calls\":[],\"risks\":[]},"
                + "  {\"name\":\"mergeData\",\"line\":233,\"complexity\":\"HIGH\",\"calls\":[],\"risks\":[]},"
                + "  {\"name\":\"updateBill\",\"line\":434,\"complexity\":\"MEDIUM\",\"calls\":[]";
            // 注意：methods 数组和根对象都没闭合
            String result = OutputNormalizer.normalize(json);
            assertNotNull(result);
            // 修复后可解析
            JsonObject root = JsonParser.parseString(result).getAsJsonObject();
            assertTrue(root.has("summary"));
            // 应保留前两个完整方法
            assertTrue(root.getAsJsonArray("methods").size() >= 2);
        }
    }
}
