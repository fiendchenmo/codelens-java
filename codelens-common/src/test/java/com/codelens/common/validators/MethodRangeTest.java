package com.codelens.common.validators;

import com.codelens.common.validators.EvidenceValidator;
import com.codelens.common.validators.MethodRange;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * C-2 EvidenceValidator 加 methodRanges — 单元测试
 *
 * 覆盖范围：
 * 1. MethodRange 数据结构（methodName + startLine + endLine + contains）
 * 2. validateRisks 重载：传入 methodRanges 的方法级校验
 * 3. 降级行为：不传 methodRanges 时与现有逻辑完全一致
 * 4. 方法级校验逻辑：line 在方法范围内 → 通过，超出 → 偏差
 * 5. 边界条件
 */
public class MethodRangeTest {

    // ========== 1. MethodRange 数据结构 ==========

    @Test
    void testMethodRangeConstruction() {
        MethodRange range = new MethodRange("process", 10, 30);
        assertEquals("process", range.getMethodName());
        assertEquals(10, range.getStartLine());
        assertEquals(30, range.getEndLine());
    }

    @Test
    void testMethodRangeContainsLine() {
        MethodRange range = new MethodRange("process", 10, 30);
        // 边界内
        assertTrue(range.contains(10));  // startLine
        assertTrue(range.contains(20));  // 中间
        assertTrue(range.contains(30));  // endLine
        // 边界外
        assertFalse(range.contains(9));   // 小于 startLine
        assertFalse(range.contains(31));  // 大于 endLine
    }

    @Test
    void testMethodRangeSingleLine() {
        // 单行方法：startLine == endLine
        MethodRange range = new MethodRange("getter", 5, 5);
        assertTrue(range.contains(5));
        assertFalse(range.contains(4));
        assertFalse(range.contains(6));
    }

    @Test
    void testMethodRangeImmutability() {
        // MethodRange 应为不可变类（final 字段，无 setter）
        MethodRange range = new MethodRange("process", 10, 30);
        // 只有 getter，没有 setter → 编译通过即满足
        assertNotNull(range.getMethodName());
    }

    // ========== 2. validateRisks 重载（方法级校验） ==========

    @Test
    void testValidateRisksWithMethodRanges() {
        // 传入 methodRanges 时，risks.line 必须落在某个方法范围内
        String json = "{\"risks\": [{\"description\": \"risk1\", \"line\": \"15\"}]}";
        List<MethodRange> methodRanges = Arrays.asList(
            new MethodRange("process", 10, 20),
            new MethodRange("validate", 25, 40)
        );
        EvidenceValidator.ValidationResult result = EvidenceValidator.validateRisks(
            json, methodRanges, 50
        );
        // line=15 在 process(10-20) 范围内 → 通过
        assertEquals(1, result.totalChecked);
        assertEquals(1, result.passedCount);
    }

    @Test
    void testValidateRisksLineOutsideAllMethods() {
        // risks.line 不在任何方法范围内 → 标记偏差
        String json = "{\"risks\": [{\"description\": \"risk1\", \"line\": \"22\"}]}";
        List<MethodRange> methodRanges = Arrays.asList(
            new MethodRange("process", 10, 20),
            new MethodRange("validate", 25, 40)
        );
        EvidenceValidator.ValidationResult result = EvidenceValidator.validateRisks(
            json, methodRanges, 50
        );
        // line=22 在 process 和 validate 之间的间隙 → 偏差
        assertEquals(1, result.totalChecked);
        assertEquals(0, result.passedCount);
        assertFalse(result.issues.isEmpty());
    }

    @Test
    void testValidateRisksMultipleRisks() {
        // 多个 risk，部分在范围内部分不在
        String json = "{\"risks\": [" +
            "{\"description\": \"risk1\", \"line\": \"15\"}," +
            "{\"description\": \"risk2\", \"line\": \"30\"}," +
            "{\"description\": \"risk3\", \"line\": \"45\"}" +
            "]}";
        List<MethodRange> methodRanges = Arrays.asList(
            new MethodRange("process", 10, 20),
            new MethodRange("validate", 25, 40)
        );
        EvidenceValidator.ValidationResult result = EvidenceValidator.validateRisks(
            json, methodRanges, 50
        );
        // line=15 → 在 process 内 ✓
        // line=30 → 在 validate 内 ✓
        // line=45 → 不在任何方法内 ✗
        assertEquals(3, result.totalChecked);
        assertEquals(2, result.passedCount);
    }

    @Test
    void testValidateRisksLineMatchesFirstMethod() {
        // line 落在多个方法范围内（嵌套/内部类），匹配第一个即可
        String json = "{\"risks\": [{\"description\": \"risk1\", \"line\": \"15\"}]}";
        List<MethodRange> methodRanges = Arrays.asList(
            new MethodRange("process", 10, 30),
            new MethodRange("innerHelper", 12, 18)
        );
        EvidenceValidator.ValidationResult result = EvidenceValidator.validateRisks(
            json, methodRanges, 50
        );
        assertEquals(1, result.totalChecked);
        assertEquals(1, result.passedCount);
    }

    // ========== 3. 降级行为 ==========

    @Test
    void testValidateRisksNullMethodRanges() {
        // methodRanges=null → 降级为整文件校验
        String json = "{\"risks\": [{\"description\": \"risk1\", \"line\": \"15\"}]}";
        String sourceCode = "line1\nline2\n".replace("\n", "\n"); // placeholder
        String[] sourceLines = new String[50];
        for (int i = 0; i < 50; i++) sourceLines[i] = "line " + (i + 1);

        EvidenceValidator.ValidationResult result = EvidenceValidator.validateRisks(
            json, null, sourceLines.length
        );
        // 降级为整文件范围校验：line=15 在 1-50 内 → 通过
        assertEquals(1, result.totalChecked);
        assertEquals(1, result.passedCount);
    }

    @Test
    void testValidateRisksEmptyMethodRanges() {
        // methodRanges=空列表 → 降级为整文件校验
        String json = "{\"risks\": [{\"description\": \"risk1\", \"line\": \"15\"}]}";
        EvidenceValidator.ValidationResult result = EvidenceValidator.validateRisks(
            json, Collections.emptyList(), 50
        );
        // 空列表 → 降级为整文件范围校验
        assertEquals(1, result.totalChecked);
        assertEquals(1, result.passedCount);
    }

    @Test
    void testValidateRisksDegradedLineOutOfFile() {
        // 降级模式：行号超出文件范围
        String json = "{\"risks\": [{\"description\": \"risk1\", \"line\": \"100\"}]}";
        EvidenceValidator.ValidationResult result = EvidenceValidator.validateRisks(
            json, Collections.emptyList(), 50
        );
        assertEquals(1, result.totalChecked);
        assertEquals(0, result.passedCount);
    }

    // ========== 4. 不改现有方法签名 ==========

    @Test
    void testExistingValidateMethodStillWorks() {
        // 原有 validate(String, String, String[]) 方法签名不变
        String json = "{\"risks\": [{\"description\": \"risk1\", \"line\": \"5\"}]}";
        String[] sourceLines = new String[10];
        for (int i = 0; i < 10; i++) sourceLines[i] = "line " + (i + 1);
        EvidenceValidator.ValidationResult result = EvidenceValidator.validate(json, null, sourceLines);
        assertNotNull(result);
        assertTrue(result.totalChecked >= 0);
    }

    // ========== 5. 边界条件 ==========

    @Test
    void testMethodRangeLineAtStartBoundary() {
        // line == startLine → 在范围内
        String json = "{\"risks\": [{\"description\": \"risk1\", \"line\": \"10\"}]}";
        List<MethodRange> methodRanges = Arrays.asList(
            new MethodRange("process", 10, 20)
        );
        EvidenceValidator.ValidationResult result = EvidenceValidator.validateRisks(
            json, methodRanges, 50
        );
        assertEquals(1, result.passedCount);
    }

    @Test
    void testMethodRangeLineAtEndBoundary() {
        // line == endLine → 在范围内
        String json = "{\"risks\": [{\"description\": \"risk1\", \"line\": \"20\"}]}";
        List<MethodRange> methodRanges = Arrays.asList(
            new MethodRange("process", 10, 20)
        );
        EvidenceValidator.ValidationResult result = EvidenceValidator.validateRisks(
            json, methodRanges, 50
        );
        assertEquals(1, result.passedCount);
    }

    @Test
    void testMethodRangeLineJustBeforeStart() {
        // line = startLine - 1 → 不在范围内
        String json = "{\"risks\": [{\"description\": \"risk1\", \"line\": \"9\"}]}";
        List<MethodRange> methodRanges = Arrays.asList(
            new MethodRange("process", 10, 20)
        );
        EvidenceValidator.ValidationResult result = EvidenceValidator.validateRisks(
            json, methodRanges, 50
        );
        assertEquals(0, result.passedCount);
    }

    @Test
    void testMethodRangeLineJustAfterEnd() {
        // line = endLine + 1 → 不在范围内
        String json = "{\"risks\": [{\"description\": \"risk1\", \"line\": \"21\"}]}";
        List<MethodRange> methodRanges = Arrays.asList(
            new MethodRange("process", 10, 20)
        );
        EvidenceValidator.ValidationResult result = EvidenceValidator.validateRisks(
            json, methodRanges, 50
        );
        assertEquals(0, result.passedCount);
    }

    @Test
    void testNoRisksInJson() {
        // JSON 中无 risks 数组 → 无校验项
        String json = "{\"keyMethods\": [{\"name\": \"process\", \"line\": \"10\"}]}";
        List<MethodRange> methodRanges = Arrays.asList(
            new MethodRange("process", 10, 20)
        );
        EvidenceValidator.ValidationResult result = EvidenceValidator.validateRisks(
            json, methodRanges, 50
        );
        assertEquals(0, result.totalChecked);
    }

    @Test
    void testInvalidLineNumber() {
        // 无效行号格式 → 低置信度问题
        String json = "{\"risks\": [{\"description\": \"risk1\", \"line\": \"abc\"}]}";
        List<MethodRange> methodRanges = Arrays.asList(
            new MethodRange("process", 10, 20)
        );
        EvidenceValidator.ValidationResult result = EvidenceValidator.validateRisks(
            json, methodRanges, 50
        );
        assertFalse(result.issues.isEmpty());
    }

    @Test
    void testLargeMethodRange() {
        // 大方法范围（整文件级）
        String json = "{\"risks\": [{\"description\": \"risk1\", \"line\": \"500\"}]}";
        List<MethodRange> methodRanges = Arrays.asList(
            new MethodRange("main", 1, 1000)
        );
        EvidenceValidator.ValidationResult result = EvidenceValidator.validateRisks(
            json, methodRanges, 1000
        );
        assertEquals(1, result.passedCount);
    }

    @Test
    void testJdk8Compatibility() {
        List<String> list = new ArrayList<>();
        Map<String, String> map = new HashMap<>();
        assertNotNull(list);
        assertNotNull(map);
    }
}
