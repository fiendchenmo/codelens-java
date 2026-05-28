package com.codelens.common.llm;

import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

public class StubLLMClientTest {

    @Test
    public void testChatReturnsFixedResponse() {
        StubLLMClient client = new StubLLMClient("{\"result\":\"ok\"}");
        String output = client.chat("system", "user");

        assertEquals("{\"result\":\"ok\"}", output);
        assertEquals(1, client.getCallCount());
    }

    @Test
    public void testChatWithMultipleResponses() {
        Queue<String> responses = new LinkedList<>();
        responses.add("{\"result\":\"first\"}");
        responses.add("{\"result\":\"second\"}");

        StubLLMClient client = new StubLLMClient(responses);

        assertEquals("{\"result\":\"first\"}", client.chat("sys", "user"));
        assertEquals("{\"result\":\"second\"}", client.chat("sys", "user"),
                "队列正常消费");
        assertEquals("{\"result\":\"second\"}", client.chat("sys", "user"),
                "队列消耗完后应始终返回最后一个值");
        assertEquals(3, client.getCallCount());
    }
}
