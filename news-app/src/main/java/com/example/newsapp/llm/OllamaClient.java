package com.example.newsapp.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
public class OllamaClient {
    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${llm.ollama.baseUrl}")
    private String baseUrl;

    @Value("${llm.ollama.model}")
    private String model;

    public String generate(String prompt, boolean jsonFormat) throws IOException, InterruptedException {
        String url = baseUrl.endsWith("/") ? baseUrl + "api/generate" : baseUrl + "/api/generate";
        GenerateRequest req = new GenerateRequest();
        req.setModel(model);
        req.setPrompt(prompt);
        req.setStream(false);
        if (jsonFormat) {
            req.setFormat("json");
        }
        String body = objectMapper.writeValueAsString(req);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            GenerateResponse gen = objectMapper.readValue(response.body(), GenerateResponse.class);
            return gen.response != null ? gen.response.trim() : "";
        } else {
            log.warn("Ollama generate failed: {} {}", response.statusCode(), response.body());
            throw new IOException("Ollama error: " + response.statusCode());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GenerateRequest {
        private String model;
        private String prompt;
        private boolean stream;
        private String format;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }
        public boolean isStream() { return stream; }
        public void setStream(boolean stream) { this.stream = stream; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GenerateResponse {
        @JsonProperty("response")
        public String response;
    }
}

