package com.docqry.backend.services;

import com.docqry.backend.entities.EmbeddingRequest;
import com.docqry.backend.exceptions.LLMCommunicationException;
import com.docqry.backend.repositories.DocumentChunkRepository;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class ContextManagerService {

    private final ReentrantLock lock = new ReentrantLock();
    private final SummarizationService summarizationService;
    Logger log = LoggerFactory.getLogger(ContextManagerService.class);
    private final QdrantService qdrantService;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentChunkService documentChunkService;

    @Getter
    private String activeDocumentId;
    @Getter
    private String context = "";

    /**
     * Creating a table will be the extension if the app supports multiple users.
     * Then the user based usage can be stored in the database and the app can
     * restrict or serve the user based on the usage.
     */

    // Define the maximum number of historical contexts to retain
    private static final int CONTEXT_HISTORY_SIZE = 5;
    private final Deque<String> contextHistory = new ArrayDeque<>(CONTEXT_HISTORY_SIZE);

    // Token limits for the model
    private static final int MAX_INPUT_TOKENS = 1_048_576;
    /**
     * private static final int MAX_OUTPUT_TOKENS = 8_192; can be used if you need
     * to limit the number of output tokens and this can be passed to the LLM service
     * as an instruction.
     */

    // Rate limits for the model
    private static final int REQUESTS_PER_MINUTE = 15;
    private static final int REQUESTS_PER_DAY = 1_500;

    // Track usage for rate limiting
    private int requestsThisMinute = 0;
    private int requestsToday = 0;
    private long lastRequestTimestamp = System.currentTimeMillis();


    public void setActiveDocumentId(String activeDocumentId) {
        this.activeDocumentId = activeDocumentId;
        clearContext(); // Clear context when a new document is selected
    }


    public void appendToContext(String newQuery, List<String> relevantChunks) {
        lock.lock();
        try {
            // Check rate limits before proceeding
            checkRateLimits();

            // Summarize the current query and relevant chunks to retrieve the context for current user query
            String currentContext = summarizationService.summarizeContext(newQuery, relevantChunks);

            // Add the current context to the history
            if (contextHistory.size() >= CONTEXT_HISTORY_SIZE) {
                contextHistory.poll(); // Remove the oldest context if the history is full
            }
            contextHistory.offer(currentContext);

            // Combine the historical context with the current context
            this.context = combineContexts(currentContext);

            // Ensure the context does not exceed the token limit
            truncateContextIfNeeded();
        } catch (Exception e) {
            log.error("Failed to append to context", e);
        } finally {
            lock.unlock();
        }
    }

    private void checkRateLimits() {
        long currentTime = System.currentTimeMillis();

        // Reset the minute counter if a minute has passed
        if (currentTime - lastRequestTimestamp >= 60_000) {
            requestsThisMinute = 0;
            lastRequestTimestamp = currentTime;
        }

        // Check if the request exceeds the rate limits
        if (requestsThisMinute >= REQUESTS_PER_MINUTE) {
            throw new RuntimeException("Rate limit exceeded: Too many requests per minute.");
        }
        if (requestsToday >= REQUESTS_PER_DAY) {
            throw new RuntimeException("Rate limit exceeded: Too many requests today.");
        }

        // Increment the request counters
        requestsThisMinute++;
        requestsToday++;
    }

    private String combineContexts(String currentContext) {
        StringBuilder combinedContext = new StringBuilder();

        // Append historical contexts
        for (String historicalContext : contextHistory) {
            combinedContext.append(historicalContext).append("\n");
        }

        // Append the current context
        combinedContext.append(currentContext);

        return combinedContext.toString();
    }

    private void truncateContextIfNeeded() {
        // Estimate the token count (assuming 1 token ≈ 4 characters)
        int tokenCount = calculateTokenCount(context);

        if (tokenCount > MAX_INPUT_TOKENS) {
            log.warn("Context exceeds token limit. Truncating oldest parts recursively...");
            truncateOldestContextRecursively();
        }
    }

    private void truncateOldestContextRecursively() {
        // Calculate the total token count
        int tokenCount = calculateTokenCount(context);

        // Base case: If the token count is within limits, stop recursion
        if (tokenCount <= MAX_INPUT_TOKENS) {
            return;
        }

        // If the context history is empty, truncate the main context directly
        if (contextHistory.isEmpty()) {
            int maxAllowedLength = MAX_INPUT_TOKENS * 4;
            context = context.substring(context.length() - maxAllowedLength);
            log.warn("Context truncated to stay within token limits.");
            return;
        }

        // Remove the oldest context from the history
        String oldestContext = contextHistory.poll();

        // Remove the oldest context partially from the context history
        if (calculateTokenCount(context) - calculateTokenCount(oldestContext) < MAX_INPUT_TOKENS) {
            // Remove part of the oldest context
            int excessTokens = tokenCount - MAX_INPUT_TOKENS;
            int tokensToRemove = Math.min(excessTokens, calculateTokenCount(oldestContext));
            int charsToRemove = tokensToRemove * 4; // Assuming 1 token ≈ 4 characters

            // Remove the excess characters from the oldest context
            String truncatedOldestContext = oldestContext.substring(charsToRemove);

            // Add the truncated context back to the history (if it's still meaningful)
            if (!truncatedOldestContext.trim().isEmpty()) {
                contextHistory.offerFirst(truncatedOldestContext);
            }
        }

        // Rebuild the context from the updated history
        context = combineContexts("");

        // Recursively check and truncate further if needed
        truncateOldestContextRecursively();
    }

    private int calculateTokenCount(String text) {
        // Estimate token count (1 token ≈ 4 characters)
        return text.length() / 4;
    }

    public void clearContext() {
        lock.lock();
        try {
            context = "";
            contextHistory.clear(); // Clear the history as well
        } finally {
            lock.unlock();
        }
    }

    public void initializeContext(String documentId) {
        // Get the first few chunks of the document (you can define how many you want)
        List<String> initialChunks = getInitialChunksFromDocument(documentId);

        // Summarize the chunks using the summarization service
        this.context = summarizationService.summarizeInitialChunks(initialChunks);
    }

    @Transactional
    public List<String> getInitialChunksFromDocument(String documentId) {
        var initialChunks = documentChunkService.getDocumentChunks(documentId);

        if (initialChunks.isEmpty()) {
            log.warn("No initial chunks found");
            return Collections.emptyList();
        } else {
            return initialChunks.stream()
                    .limit(Math.min(6, initialChunks.size()))
                    .toList();
        }
    }

    public void buildContext(EmbeddingRequest payload)
            throws
            LLMCommunicationException,
            ExecutionException,
            InterruptedException {

        var queryText = payload.getQueryText();
        var topK = payload.getLimit();

        // Retrieve relevant vector embeddings from Qdrant
        var searchResults = qdrantService.getRelevantEmbeddings(queryText, topK);

        if (searchResults.isEmpty()) {
            log.warn("No results found for query: {}", queryText);
            throw new LLMCommunicationException("No results found for query: "+queryText);
        }
        // Prepare the response with the results and  Get the results list from the processed search results
        Map<String, Object> processedSearchResults = qdrantService.processQueryResponse(searchResults);
        var results = (List<Map<String, Object>>) processedSearchResults.get("results");

        if (results.isEmpty()) {
            throw new LLMCommunicationException("Query out of context");
        }
        // Get the documentId from the results since it's the same for each item get the first result & Get the chunkIds from the results
        var documentId = (String) results.getFirst().get("docId");
        var chunkIds = results.stream()
                .map(result -> (String) result.get("chunkId"))
                .toList();

        // Retrieve the respective chunkText from PG database
        var relevantChunks = documentChunkService.getDocumentChunks(documentId, chunkIds);

        // reinitialize the context
        appendToContext(queryText, relevantChunks);
    }
}