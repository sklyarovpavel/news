package com.example.newsapp.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class GigaChatClient {

    private final ChatClient chatClient;

    public GigaChatClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Выполняет генерацию ответа через GigaChat.
     * Параметр jsonFormat оставлен для совместимости с предыдущим интерфейсом;
     * формат вывода контролируется самим prompt'ом вызывающей стороны.
     */
    public String generate(String prompt, boolean jsonFormat) {
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();
    }
}

