package com.posgateway.aml.service.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.jev.JevProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @deprecated Use {@link OpenRouterChatClient} for chat completions and {@link JevDecisionsClient} for decisions.
 */
@Deprecated
@Component
public class OpenRouterClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);

    public record OpenRouterResponse(
            String content,
            String modelUsed,
            Integer inputTokens,
            Integer outputTokens,
            Double estimatedCostUsd,
            long latencyMs
    ) {}

    private final OpenRouterChatClient chatClient;
    private final JevProperties properties;

    public OpenRouterClient(OpenRouterChatClient chatClient, JevProperties properties) {
        this.chatClient = chatClient;
        this.properties = properties;
    }

    public OpenRouterResponse chatCompletion(String model, String systemPrompt, String userPrompt,
                                             Duration timeout) throws Exception {
        OpenRouterChatClient.ChatResponse response =
                chatClient.chatCompletion(model, systemPrompt, userPrompt, timeout);
        return new OpenRouterResponse(
                response.content(),
                response.modelUsed(),
                response.inputTokens(),
                response.outputTokens(),
                response.estimatedCostUsd(),
                response.latencyMs());
    }

    public static String redactSecrets(String text, JevProperties properties) {
        return OpenRouterChatClient.redactSecrets(text, properties);
    }
}
