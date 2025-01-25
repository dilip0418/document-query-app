package com.docqry.backend.controllers;


import com.docqry.backend.entities.EmbeddingRequest;
import com.docqry.backend.entities.Prompt;
import com.docqry.backend.exceptions.LLMCommunicationException;
import com.docqry.backend.exceptions.NoContextAvailableException;
import com.docqry.backend.services.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/prompt")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PromptController {

    private final Logger log = LoggerFactory.getLogger(PromptController.class);

    private final LLMService geminiService;
    private final ContextManagerService contextManagerService;
    private final QdrantService qdrantService;
    private final PromptManager promptManager;
    private final DocumentChunkService documentChunkService;

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generatePrompt(
            @RequestBody Prompt prompt
    ) {
        try {
            var response = promptManager.generateProcessedPrompt(prompt.getPromptText());
            if (response != null) {
                log.info("Prompt generated {}", response);
                return ResponseEntity.ok(Map.of("response", response));
            } else {
                log.error("Failed to generate prompt");
                return ResponseEntity.internalServerError().build();
            }
        } catch (NoContextAvailableException e) {
            log.error("Couldn't generate prompt due to missing context");
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> askQuery(@RequestBody EmbeddingRequest payload) {
        try {
            // Validate input
            if (payload.getQueryText() == null || payload.getQueryText().isEmpty()) {
                throw new IllegalArgumentException("Query text cannot be null or empty.");
            }

            // Extract query text and limit
            String queryText = payload.getQueryText();
            int topK = payload.getLimit();

            // Retrieve relevant vector embeddings from Qdrant
            var searchResults = qdrantService.getRelevantEmbeddings(queryText, topK);

            if (searchResults.isEmpty()) {
                log.warn("No results found for query: {}", queryText);
                return ResponseEntity.ok(Collections.emptyMap());
            }
            // Prepare the response with the results and  Get the results list from the processed search results
            Map<String, Object> processedSearchResults = qdrantService.processQueryResponse(searchResults);
            var results = (List<Map<String, Object>>) processedSearchResults.get("results");

            if (results.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            // Get the documentId from the results since it's the same for each item get the first result & Get the chunkIds from the results
            var documentId = (String) results.getFirst().get("docId");
            var chunkIds = results.stream()
                    .map(result -> (String) result.get("chunkId"))
                    .toList();

            // Retrieve the respective chunkText from PG database
            var relevantChunks = documentChunkService.getDocumentChunks(documentId, chunkIds);

            // reinitialize the context
            contextManagerService.appendToContext(queryText, relevantChunks);

            System.out.println(contextManagerService.getContext());

            return ResponseEntity.ok(Map.of("chunks", relevantChunks));

        } catch (IllegalArgumentException ex) {
            log.error("Invalid request payload: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Error processing query: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().body(Map.of("error", "An error occurred while processing the query."));
        }
    }

    @PostMapping("/llm-response")
    public ResponseEntity<Map<String, String>> getLLMResponse(
            @RequestBody EmbeddingRequest payload
    ) {
        try {
            //TODO: Prepare a processed prompt for the LLM
            if (payload.getQueryText() == null || payload.getQueryText().isEmpty()) {
                throw new IllegalArgumentException("Query text cannot be null or empty.");
            }
            if(contextManagerService.getActiveDocumentId() == null) {
                return ResponseEntity.internalServerError().body(Map.of("error", "No active document found to ;query."));
            }
            contextManagerService.buildContext(payload);

            // Build a processedPrompt that can be fed to the LLM
            var processedPrompt = promptManager.generateProcessedPrompt(payload.getQueryText());

            // Sanitize the processed prompt
            String sanitizedPrompt = geminiService.sanitizePrompt(processedPrompt);
            if (sanitizedPrompt == null || sanitizedPrompt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Processed prompt is required."));
            }

            // Query the LLM for the relevant information from the context/processedPrompt given.
            String llmResponse = geminiService.getLLMResponse(sanitizedPrompt);
            return ResponseEntity.ok(Map.of("llmResponse", llmResponse));
        } catch (LLMCommunicationException | NoContextAvailableException e) {
            log.error("Error generating LLM response: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        } catch (Exception ex) {
            log.error("Error generating LLM response: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().body(Map.of("error", "Oops! Something went wrong"));
        }
    }

}
