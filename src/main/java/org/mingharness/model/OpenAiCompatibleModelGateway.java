package org.mingharness.model;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "harness.model", name = "enabled", havingValue = "true")
public class OpenAiCompatibleModelGateway implements ModelGateway {

    private final RestClient restClient;
    private final String model;

    public OpenAiCompatibleModelGateway(
            RestClient.Builder restClientBuilder,
            @Value("${harness.model.base-url}") String baseUrl,
            @Value("${harness.model.api-key}") String apiKey,
            @Value("${harness.model.name:demo-model}") String model) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.model = model;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ModelResponse complete(ModelRequest request) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", request.input())),
                "temperature", 0.2
        );
        Map<String, Object> response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getOrDefault("choices", List.of());
        Map<String, Object> firstChoice = choices.isEmpty() ? Map.of() : choices.get(0);
        Map<String, Object> message = (Map<String, Object>) firstChoice.getOrDefault("message", Map.of());
        String content = String.valueOf(message.getOrDefault("content", ""));
        return new ModelResponse(content, model, request.promptVersion(), 0, 0);
    }
}
