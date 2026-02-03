package com.example.newsapp.api;

import com.example.newsapp.llm.NewsClassifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/llm", produces = MediaType.APPLICATION_JSON_VALUE)
public class LlmController {
    private final NewsClassifier classifier;

    public LlmController(NewsClassifier classifier) {
        this.classifier = classifier;
    }

    public record ClassifyRequest(String text) {}
    public record ClassifyResponse(boolean suitable, double confidence) {}

    @PostMapping("/classify")
    public ClassifyResponse classify(@RequestBody ClassifyRequest req) {
        var result = classifier.classify(req.text());
        return new ClassifyResponse(result.isSuitable(), result.getConfidence());
    }
}

