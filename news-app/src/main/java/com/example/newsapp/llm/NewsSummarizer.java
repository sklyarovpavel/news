package com.example.newsapp.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NewsSummarizer {
    private static final Logger log = LoggerFactory.getLogger(NewsSummarizer.class);
    private final OllamaClient ollamaClient;

    public NewsSummarizer(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    public String summarize(String titleAndText) {
        if (titleAndText == null || titleAndText.isBlank()) {
            return null;
        }
        String prompt = buildPrompt(titleAndText);
        try {
            // Для сводки не требуем JSON; вернём чистый текст
            log.info("LLM: summarize call promptLen={} textLen={}",
                    prompt == null ? 0 : prompt.length(),
                    titleAndText == null ? 0 : titleAndText.length());
            String resp = ollamaClient.generate(prompt, false);
            if (resp != null) {
                log.info("LLM: summarize responseLen={}", resp.length());
                return resp.trim();
            }
        } catch (Exception e) {
            log.warn("LLM summarize error: {}", e.toString());
        }
        return null;
    }

    private String buildPrompt(String text) {
        return """
                Ты — помощник, делающий краткие сводки новостей для Telegram. Ответь на русском.
                Сформируй краткую выжимку сути (2–3 предложения, максимум 400 символов).
                Без лишней воды, без перечисления второстепенных деталей. Если есть конкретные даты/сроки/действия — укажи.
                
                Исходный текст:
                ---
                %s
                ---
                Краткая сводка:
                """.formatted(text);
    }
}

