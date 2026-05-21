package com.codelens.common.normalizers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class OutputNormalizerTest {

    @Test
    void testNormalizeDepNameNoChange() {
        assertEquals("queryRefBillService", OutputNormalizer.normalizeDepName("queryRefBillService"));
        assertEquals("userMapper", OutputNormalizer.normalizeDepName("userMapper"));
        assertEquals("a", OutputNormalizer.normalizeDepName("a"));
    }

    @Test
    void testNormalizeDepNameFullQualified() {
        assertEquals("ecsBillMainService",
            OutputNormalizer.normalizeDepName("com.stream.ecs.bill.service.IEcsBillMainService"));
    }

    @Test
    void testNormalizeDepNameInterfaceOnly() {
        assertEquals("ecsBillMainService", OutputNormalizer.normalizeDepName("IEcsBillMainService"));
    }

    @Test
    void testNormalizeDepNameMethodCall() {
        assertEquals("billInfoCmd",
            OutputNormalizer.normalizeDepName("com.stream.bill.api.IBillInfoCmd.queryBillInfoById()"));
    }

    @Test
    void testNormalizeDepNameSimpleMethodCall() {
        assertEquals("billInfoCmd", OutputNormalizer.normalizeDepName("IBillInfoCmd.queryBillInfoById()"));
    }

    @Test
    void testNormalizeDepNameNonInterface() {
        assertEquals("stringUtil", OutputNormalizer.normalizeDepName("StringUtil"));
        assertEquals("ecsBillAssetsBXHandler", OutputNormalizer.normalizeDepName("EcsBillAssetsBXHandler"));
    }

    @Test
    void testIsToolClassDep() {
        assertTrue(OutputNormalizer.isToolClassDep("StringUtil"));
        assertTrue(OutputNormalizer.isToolClassDep("BeanUtil"));
        assertTrue(OutputNormalizer.isToolClassDep("DateUtils"));
        assertTrue(OutputNormalizer.isToolClassDep("JSONUtil"));
        assertTrue(OutputNormalizer.isToolClassDep("CollectionUtils"));
        assertTrue(OutputNormalizer.isToolClassDep("com.stream.core.util.StringUtil"));
        assertTrue(OutputNormalizer.isToolClassDep("org.apache.commons.lang3.StringUtils"));
        assertTrue(OutputNormalizer.isToolClassDep("IBaseService"));
        assertTrue(OutputNormalizer.isToolClassDep("BaseMapper"));
    }

    @Test
    void testIsNotToolClassDep() {
        assertFalse(OutputNormalizer.isToolClassDep("userMapper"));
        assertFalse(OutputNormalizer.isToolClassDep("queryRefBillService"));
        assertFalse(OutputNormalizer.isToolClassDep("SysUser"));
        assertFalse(OutputNormalizer.isToolClassDep("String")); // JDK 标准类不在此列
    }

    @Test
    void testNormalizeDepNamesIntegration() {
        JsonArray deps = new JsonArray();

        JsonObject dep1 = new JsonObject();
        dep1.addProperty("name", "com.stream.ecs.bill.service.IEcsBillMainService");
        dep1.addProperty("line", "10");
        deps.add(dep1);

        JsonObject dep2 = new JsonObject();
        dep2.addProperty("name", "queryRefBillService");
        dep2.addProperty("line", "20");
        deps.add(dep2);

        JsonObject dep3 = new JsonObject();
        dep3.addProperty("name", "com.stream.bill.api.IBillInfoCmd.queryBillInfoById()");
        dep3.addProperty("line", "30");
        deps.add(dep3);

        OutputNormalizer.normalizeDepNames(deps);

        assertEquals("ecsBillMainService", deps.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("queryRefBillService", deps.get(1).getAsJsonObject().get("name").getAsString());
        assertEquals("billInfoCmd", deps.get(2).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void testFilterToolClassDeps() {
        JsonArray deps = new JsonArray();

        JsonObject dep1 = new JsonObject();
        dep1.addProperty("name", "userMapper");
        dep1.addProperty("line", "10");
        deps.add(dep1);

        JsonObject dep2 = new JsonObject();
        dep2.addProperty("name", "StringUtil");
        dep2.addProperty("line", "20");
        deps.add(dep2);

        JsonObject dep3 = new JsonObject();
        dep3.addProperty("name", "queryRefBillService");
        dep3.addProperty("line", "30");
        deps.add(dep3);

        OutputNormalizer.filterToolClassDeps(deps);

        assertEquals(2, deps.size());
        assertEquals("userMapper", deps.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("queryRefBillService", deps.get(1).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void testNormalizeDepTypeValid() {
        assertEquals("field", OutputNormalizer.normalizeDepType("field"));
        assertEquals("method_call", OutputNormalizer.normalizeDepType("method_call"));
    }

    @Test
    void testNormalizeDepTypeInvalid() {
        assertEquals("field", OutputNormalizer.normalizeDepType("injection"));
        assertEquals("field", OutputNormalizer.normalizeDepType("dependency"));
        assertEquals("field", OutputNormalizer.normalizeDepType("service"));
        assertEquals("field", OutputNormalizer.normalizeDepType("autowired"));
        assertEquals("field", OutputNormalizer.normalizeDepType("cross_file"));
        assertEquals("field", OutputNormalizer.normalizeDepType("internal"));
        assertEquals("field", OutputNormalizer.normalizeDepType("external"));
        assertEquals("field", OutputNormalizer.normalizeDepType("same_file"));
        assertEquals("field", OutputNormalizer.normalizeDepType(null));
    }

    @Test
    void testNormalizeDepTypes() {
        JsonArray deps = new JsonArray();

        JsonObject dep1 = new JsonObject();
        dep1.addProperty("name", "userMapper");
        dep1.addProperty("type", "field");
        deps.add(dep1);

        JsonObject dep2 = new JsonObject();
        dep2.addProperty("name", "queryRefBillService");
        dep2.addProperty("type", "injection");
        deps.add(dep2);

        JsonObject dep3 = new JsonObject();
        dep3.addProperty("name", "otherService");
        // no type field
        deps.add(dep3);

        OutputNormalizer.normalizeDepTypes(deps);

        assertEquals("field", deps.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("field", deps.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("field", deps.get(2).getAsJsonObject().get("type").getAsString());
    }

    @Test
    void testNormalizeCallsStringArray() {
        JsonArray calls = new JsonArray();
        calls.add("selectById()");
        calls.add("mergeBillMain()");

        OutputNormalizer.normalizeCalls(calls);

        assertEquals(2, calls.size());
        assertTrue(calls.get(0).isJsonObject());
        assertEquals("selectById", calls.get(0).getAsJsonObject().get("method").getAsString());
        assertEquals(-1, calls.get(0).getAsJsonObject().get("line").getAsInt());
        assertEquals("unknown", calls.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("mergeBillMain", calls.get(1).getAsJsonObject().get("method").getAsString());
    }

    @Test
    void testNormalizeCallsObjectArray() {
        JsonArray calls = new JsonArray();

        JsonObject call1 = new JsonObject();
        call1.addProperty("method", "selectById()");
        call1.addProperty("line", 100);
        calls.add(call1);

        JsonObject call2 = new JsonObject();
        call2.addProperty("method", "mergeBillMain");
        // no line or type
        calls.add(call2);

        OutputNormalizer.normalizeCalls(calls);

        assertEquals("selectById", calls.get(0).getAsJsonObject().get("method").getAsString());
        assertEquals(100, calls.get(0).getAsJsonObject().get("line").getAsInt());
        assertEquals("unknown", calls.get(0).getAsJsonObject().get("type").getAsString());

        assertEquals("mergeBillMain", calls.get(1).getAsJsonObject().get("method").getAsString());
        assertEquals(-1, calls.get(1).getAsJsonObject().get("line").getAsInt());
        assertEquals("unknown", calls.get(1).getAsJsonObject().get("type").getAsString());
    }

    @Test
    void testNormalizeAllCalls() {
        JsonArray keyMethods = new JsonArray();

        JsonObject method1 = new JsonObject();
        method1.addProperty("name", "processPayment");
        method1.addProperty("line", "10");
        JsonArray calls1 = new JsonArray();
        calls1.add("findById()");
        calls1.add("save()");
        method1.add("calls", calls1);
        keyMethods.add(method1);

        JsonObject method2 = new JsonObject();
        method2.addProperty("name", "validateOrder");
        method2.addProperty("line", "50");
        // no calls
        keyMethods.add(method2);

        OutputNormalizer.normalizeAllCalls(keyMethods);

        JsonArray resultCalls = keyMethods.get(0).getAsJsonObject().getAsJsonArray("calls");
        assertEquals(2, resultCalls.size());
        assertEquals("findById", resultCalls.get(0).getAsJsonObject().get("method").getAsString());
        assertEquals("save", resultCalls.get(1).getAsJsonObject().get("method").getAsString());
    }

    @Test
    void testNormalizeRemovesArchitectureIssues() {
        String json = "{\"summary\":\"test\",\"dependencies\":[{\"name\":\"svc\",\"type\":\"field\",\"line\":\"1\",\"description\":\"test\"}],\"architecture_issues\":[{\"issue\":\"test\"}]}";
        String result = OutputNormalizer.normalize(json);
        assertFalse(result.contains("architecture_issues"), "architecture_issues should be removed");
    }

    @Test
    void testNormalizeNoDepsStillNormalizes() {
        // Even without dependencies, keyMethods calls should be normalized and arch issues removed
        String json = "{\"summary\":\"test\",\"keyMethods\":[{\"name\":\"process\",\"line\":\"10\",\"calls\":[\"doSomething()\"]}],\"architecture_issues\":[{\"issue\":\"test\"}]}";
        String result = OutputNormalizer.normalize(json);
        assertFalse(result.contains("architecture_issues"), "architecture_issues should be removed");
        assertTrue(result.contains("doSomething"), "calls should still be normalized");
        assertFalse(result.contains("doSomething()"), "parentheses should be stripped");
    }

    @Test
    void testNormalizeMethodCallDepsRemovedFromDeps() {
        // method_call 类型 deps 应从 dependencies 中移除，并追加到对应 keyMethods 的 calls
        String json = "{" +
            "\"dependencies\": [" +
            "  {\"name\": \"userMapper\", \"type\": \"field\", \"line\": \"1\"}," +
            "  {\"name\": \"base64Util\", \"type\": \"method_call\", \"line\": \"5\"}," +
            "  {\"name\": \"ltpaTokenManager\", \"type\": \"method_call\", \"line\": \"15\"}," +
            "  {\"name\": \"billService\", \"type\": \"field\", \"line\": \"20\"}" +
            "]," +
            "\"keyMethods\": [" +
            "  {\"name\": \"login\", \"line\": \"5\"}," +
            "  {\"name\": \"validateToken\", \"line\": \"15\"}" +
            "]" +
            "}";
        String result = OutputNormalizer.normalize(json);

        // 解析 JSON 验证 dependencies 数组
        JsonObject root = JsonParser.parseString(result).getAsJsonObject();
        JsonArray deps = root.getAsJsonArray("dependencies");
        assertEquals(2, deps.size(), "dependencies should only contain field types");
        assertEquals("userMapper", deps.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("billService", deps.get(1).getAsJsonObject().get("name").getAsString());

        // keyMethods.calls 应包含迁移的 method_call
        JsonArray kms = root.getAsJsonArray("keyMethods");
        assertTrue(kms.get(0).getAsJsonObject().has("calls"), "login should have calls");
        JsonArray loginCalls = kms.get(0).getAsJsonObject().getAsJsonArray("calls");
        assertTrue(loginCalls.size() >= 1, "login should have migrated calls");
        assertEquals("base64Util", loginCalls.get(0).getAsJsonObject().get("name").getAsString());

        assertTrue(kms.get(1).getAsJsonObject().has("calls"), "validateToken should have calls");
        JsonArray vtCalls = kms.get(1).getAsJsonObject().getAsJsonArray("calls");
        assertTrue(vtCalls.size() >= 1, "validateToken should have migrated calls");
        assertEquals("ltpaTokenManager", vtCalls.get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void testNormalizeUnmatchedMethodCallDropped() {
        // 无匹配 keyMethod 的 method_call 应被丢弃（不留在 deps 中）
        String json = "{" +
            "\"dependencies\": [" +
            "  {\"name\": \"userMapper\", \"type\": \"field\", \"line\": \"1\"}," +
            "  {\"name\": \"orphanCall\", \"type\": \"method_call\", \"line\": \"99\"}" +
            "]" +
            "}";
        String result = OutputNormalizer.normalize(json);

        JsonObject root = JsonParser.parseString(result).getAsJsonObject();
        JsonArray deps = root.getAsJsonArray("dependencies");
        assertEquals(1, deps.size(), "orphan method_call should be dropped");
        assertEquals("userMapper", deps.get(0).getAsJsonObject().get("name").getAsString());
    }
}
