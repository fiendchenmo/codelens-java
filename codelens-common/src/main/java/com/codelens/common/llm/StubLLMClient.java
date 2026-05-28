package com.codelens.common.llm;

import java.util.LinkedList;
import java.util.Queue;

/**
 * 测试用 Stub LLM Client。
 * <p>
 * 返回预定义的固定 JSON，支持多轮调用时使用队列依次返回不同内容。
 */
public class StubLLMClient implements LLMClient {

    private final Queue<String> responses;
    private int callCount;

    public StubLLMClient(String fixedResponse) {
        this.responses = new LinkedList<>();
        this.responses.add(fixedResponse);
        this.callCount = 0;
    }

    public StubLLMClient(Queue<String> responses) {
        this.responses = new LinkedList<>(responses);
        this.callCount = 0;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        callCount++;
        if (responses.isEmpty()) {
            return "";
        }
        String response = responses.poll();
        // 保持最后一个响应为默认值
        if (responses.isEmpty()) {
            responses.add(response);
        }
        return response;
    }

    public int getCallCount() {
        return callCount;
    }
}
