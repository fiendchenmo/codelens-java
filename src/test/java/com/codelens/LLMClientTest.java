package com.codelens;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class LLMClientTest {
    
    // 反射获取私有方法
    private Object invokePrivate(String methodName, Class<?> paramType, Object arg) throws Exception {
        Method m = LLMClient.class.getDeclaredMethod(methodName, paramType);
        m.setAccessible(true);
        return m.invoke(null, arg);
    }
    
    @Test
    void testEscapeJson_quotes() throws Exception {
        String result = (String) invokePrivate("escapeJson", String.class, "hello \"world\"");
        assertEquals("hello \\\"world\\\"", result);
    }
    
    @Test
    void testEscapeJson_backslash() throws Exception {
        String result = (String) invokePrivate("escapeJson", String.class, "path\\to\\file");
        assertEquals("path\\\\to\\\\file", result);
    }
    
    @Test
    void testEscapeJson_newline() throws Exception {
        String result = (String) invokePrivate("escapeJson", String.class, "line1\nline2");
        assertEquals("line1\\nline2", result);
    }
    
    @Test
    void testEscapeJson_tab() throws Exception {
        String result = (String) invokePrivate("escapeJson", String.class, "col1\tcol2");
        assertEquals("col1\\tcol2", result);
    }
    
    @Test
    void testEscapeJson_mixed() throws Exception {
        String result = (String) invokePrivate("escapeJson", String.class, "a\"b\\c\nd");
        assertEquals("a\\\"b\\\\c\\nd", result);
    }
    
    @Test
    void testEscapeJson_empty() throws Exception {
        String result = (String) invokePrivate("escapeJson", String.class, "");
        assertEquals("", result);
    }
    
    @Test
    void testExtractContentField_simple() throws Exception {
        String json = "{\"content\":\"hello world\"}";
        String result = (String) invokePrivate("extractContentField", String.class, json);
        assertEquals("hello world", result);
    }
    
    @Test
    void testExtractContentField_withEscapes() throws Exception {
        String json = "{\"content\":\"line1\\nline2\\\"quoted\\\"\"}";
        String result = (String) invokePrivate("extractContentField", String.class, json);
        assertEquals("line1\nline2\"quoted\"", result);
    }
    
    @Test
    void testExtractContentField_withBackslash() throws Exception {
        String json = "{\"content\":\"path\\\\to\\\\file\"}";
        String result = (String) invokePrivate("extractContentField", String.class, json);
        assertEquals("path\\to\\file", result);
    }
    
    @Test
    void testExtractContentField_noContentKey() throws Exception {
        String json = "{\"error\":\"something\"}";
        String result = (String) invokePrivate("extractContentField", String.class, json);
        assertEquals(json, result); // 返回原文
    }
}
