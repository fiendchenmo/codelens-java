package com.codelens.common.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GroupingValidator} 分组校验测试。
 */
class GroupingValidatorTest {

    private GroupingValidator validator;

    @BeforeEach
    void setUp() {
        validator = new GroupingValidator();
    }

    @Test
    void validMultipleGroups() {
        String json = "["
                + "{\"group\":\"业务逻辑\",\"files\":[\"OrderService\",\"PaymentService\"]},"
                + "{\"group\":\"数据访问\",\"files\":[\"OrderRepository\"]}"
                + "]";
        ValidationResult result = validator.validate(json);
        assertTrue(result.isValid());
    }

    @Test
    void validSingleGroup() {
        String json = "["
                + "{\"group\":\"工具类\",\"files\":[\"StringUtil\",\"DateUtil\"]}"
                + "]";
        ValidationResult result = validator.validate(json);
        assertTrue(result.isValid());
    }

    @Test
    void emptyArray() {
        String json = "[]";
        ValidationResult result = validator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("至少"));
    }

    @Test
    void invalidNotArray() {
        String json = "{\"group\":\"test\",\"files\":[]}";
        ValidationResult result = validator.validate(json);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("数组"));
    }

    @Test
    void nullOrEmptyJson() {
        ValidationResult nullResult = validator.validate(null);
        assertFalse(nullResult.isValid());
        assertTrue(nullResult.getErrorMessage().contains("空"));

        ValidationResult emptyResult = validator.validate("");
        assertFalse(emptyResult.isValid());
        assertTrue(emptyResult.getErrorMessage().contains("空"));
    }

    @Test
    void groupWithoutName() {
        String json = "["
                + "{\"files\":[\"SomeFile\"]}"
                + "]";
        ValidationResult result = validator.validate(json);
        assertFalse(result.isValid());
    }

    @Test
    void groupWithoutFiles() {
        String json = "["
                + "{\"group\":\"孤分组\"}"
                + "]";
        ValidationResult result = validator.validate(json);
        assertFalse(result.isValid());
    }

    @Test
    void groupWithEmptyFiles() {
        String json = "["
                + "{\"group\":\"空组\",\"files\":[]}"
                + "]";
        ValidationResult result = validator.validate(json);
        assertFalse(result.isValid());
    }

    @Test
    void useNameInsteadOfGroup() {
        String json = "["
                + "{\"name\":\"业务逻辑\",\"files\":[\"OrderService\"]}"
                + "]";
        ValidationResult result = validator.validate(json);
        assertTrue(result.isValid());
    }
}
