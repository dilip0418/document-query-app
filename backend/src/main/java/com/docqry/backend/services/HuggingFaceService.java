package com.docqry.backend.services;

import com.docqry.backend.exceptions.LLMCommunicationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HuggingFaceService implements LLMService {

    private final Logger log = LoggerFactory.getLogger(HuggingFaceService.class);
    private final RestTemplate restTemplate;


    @Override
    public String getLLMResponse(String processedPrompt) throws LLMCommunicationException {
        if (processedPrompt == null || processedPrompt.isEmpty()) {
            throw new IllegalArgumentException("Processed prompt cannot be null or empty.");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            //    @Value(("${huggingface.api.key}"))
            // Replace with your API token
            String apiToken = "";
            headers.set("Authorization", "Bearer " + apiToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Hugging Face Inference API expects this specific format
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("inputs", processedPrompt);
            // Optional parameters for Zephyr
            requestMap.put("parameters", Map.of(
                    "max_new_tokens", 256,
                    "temperature", 0.7,
                    "top_p", 0.95,
                    "do_sample", true
            ));

            String requestBody = mapper.writeValueAsString(requestMap);
            log.debug("Request body: {}", requestBody);

            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

            RestTemplate restTemplateWithTimeout = new RestTemplateBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .readTimeout(Duration.ofSeconds(30))
                    .build();

            //    @Value("${huggingface.api.url}")
            String apiUrl = "";
            ResponseEntity<String> response = restTemplateWithTimeout.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                // Parse the response - Hugging Face returns an array of responses
                JsonNode responseArray = mapper.readTree(response.getBody());
                if (responseArray.isArray() && responseArray.size() > 0) {
                    String generatedText = responseArray.get(0)
                            .path("generated_text")
                            .asText();

                    // Clean up the response by removing the original prompt if it's included
                    if (generatedText.startsWith(processedPrompt)) {
                        generatedText = generatedText.substring(processedPrompt.length()).trim();
                    }

                    return generatedText;
                }
                return "No response generated";
            } else {
                throw new LLMCommunicationException(
                        "Failed to get a successful response. Status: " + response.getStatusCode()
                );
            }
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON: {}", e.getMessage(), e);
            throw new LLMCommunicationException("Error processing JSON request/response");
        } catch (HttpStatusCodeException ex) {
            log.error("HTTP error from LLM API: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new LLMCommunicationException(
                    "LLM API HTTP error: " + ex.getStatusCode() + ". Details: " + ex.getResponseBodyAsString()
            );
        } catch (Exception ex) {
            log.error("Error communicating with LLM API: {}", ex.getMessage(), ex);
            throw new LLMCommunicationException("Failed to fetch response from LLM API");
        }
    }
}
