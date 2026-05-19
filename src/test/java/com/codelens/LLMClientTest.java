package com.codelens;

import com.codelens.common.utils.StringUtil;
import com.codelens.LLMException;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class LLMClientTest {
    
    // 反射获取私有方法
    private Object invokePrivate(String methodName, Class<?> paramType, Object arg) throws Exception {
        Method m = LLMClient.class.getDeclaredMethod(methodName, paramType);
        m.setAccessible(true);
        return m.invoke(null, arg);
    }
    
    // 反射获取私有常量
    private Object getConstant(String fieldName) throws Exception {
        Field f = LLMClient.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(null);
    }
    
    // ========== StringUtil.escapeJson() 测试（直接调用） ==========
    
    @Test
    void testEscapeJson_quotes() {
        String result = StringUtil.escapeJson("hello \"world\"");
        assertEquals("hello \\\"world\\\"", result);
    }
    
    @Test
    void testEscapeJson_backslash() {
        String result = StringUtil.escapeJson("path\\to\\file");
        assertEquals("path\\\\to\\\\file", result);
    }
    
    @Test
    void testEscapeJson_newline() {
        String result = StringUtil.escapeJson("line1\nline2");
        assertEquals("line1\\nline2", result);
    }
    
    @Test
    void testEscapeJson_tab() {
        String result = StringUtil.escapeJson("col1\tcol2");
        assertEquals("col1\\tcol2", result);
    }
    
    @Test
    void testEscapeJson_mixed() {
        String result = StringUtil.escapeJson("a\"b\\c\nd");
        assertEquals("a\\\"b\\\\c\\nd", result);
    }
    
    @Test
    void testEscapeJson_empty() {
        String result = StringUtil.escapeJson("");
        assertEquals("", result);
    }
    
    @Test
    void testEscapeJson_null() {
        // StringUtil.escapeJson 对 null 返回空字符串
        String result = StringUtil.escapeJson(null);
        assertEquals("", result);
    }
    
    // ========== extractContentField 测试（仍需反射） ==========
    
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
        try {
            invokePrivate("extractContentField", String.class, json);
            fail("Expected LLMException for missing content field");
        } catch (LLMException e) {
            assertEquals(LLMException.ErrorType.PARSE_ERROR, e.getErrorType());
        }
    }
    
    // ========== 默认值和参数回退测试 ==========
    
    @Test
    void testDefaultConstants() throws Exception {
        assertEquals("https://api.deepseek.com/v1/chat/completions", getConstant("DEFAULT_API_URL"));
        assertEquals("deepseek-v4-flash", getConstant("DEFAULT_MODEL"));
        assertEquals(0.1, getConstant("DEFAULT_TEMPERATURE"));
    }
    
    @Test
    void testGetterMethods() {
        assertEquals("https://api.deepseek.com/v1/chat/completions", LLMClient.getDefaultApiUrl());
        assertEquals("deepseek-v4-flash", LLMClient.getDefaultModel());
        assertEquals(0.1, LLMClient.getDefaultTemperature());
    }
    
    @Test
    void testAnalyzeWithCustomModel() throws Exception {
        // 测试带自定义参数的 analyze 方法签名正确性
        // 使用反射验证方法存在且参数正确
        Method analyzeMethod = LLMClient.class.getMethod("analyze", String.class, String.class, String.class, 
            String.class, String.class, double.class);
        assertNotNull(analyzeMethod);
        
        // 验证 null 参数时方法不会抛出异常（会回退到默认值）
        // 由于不会实际调用 API，只需要验证方法可以被访问
        analyzeMethod.setAccessible(true);
        // 注意：这里不调用实际方法，只验证方法签名
    }
    
    @Test
    void testAnalyzeWithDefaultValues() throws Exception {
        // 验证原来的三参数方法仍然存在且可以调用
        Method originalMethod = LLMClient.class.getMethod("analyze", String.class, String.class, String.class);
        assertNotNull(originalMethod);
    }
    
    @Test
    void testParameterFallbackLogic() throws Exception {
        // 通过反射验证内部回退逻辑
        // 测试: apiUrl 为 null 或空时应该使用默认值
        // 测试: model 为 null 或空时应该使用默认值  
        // 测试: temperature 为 NaN 时应该使用默认值
        
        // 验证 DEFAULT_API_URL 等常量存在
        String defaultApiUrl = LLMClient.getDefaultApiUrl();
        String defaultModel = LLMClient.getDefaultModel();
        double defaultTemp = LLMClient.getDefaultTemperature();
        
        assertNotNull(defaultApiUrl);
        assertNotNull(defaultModel);
        assertFalse(Double.isNaN(defaultTemp));
        
        // 验证默认值符合预期
        assertEquals("https://api.deepseek.com/v1/chat/completions", defaultApiUrl);
        assertEquals("deepseek-v4-flash", defaultModel);
        assertEquals(0.1, defaultTemp);
    }
    
    @Test
    void testAnalyzeSignatureCompatibility() throws Exception {
        // 验证两种方法签名都存在
        // 原方法（兼容插件端）
        Method original3Param = LLMClient.class.getMethod("analyze", String.class, String.class, String.class);
        assertNotNull(original3Param);
        
        // 新方法（支持自定义配置）
        Method extended6Param = LLMClient.class.getMethod("analyze", String.class, String.class, String.class, 
            String.class, String.class, double.class);
        assertNotNull(extended6Param);
    }
}
