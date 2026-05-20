package com.codelens.common.normalizers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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
}
