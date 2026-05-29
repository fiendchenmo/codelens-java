package com.codelens;

import com.codelens.common.llm.LLMClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

public class CliLLMClientTest {

    @Test
    public void testConstructorStoresParams() throws Exception {
        CliLLMClient client = new CliLLMClient("test-key", "https://test.url", "test-model", 0.5);

        Field apiKeyField = CliLLMClient.class.getDeclaredField("apiKey");
        apiKeyField.setAccessible(true);
        assertEquals("test-key", apiKeyField.get(client));

        Field modelField = CliLLMClient.class.getDeclaredField("model");
        modelField.setAccessible(true);
        assertEquals("test-model", modelField.get(client));
    }

    @Test
    public void testChatReturnsString() {
        CliLLMClient client = new CliLLMClient("test-key", null, null, Double.NaN);
        // 使用无效的 API key 应当抛出 RuntimeException（包装 LLMException）
        assertThrows(RuntimeException.class, () -> {
            client.chat("system", "user");
        });
    }

    @Test
    public void testImplementsInterface() {
        CliLLMClient client = new CliLLMClient("k", null, null, Double.NaN);
        assertTrue(client instanceof LLMClient);
    }
}
