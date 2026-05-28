package com.codelens.common.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationResultTest {

    @Test
    public void testOk_ReturnsValidResult() {
        ValidationResult result = ValidationResult.ok();

        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
        assertNull(result.getFieldName());
    }

    @Test
    public void testFail_ReturnsInvalidResult() {
        ValidationResult result = ValidationResult.fail("className", "className 缺失");

        assertFalse(result.isValid());
        assertEquals("className", result.getFieldName());
        assertEquals("className 缺失", result.getErrorMessage());
    }
}
