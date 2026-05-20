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
}
