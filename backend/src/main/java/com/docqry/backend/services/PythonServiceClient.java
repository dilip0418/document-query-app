package com.docqry.backend.services;

import java.util.List;

public interface PythonServiceClient {
    /**
     * Generate embeddings for a list of texts
     * @param texts List of texts to generate embeddings for
     * @return List of float arrays representing the embeddings
     */
    List<float[]> generateEmbeddings(List<String> texts);

    /**
     * Generate embedding vector for a single query
     * @param queryText Query text to generate embedding for
     * @return float array representing the query embedding
     */
    float[] generateQueryVector(String queryText);

    /**
     * Generate a query-focused summary from relevant chunks
     * @param query The user's query
     * @param relevantChunks List of relevant text chunks
     * @return SummarizationResponse containing the summary and ranked chunks
     */
    PythonServiceClientImpl.SummarizationResponse summarizeText(String query, List<String> relevantChunks);

    /**
     * Generate an initial summary of the document
     * @param initialChunks List of initial document chunks
     * @return InitialSummarizationResponse containing overview, key topics, and selected chunks
     */
    PythonServiceClientImpl.InitialSummarizationResponse summarizeInitialChunks(List<String> initialChunks);
}