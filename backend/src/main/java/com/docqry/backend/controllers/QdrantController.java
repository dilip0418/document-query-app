package com.docqry.backend.controllers;

import com.docqry.backend.entities.EmbeddingRequest;
import com.docqry.backend.services.QdrantService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/qdrant")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class QdrantController {

    private final QdrantService qdrantService;


    Logger log = LoggerFactory.getLogger(QdrantController.class);

    @PostMapping("/retrieve-embeds")
    public ResponseEntity<List<Map<String, Object>>> retrieveEmbeddings(@RequestBody EmbeddingRequest payload) {
        try {
            // Validate input
            if (payload.getQueryText() == null || payload.getQueryText().isEmpty()) {
                throw new IllegalArgumentException("Query text cannot be null or empty.");
            }
            if (payload.getLimit() <= 0) {
                throw new IllegalArgumentException("Limit must be greater than 0.");
            }

            // Extract query text and limit
            String queryText = payload.getQueryText();
            int topK = payload.getLimit();

            var searchResults = qdrantService.getRelevantEmbeddings(queryText, topK);

            if (searchResults.isEmpty()) {
                log.warn("No results found for query: {}", queryText);
                return ResponseEntity.ok(Collections.emptyList());
            }

            // Prepare the response with the results
            List<Map<String, Object>> response = qdrantService.prepareResponse(searchResults);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            log.error("Invalid request payload: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(Collections.singletonList(Map.of("error", ex.getMessage())));
        } catch (Exception ex) {
            log.error("Error retrieving embeddings: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonList(Map.of("error", "An error occurred while retrieving embeddings.")));
        }
    }
}
