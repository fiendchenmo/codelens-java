package com.codelens.common.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeMetaData 版本化Schema单元测试（C-3 增强）
 * 
 * @since 0.2.9
 */
class CodeMetaDataSchemaVersionTest {

    // ==================== V2 Schema ====================

    @Nested
    class V2SchemaTests {

        @Test
        void testGetSchemaV2ContainsAllRequiredFields() {
            String schema = CodeMetaData.getSchema(SchemaVersion.V2);
            assertNotNull(schema);
            assertTrue(schema.contains("\"summary\""));
            assertTrue(schema.contains("\"design_intent\""));
            assertTrue(schema.contains("\"class_analysis\""));
            assertTrue(schema.contains("\"dependencies\""));
            assertTrue(schema.contains("\"risks\""));
            assertTrue(schema.contains("\"keyMethods\""));
            assertTrue(schema.contains("\"framework_integration\""));
        }

        @Test
        void testV2SchemaDependenciesStructure() {
            String schema = CodeMetaData.getSchema(SchemaVersion.V2);
            // dependencies 应含 name/type/line/description
            assertTrue(schema.contains("\"name\""));
            assertTrue(schema.contains("\"type\""));
            assertTrue(schema.contains("\"line\""));
            assertTrue(schema.contains("\"description\""));
        }

        @Test
        void testV2SchemaKeyMethodsStructure() {
            String schema = CodeMetaData.getSchema(SchemaVersion.V2);
            // keyMethods 应含 name/line/signature/visibility/annotations/calls/description
            assertTrue(schema.contains("\"signature\""));
            assertTrue(schema.contains("\"visibility\""));
            assertTrue(schema.contains("\"annotations\""));
            assertTrue(schema.contains("\"calls\""));
        }

        @Test
        void testV2SchemaRisksStructure() {
            String schema = CodeMetaData.getSchema(SchemaVersion.V2);
            assertTrue(schema.contains("\"severity\""));
            assertTrue(schema.contains("\"impact\""));
            assertTrue(schema.contains("\"suggestion\""));
        }
    }

    // ==================== V3 Schema ====================

    @Nested
    class V3SchemaTests {

        @Test
        void testGetSchemaV3ContainsAllRequiredFields() {
            String schema = CodeMetaData.getSchema(SchemaVersion.V3);
            assertNotNull(schema);
            assertTrue(schema.contains("\"summary\""));
            assertTrue(schema.contains("\"framework\""));
            assertTrue(schema.contains("\"fields\""));
            assertTrue(schema.contains("\"methods\""));
        }

        @Test
        void testV3SchemaNotContainV2Fields() {
            String schema = CodeMetaData.getSchema(SchemaVersion.V3);
            assertFalse(schema.contains("\"dependencies\""));
            assertFalse(schema.contains("\"keyMethods\""));
            assertFalse(schema.contains("\"framework_integration\""));
            assertFalse(schema.contains("\"design_intent\""));
            assertFalse(schema.contains("\"class_analysis\""));
        }

        @Test
        void testV3MethodsStructure() {
            String schema = CodeMetaData.getSchema(SchemaVersion.V3);
            assertTrue(schema.contains("\"complexity\""));
            assertTrue(schema.contains("\"params\""));
            assertTrue(schema.contains("\"logic_summary\""));
            assertTrue(schema.contains("\"calls\""));
            assertTrue(schema.contains("\"return\""));
            assertTrue(schema.contains("\"exceptions\""));
            assertTrue(schema.contains("\"called_by\""));
            assertTrue(schema.contains("\"risks\""));
        }

        @Test
        void testV3FieldsStructure() {
            String schema = CodeMetaData.getSchema(SchemaVersion.V3);
            assertTrue(schema.contains("\"injectType\""));
            assertTrue(schema.contains("\"type\""));
        }

        @Test
        void testV3CallsStructure() {
            String schema = CodeMetaData.getSchema(SchemaVersion.V3);
            // V3 calls 是复杂对象: target/line/type
            assertTrue(schema.contains("\"target\""));
        }
    }

    // ==================== 版本选择逻辑 ====================

    @Nested
    class VersionSelectionTests {

        @Test
        void testGetSchemaNullReturnsV2() {
            String schema = CodeMetaData.getSchema(null);
            assertNotNull(schema);
            assertTrue(schema.contains("\"dependencies\""));
            assertTrue(schema.contains("\"keyMethods\""));
        }

        @Test
        void testGetAvailableVersions() {
            SchemaVersion[] versions = CodeMetaData.getAvailableVersions();
            assertEquals(2, versions.length);
            assertEquals(SchemaVersion.V2, versions[0]);
            assertEquals(SchemaVersion.V3, versions[1]);
        }

        @Test
        void testJsonSchemaBackwardCompatible() {
            assertEquals(CodeMetaData.getSchema(SchemaVersion.V2), CodeMetaData.JSON_SCHEMA);
        }

        @Test
        void testV2AndV3SchemasAreDifferent() {
            String v2 = CodeMetaData.getSchema(SchemaVersion.V2);
            String v3 = CodeMetaData.getSchema(SchemaVersion.V3);
            assertNotEquals(v2, v3, "V2 and V3 schemas must differ");
        }
    }

    // ==================== V2→V3 字段映射验证 ====================

    @Nested
    class FieldMappingTests {

        @Test
        void testDependenciesMapsToFieldsAndCalls() {
            // V2 dependencies → V3 fields + methods.calls
            String v2 = CodeMetaData.getSchema(SchemaVersion.V2);
            String v3 = CodeMetaData.getSchema(SchemaVersion.V3);
            // V2 有 dependencies，V3 用 fields 替代字段注入部分
            assertTrue(v2.contains("\"dependencies\""));
            assertTrue(v3.contains("\"fields\""));
            assertTrue(v3.contains("\"calls\""));
        }

        @Test
        void testRisksMapsToMethodRisks() {
            // V2 risks → V3 methods[].risks
            String v2 = CodeMetaData.getSchema(SchemaVersion.V2);
            String v3 = CodeMetaData.getSchema(SchemaVersion.V3);
            assertTrue(v2.contains("\"risks\""));
            assertTrue(v3.contains("\"risks\"")); // V3 risks 在 methods 内部
        }

        @Test
        void testFrameworkIntegrationMapsToFramework() {
            // V2 framework_integration → V3 顶层 framework
            String v2 = CodeMetaData.getSchema(SchemaVersion.V2);
            String v3 = CodeMetaData.getSchema(SchemaVersion.V3);
            assertTrue(v2.contains("\"framework_integration\""));
            assertTrue(v3.contains("\"framework\""));
            assertFalse(v3.contains("\"framework_integration\""));
        }

        @Test
        void testDesignIntentClassAnalysisMergedIntoSummary() {
            // V2 design_intent + class_analysis → V3 summary
            String v2 = CodeMetaData.getSchema(SchemaVersion.V2);
            String v3 = CodeMetaData.getSchema(SchemaVersion.V3);
            assertTrue(v2.contains("\"design_intent\""));
            assertTrue(v2.contains("\"class_analysis\""));
            assertFalse(v3.contains("\"design_intent\""));
            assertFalse(v3.contains("\"class_analysis\""));
            assertTrue(v3.contains("\"summary\""));
        }
    }
}
