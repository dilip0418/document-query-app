package com.docqry.backend.services;

import com.docqry.backend.exceptions.LLMCommunicationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiService implements LLMService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1/models/gemini-1.5-pro:generateContent}")
    private String apiUrl;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public GeminiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }

    public String getLLMResponse(String prompt) throws LLMCommunicationException {
        try {
            // Construct request body
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> contents = new HashMap<>();
            contents.put("role", "user");
            contents.put("parts", List.of(Map.of("text", prompt)));

            requestBody.put("contents", List.of(contents));
            requestBody.put("generationConfig", Map.of(
                    "temperature", 0.7,
                    "topK", 40,
                    "topP", 0.95,
                    "maxOutputTokens", 2048,
                    "stopSequences", List.of()
            ));

            // Add API key to URL
            String fullUrl = apiUrl + "?key=" + apiKey;

            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Create request entity
            HttpEntity<String> requestEntity = new HttpEntity<>(
                    objectMapper.writeValueAsString(requestBody),
                    headers
            );

            log.debug("Sending request to Gemini API: {}", requestBody);

            // Make API call
            ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            // Parse response
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode responseNode = objectMapper.readTree(response.getBody());
                return extractTextFromGeminiResponse(responseNode);
            } else {
                throw new LLMCommunicationException("Failed to get successful response from Gemini API");
            }

        } catch (Exception e) {
            log.error("Error while communicating with Gemini API", e);
            throw new LLMCommunicationException("Failed to get response from Gemini API: " + e.getMessage());
        }
    }

    private String extractTextFromGeminiResponse(JsonNode responseNode) throws LLMCommunicationException {
        try {
            // Navigate through the response structure
            JsonNode candidatesNode = responseNode.path("candidates");
            if (candidatesNode.isArray() && !candidatesNode.isEmpty()) {
                JsonNode contentNode = candidatesNode.get(0).path("content");
                JsonNode partsNode = contentNode.path("parts");
                if (partsNode.isArray() && !partsNode.isEmpty()) {
                    return partsNode.get(0).path("text").asText();
                }
            }
            throw new LLMCommunicationException("Unable to extract text from Gemini response");
        } catch (Exception e) {
            log.error("Error parsing Gemini response", e);
            throw new LLMCommunicationException("Failed to parse Gemini response: " + e.getMessage());
        } catch (LLMCommunicationException e) {
            throw new RuntimeException(e);
        }
    }
}