package com.docqry.backend.services;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PythonServiceClientImpl implements PythonServiceClient {

    private final Logger log = LoggerFactory.getLogger(PythonServiceClientImpl.class);
    private final RestTemplate restTemplate;

    private static final String BASE_URL = "http://embedding-service:8000";
    private static final String EMBEDDING_ENDPOINT = BASE_URL + "/generate-embeddings";
    private static final String SUMMARIZE_ENDPOINT = BASE_URL + "/summarize";
    private static final String INITIAL_SUMMARY_ENDPOINT = BASE_URL + "/initial-summary";

    @Data
    public static class SummarizationResponse {
        private String summary;                // Main query-focused summary
        private List<String> rankedChunks;     // Ranked chunks used in summary
        private List<Float> chunkScores;       // Relevance scores for chunks
    }

    @Data
    public static class InitialSummarizationResponse {
        private String overviewSummary;        // Document overview
        private List<String> keyTopics;        // Key topics identified
        private List<String> selectedChunks;   // Representative chunks used
    }

    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        EmbeddingRequest request = new EmbeddingRequest(texts);
        EmbeddingResponse response = restTemplate.postForObject(
                EMBEDDING_ENDPOINT,
                request,
                EmbeddingResponse.class
        );
        if (response == null) throw new AssertionError();
        return response.getEmbeddings();
    }

    @Override
    public float[] generateQueryVector(String queryText) {
        List<String> texts = new ArrayList<>();
        texts.add(queryText);
        List<float[]> embeddings = generateEmbeddings(texts);

        if (embeddings != null && !embeddings.isEmpty()) {
            return embeddings.getFirst();
        }
        throw new IllegalArgumentException("Failed to generate query vector");
    }

    @Override
    public SummarizationResponse summarizeText(String query, List<String> relevantChunks) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", query);
        requestBody.put("chunks", relevantChunks);
        requestBody.put("max_tokens", 1024);  // Configurable based on Gemini's constraints
        requestBody.put("top_k", 5);          // Number of chunks to use for summary

        log.debug("Sending request to Python server for summarizing text with query: {}", query);
        ResponseEntity<Map> response = restTemplate.postForEntity(
                SUMMARIZE_ENDPOINT,
                requestBody,
                Map.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            SummarizationResponse summarizationResponse = new SummarizationResponse();
            summarizationResponse.setSummary((String) response.getBody().get("summary"));

            Object rankedChunksObj = response.getBody().get("ranked_chunks");
            if (rankedChunksObj instanceof List<?> rankedChunksList) {
                List<String> rankedChunks = rankedChunksList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
                summarizationResponse.setRankedChunks(rankedChunks);
                log.debug("Ranked chunks used for summary: {}", rankedChunks);
            }

            Object chunkScoresObj = response.getBody().get("chunk_scores");
            if (chunkScoresObj instanceof List<?> chunkScoresList) {
                List<Float> chunkScores = chunkScoresList.stream()
                        .filter(Number.class::isInstance)
                        .map(num -> ((Number) num).floatValue())
                        .toList();
                summarizationResponse.setChunkScores(chunkScores);
                log.debug("Chunk relevance scores: {}", chunkScores);
            }

            return summarizationResponse;
        } else {
            throw new RuntimeException("Failed to summarize text");
        }
    }

    @Override
    public InitialSummarizationResponse summarizeInitialChunks(List<String> initialChunks) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("chunks", initialChunks);
        requestBody.put("max_tokens", 1024);
        requestBody.put("chunk_count", 5);    // Number of representative chunks to use

        log.debug("Sending request to Python server for initial document summarization");
        ResponseEntity<Map> response = restTemplate.postForEntity(
                INITIAL_SUMMARY_ENDPOINT,
                requestBody,
                Map.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            InitialSummarizationResponse summaryResponse = new InitialSummarizationResponse();

            summaryResponse.setOverviewSummary((String) response.getBody().get("overview_summary"));

            Object keyTopicsObj = response.getBody().get("key_topics");
            if (keyTopicsObj instanceof List<?> keyTopicsList) {
                List<String> keyTopics = keyTopicsList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
                summaryResponse.setKeyTopics(keyTopics);
            }

            Object selectedChunksObj = response.getBody().get("selected_chunks");
            if (selectedChunksObj instanceof List<?> selectedChunksList) {
                List<String> selectedChunks = selectedChunksList.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList();
                summaryResponse.setSelectedChunks(selectedChunks);
            }

            log.debug("Generated initial summary with {} key topics",
                    summaryResponse.getKeyTopics() != null ? summaryResponse.getKeyTopics().size() : 0);

            return summaryResponse;
        } else {
            throw new RuntimeException("Failed to generate initial summary");
        }
    }

    // Helper classes for embedding requests/responses
    @Data
    private static class EmbeddingRequest {
        private final List<String> texts;
    }

    @Data
    private static class EmbeddingResponse {
        private List<float[]> embeddings;
    }
}