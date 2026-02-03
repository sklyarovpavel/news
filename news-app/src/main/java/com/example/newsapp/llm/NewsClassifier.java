package com.example.newsapp.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NewsClassifier {
    private static final Logger log = LoggerFactory.getLogger(NewsClassifier.class);
    private final OllamaClient ollamaClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public NewsClassifier(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    public ClassificationResult classify(String text) {
        boolean heuristicRelevant = isHeuristicallyRelevant(text);
        String prompt = buildPrompt(text);
        try {
            String resp = ollamaClient.generate(prompt, true);
            // ожидаем JSON {"suitable":true/false,"confidence":0..1,"reasons":[...]}
            try {
                JsonNode node = mapper.readTree(resp);
                boolean suitable = node.path("suitable").asBoolean(false);
                double confidence = node.path("confidence").asDouble(0.0);
                ClassificationResult result = new ClassificationResult();
                result.setSuitable(suitable);
                result.setConfidence(confidence);
                if (heuristicRelevant && !suitable) {
                    // Подстраховка на случай промахов модели по ключевым терминам
                    result.setSuitable(true);
                    result.setConfidence(Math.max(confidence, 0.8));
                } else if (heuristicRelevant && suitable && confidence < 0.7) {
                    result.setConfidence(0.7);
                }
                // reasons optional
                return result;
            } catch (Exception parseEx) {
                // fallback: простая эвристика по тексту
                String normalized = resp.trim().toLowerCase();
                boolean suitable = normalized.contains("true") || normalized.contains("yes");
                ClassificationResult result = new ClassificationResult();
                boolean finalSuitable = suitable || heuristicRelevant;
                result.setSuitable(finalSuitable);
                result.setConfidence(finalSuitable ? 0.8 : 0.5);
                return result;
            }
        } catch (Exception e) {
            log.error("LLM classify error", e);
            ClassificationResult result = new ClassificationResult();
            boolean finalSuitable = heuristicRelevant;
            result.setSuitable(finalSuitable);
            result.setConfidence(finalSuitable ? 0.8 : 0.0);
            return result;
        }
    }

    private boolean isHeuristicallyRelevant(String text) {
        if (text == null) return false;
        String t = text.toLowerCase();
        String[] outage = {"недоступ", "outage", "downtime", "инцидент", "авар", "деградац", "degraded", "maintenance", "обслуживан"};
        String[] api = {"api v", "новая версия api", "deprecated", "устаревш", "миграц", "breaking change", "обновление api"};
        String[] conn = {"endpoint", "эндпоинт", "url", "адрес", "ip", "allowlist", "белый список", "tls", "сертификат", "certificate", "порт", "protocol", "протокол", "oauth", "token", "токен", "ключ доступа", "sdk", "driver", "драйвер"};
        for (String k : outage) if (t.contains(k)) return true;
        for (String k : api) if (t.contains(k)) return true;
        for (String k : conn) if (t.contains(k)) return true;
        return false;
    }

    private String buildPrompt(String text) {
        return """
        You are a strict news triage classifier for IT service operations. Answer in Russian.
        Decide if the news item is RELEVANT for our processing. We only care about:
        - Unavailability / outages / incidents / degraded performance / maintenance of IT services
        - API changes: new versions, deprecations, breaking changes, migration notices
        - Connection/config changes: endpoints/URLs, auth methods/credentials/tokens, IP allowlists, TLS/certificates, ports, protocols, SDK/driver requirements
        
        NOT relevant examples:
        - General product launches, marketing/PR, hiring/financial news
        - Generic tech articles with no actionable change or incident
        - Hardware releases or consumer features unrelated to service availability or integration
        
        Output strictly and only this JSON object (no extra keys, no prose):
        {"suitable": <true|false>, "confidence": <number 0..1>, "reasons": [<short strings>]}
        - suitable: true only if the item clearly matches one of the relevant categories above
        - confidence: 0..1 reflecting certainty (use <=0.3 if unsure)
        
        Examples:
        News: "Сервис ABC испытывает недоступность в регионе EU-West, специалисты работают над восстановлением."
        Output: {"suitable": true, "confidence": 0.9, "reasons": ["недоступность сервиса","инцидент"]}
        
        News: "Провайдер DEF объявил об обязательном переходе на API v3 до 30 июня, старые эндпоинты будут отключены."
        Output: {"suitable": true, "confidence": 0.9, "reasons": ["обновление версии API","отключение старых эндпоинтов"]}
        
        News: "Изменены IP-адреса и TLS-сертификаты для подключения к сервису GHI, требуется обновление доверенных корней."
        Output: {"suitable": true, "confidence": 0.85, "reasons": ["изменения настроек подключения","TLS/сертификаты"]}
        
        News: "Компания открыла новый офис и планирует расширение штата."
        Output: {"suitable": false, "confidence": 0.95, "reasons": ["корпоративные новости, не затрагивает сервисы"]}
        
        News: "Представлен новый смартфон с улучшенной камерой."
        Output: {"suitable": false, "confidence": 0.95, "reasons": ["потребительский продукт, не про сервисы/интеграции"]}
        
        News:
        ---
        %s
        ---
        """.formatted(text);
    }

    public static class ClassificationResult {
        private boolean suitable;
        private double confidence;

        public boolean isSuitable() { return suitable; }
        public void setSuitable(boolean suitable) { this.suitable = suitable; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }
}

