package com.codelens.common.prompts;

import com.codelens.common.models.CodeMetaData;
import com.codelens.common.models.SchemaVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SystemPrompt 单元测试
 * 覆盖 C-3（版本化buildBase）+ C-4（双模板 + 两阶段填充说明）
 *
 * @since 0.2.9
 */
class SystemPromptTest {

    // ==================== C-3: buildBase 版本化 ====================

    @Nested
    class BuildBaseV2Tests {

        @Test
        void testBuildBaseDefaultReturnsV2() {
            String prompt = SystemPrompt.buildBase();
            assertNotNull(prompt);
            assertTrue(prompt.contains("\"dependencies\""), "V2 prompt should contain dependencies");
            assertTrue(prompt.contains("\"keyMethods\""), "V2 prompt should contain keyMethods");
            assertTrue(prompt.contains("\"design_intent\""), "V2 prompt should contain design_intent");
        }

        @Test
        void testBuildBaseV2Explicit() {
            String prompt = SystemPrompt.buildBase(SchemaVersion.V2);
            assertNotNull(prompt);
            assertTrue(prompt.contains("\"dependencies\""));
            assertTrue(prompt.contains("\"keyMethods\""));
            assertTrue(prompt.contains("\"design_intent\""));
            assertTrue(prompt.contains("\"class_analysis\""));
            assertTrue(prompt.contains("\"framework_integration\""));
        }

        @Test
        void testBuildBaseV2ContainsCoreRules() {
            String prompt = SystemPrompt.buildBase(SchemaVersion.V2);
            assertTrue(prompt.contains(CodeMetaData.CORE_RULES), "V2 prompt should include CORE_RULES");
        }

        @Test
        void testBuildBaseV2NotContainV3TopLevelFields() {
            String prompt = SystemPrompt.buildBase(SchemaVersion.V2);
            // V2 不应包含 V3 的顶层字段 fields/methods/framework
            // 注意: "methods" 可能出现在 keyMethods 中，所以只检查顶层 schema 部分
            String schemaPart = prompt.substring(
                prompt.indexOf("JSON Schema"),
                prompt.indexOf("核心分析规则") > 0 ? prompt.indexOf("核心分析规则") : prompt.length()
            );
            assertFalse(schemaPart.contains("\"fields\""), "V2 schema should not contain V3 'fields'");
        }
    }

    @Nested
    class BuildBaseV3Tests {

        @Test
        void testBuildBaseV3ContainsV3Schema() {
            String prompt = SystemPrompt.buildBase(SchemaVersion.V3);
            assertNotNull(prompt);
            assertTrue(prompt.contains("\"summary\""), "V3 prompt should contain summary");
            assertTrue(prompt.contains("\"framework\""), "V3 prompt should contain framework");
            assertTrue(prompt.contains("\"fields\""), "V3 prompt should contain fields");
            assertTrue(prompt.contains("\"methods\""), "V3 prompt should contain methods");
        }

        @Test
        void testBuildBaseV3NotContainV2Fields() {
            String prompt = SystemPrompt.buildBase(SchemaVersion.V3);
            assertFalse(prompt.contains("\"dependencies\""), "V3 prompt should not contain V2 dependencies");
            assertFalse(prompt.contains("\"keyMethods\""), "V3 prompt should not contain V2 keyMethods");
            assertFalse(prompt.contains("\"design_intent\""), "V3 prompt should not contain V2 design_intent");
            assertFalse(prompt.contains("\"class_analysis\""), "V3 prompt should not contain V2 class_analysis");
            assertFalse(prompt.contains("\"framework_integration\""), "V3 prompt should not contain V2 framework_integration");
        }

        @Test
        void testBuildBaseV3ContainsCoreRules() {
            String prompt = SystemPrompt.buildBase(SchemaVersion.V3);
            assertTrue(prompt.contains(CodeMetaData.CORE_RULES), "V3 prompt should include CORE_RULES");
        }
    }

    @Nested
    class BuildBaseNullTests {

        @Test
        void testBuildBaseNullFallsBackToV2() {
            String nullResult = SystemPrompt.buildBase(null);
            String v2Result = SystemPrompt.buildBase(SchemaVersion.V2);
            assertEquals(v2Result, nullResult, "null version should fall back to V2");
        }
    }

    // ==================== C-4: 双模板 + 两阶段填充说明 ====================

    @Nested
    class C4TwoStagePromptTests {

        @Test
        void testV3PromptContainsTwoStageDescription() {
            // C-4 验收标准: V3 prompt含两阶段说明
            String prompt = SystemPrompt.buildBase(SchemaVersion.V3);
            // 两阶段关键词 — 实现后这些断言应通过
            assertTrue(
                prompt.contains("阶段") || prompt.contains("stage") || prompt.contains("phase"),
                "V3 prompt should contain two-stage filling description"
            );
        }

        @Test
        void testV2PromptDoesNotContainTwoStageDescription() {
            // C-4 验收标准: V2不变
            String v2Prompt = SystemPrompt.buildBase(SchemaVersion.V2);
            assertFalse(
                v2Prompt.contains("第一阶段") && v2Prompt.contains("第二阶段"),
                "V2 prompt should not contain explicit two-stage description"
            );
        }

        @Test
        void testV3PromptSchemaDifferentFromV2() {
            String v2Prompt = SystemPrompt.buildBase(SchemaVersion.V2);
            String v3Prompt = SystemPrompt.buildBase(SchemaVersion.V3);
            assertNotEquals(v2Prompt, v3Prompt, "V2 and V3 prompts should differ");
        }
    }

    // ==================== CLI build() 完整 prompt ====================

    @Nested
    class CLIBuildTests {

        @Test
        void testBuildContainsCLIRules() {
            String prompt = SystemPrompt.build();
            assertNotNull(prompt);
            assertTrue(prompt.contains("CLI 端特有要求"), "CLI build() should contain CLI-specific rules");
        }

        @Test
        void testBuildWithStructContext() {
            String structContext = "[代码结构底图]\nfields: userMapper, billService";
            String prompt = SystemPrompt.build(structContext);
            assertTrue(prompt.contains(structContext), "Should include struct context when provided");
        }

        @Test
        void testBuildWithNullStructContext() {
            String prompt = SystemPrompt.build(null);
            assertNotNull(prompt);
            // CORE_RULES 中含有 "null" 关键词（空指针规则），不能简单断言不包含
            // 验证 structContext=null 不会拼入额外内容：与 build() 无参版一致
            assertEquals(SystemPrompt.build(), prompt,
                "build(null) should equal build() — no extra content injected");
        }

        @Test
        void testBuildWithEmptyStructContext() {
            String prompt = SystemPrompt.build("");
            assertNotNull(prompt);
        }

        @Test
        void testBuildContainsFewShot() {
            String prompt = SystemPrompt.build();
            assertTrue(prompt.contains("Few-shot"), "CLI build() should contain few-shot examples");
        }
    }
}
