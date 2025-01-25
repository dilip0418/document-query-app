package com.docqry.backend.services;

import com.docqry.backend.config.QdrantConfig;
import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.WithVectorsSelectorFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointStruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.QueryFactory.nearest;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

@Service
@RequiredArgsConstructor
public class QdrantService {

    private final QdrantClient qdrantClient;
    private final RestTemplate restTemplate;


    private final PythonServiceClient pythonServiceClient;

    private final QdrantConfig qdrantConfig;

    Logger log = LoggerFactory.getLogger(QdrantService.class);

    @Autowired
    public QdrantService(RestTemplate restTemplate, PythonServiceClient pythonServiceClient, QdrantConfig qdrantConfig) {
        this.restTemplate = restTemplate;
        this.pythonServiceClient = pythonServiceClient;
        this.qdrantConfig = qdrantConfig;
        this.qdrantClient = new QdrantClient(QdrantGrpcClient.newBuilder("qdrant", 6334, false).build());
    }

    /**
     * Stores an embedding in Qdrant along with metadata.
     *
     * @param collectionName The name of the Qdrant collection.
     * @param embeddings     The list of embedding vectors to store.
     * @param docId          The ID of the document the chunk belongs to.
     * @param chunkIds       The IDs of specific chunks.
     */
    public void storeEmbeddings(String collectionName, List<float[]> embeddings, String docId, List<String> chunkIds)
            throws ExecutionException, InterruptedException {

        System.out.println(qdrantConfig.getQdrantUrl());
        // Check and create collection if necessary
        if (!qdrantClient.collectionExistsAsync(collectionName).get()) {
            System.out.println();
            int vectorDimension = embeddings.get(0).length;
            createNewCollection(collectionName, vectorDimension);
        }

        // Prepare point structures
        List<PointStruct> points = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            PointStruct point = PointStruct.newBuilder()
                    .setId(id(UUID.fromString(chunkIds.get(i))))
                    .setVectors(vectors(toFloatList(embeddings.get(i))))
                    .putAllPayload(Map.of(
                            "docId", value(docId),
                            "chunkId", value(chunkIds.get(i)),
                            "chunkIndex", value(i)  // Adding chunk index metadata
                    ))
                    .build();
            points.add(point);
        }
        // Batch upsert points to Qdrant
        qdrantClient.upsertAsync(collectionName, points).get();
    }

    /**
     * Retrieves the most similar embeddings from a specified Qdrant collection based on a query vector.
     * This method performs a nearest neighbor search in the vector space of the specified collection,
     * returning the top K most similar points to the given query vector. It includes both the vector
     * data and payload information in the results.
     *
     * @param collectionName The name of the Qdrant collection to search in. Must not be null or empty.
     * @param queryVector    The query vector to use for similarity search. Must not be null or empty.
     * @param topK           The number of top similar points to retrieve. Must be greater than 0.
     * @return A list of ScoredPoint objects representing the most similar points found,
     * ordered by similarity (highest similarity first). Each ScoredPoint includes
     * the point's ID, similarity score, payload, and vector data.
     * @throws ExecutionException       If an execution exception occurs during the asynchronous operation.
     * @throws InterruptedException     If the operation is interrupted.
     * @throws IllegalArgumentException If any of the input parameters are invalid.
     */
    public List<Points.ScoredPoint> retrieveEmbeddings(
            String collectionName,
            float[] queryVector,
            int topK) throws ExecutionException, InterruptedException {

        // Validate inputs
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty.");
        }
        if (queryVector == null || queryVector.length == 0) {
            throw new IllegalArgumentException("Query vector cannot be null or empty.");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("TopK must be greater than 0.");
        }

        // Convert query vector to List<Float>
        List<Float> queryVectorList = toFloatList(queryVector);

        // Build the query
        Points.QueryPoints query = Points.QueryPoints.newBuilder()
                .setCollectionName(collectionName)
                .setLimit(topK)
                .setQuery(nearest(queryVectorList))
                .setWithPayload(WithPayloadSelectorFactory.enable(true)) // Include payloads
                .setWithVectors(WithVectorsSelectorFactory.enable(true)) // Include vectors
                .build();

        // Log query details
        log.info("Querying Qdrant collection: {}", collectionName);
        log.debug("Query vector: {}", queryVectorList);
        log.debug("TopK: {}", topK);

        // Execute the query
        List<Points.ScoredPoint> results = qdrantClient.queryAsync(query).get();

        // Handle empty results
        if (results.isEmpty()) {
            log.warn("No results found for the given query.");
        } else {
            log.info("Retrieved {} results.", results.size());
        }
        return results;
    }


    /**
     * Creates a new collection in Qdrant with the specified name and vector dimension.
     * This method is used to initialize a new vector space for storing embeddings.
     *
     * @param collectionName  The name of the collection to be created. This should be a unique
     *                        identifier for the collection within the Qdrant instance.
     * @param vectorDimension The dimension of the vectors to be stored in this collection.
     *                        This should match the size of the embedding vectors that will
     *                        be inserted into the collection.
     * @throws ExecutionException   If an error occurs during the execution of the asynchronous operation.
     * @throws InterruptedException If the thread is interrupted while waiting for the operation to complete.
     */
    private void createNewCollection(String collectionName, int vectorDimension) {
        try {
            // First check if collection exists
            boolean exists = qdrantClient.collectionExistsAsync(collectionName)
                    .get(30, TimeUnit.SECONDS); // Add timeout

            if (!exists) {
                System.out.println("Collection " + collectionName + " does not exist. Creating new collection...");

                Collections.VectorParams params = Collections.VectorParams.newBuilder()
                        .setDistance(Collections.Distance.Dot)
                        .setSize(vectorDimension)
                        .build();

                // Create collection with explicit error handling
                Collections.CollectionOperationResponse created = qdrantClient.createCollectionAsync(collectionName, params)
                        .get(30, TimeUnit.SECONDS);

                if (created.getResult()) {
                    System.out.println("Collection created successfully");
                } else {
                    System.out.println("Failed to create collection");
                }
            } else {
                System.out.println("Collection " + collectionName + " already exists");
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println("Error creating/checking collection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void verifyEmbeddings(String collectionName, List<String> chunkIds) throws InterruptedException {
        for (String chunkId : chunkIds) {
            // Build a search request to retrieve the point by its ID
            Points.SearchPoints searchRequest = Points.SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setFilter(Points.Filter.newBuilder()
                            .addMust(Points.Condition.newBuilder()
                                    .setField(Points.FieldCondition.newBuilder()
                                            .setKey("chunkId")
                                            .setMatch(Points.Match.newBuilder()
                                                    .setKeyword(chunkId)
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                    .setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(true).build())
                    .setLimit(1) // Fetch only one point
                    .build();

            // Execute the search request
            ListenableFuture<List<Points.ScoredPoint>> searchResponse = qdrantClient.searchAsync(searchRequest);

            // Verify that the point exists
            if (searchResponse.resultNow().isEmpty()) {
                System.err.println("Point with chunkId " + chunkId + " not found in collection " + collectionName);
            } else {
                System.out.println("Point with chunkId " + chunkId + " found in collection " + collectionName);
            }
        }
    }

    private List<Float> toFloatList(float[] array) {
        return IntStream.range(0, array.length)
                .mapToObj(i -> array[i])
                .toList();
    }

    /**
     * @param queryText Users query text string
     * @param topK integer to specify the top most k matching chunk/embeddings
     * @return A list of ScoredPoint objects representing the most similar points found,
     *      * ordered by similarity (highest similarity first). Each ScoredPoint includes
     * @throws ExecutionException
     * @throws InterruptedException
     */
    public List<Points.ScoredPoint> getRelevantEmbeddings(
            String queryText,
            int topK
    ) throws ExecutionException, InterruptedException {
        // Generate query vector using Python service
        float[] queryVector = pythonServiceClient.generateQueryVector(queryText);

        // Retrieve embeddings from Qdrant
        return retrieveEmbeddings(qdrantConfig.getCollectionName(), queryVector, topK);
    }

    // Method to delete points from the Qdrant vector store based on a filter
    public void deleteVectorEmbeddingsByFilter(String collectionName, String filterField, String filterValue) throws ExecutionException, InterruptedException {
        // Create the filter condition (e.g., matching a specific keyword in the vector store)
        Points.Filter filter = Points.Filter.newBuilder().addMust(matchKeyword(filterField, filterValue)).build();

        // Perform the delete operation
        qdrantClient.deleteAsync(collectionName, filter).get();
    }

    public List<Map<String, Object>> prepareResponse(List<Points.ScoredPoint> searchResults) {
        List<Map<String, Object>> responseList = new ArrayList<>();
        for (Points.ScoredPoint point : searchResults) {
            Map<String, Object> result = new HashMap<>();

            // Extract payload data
            Map<String, JsonWithInt.Value> payload = point.getPayloadMap();
            String docId = cleanStringValue(String.valueOf(payload.getOrDefault("docId", value("unknown"))));
            String chunkId = cleanStringValue(String.valueOf(payload.getOrDefault("chunkId", value("unknown"))));

            // Extract vector data
            List<Float> vectorData = point.getVectors().getVector().getDataList();

            result.put("id", point.getId().getUuid());
            result.put("score", point.getScore());
            result.put("docId", docId);
            result.put("chunkId", chunkId);
            result.put("vectors", vectorData);

            responseList.add(result);
        }
        return responseList;
    }

    // Helper method to process query results
    public Map<String, Object> processQueryResponse(List<Points.ScoredPoint> searchResults) {
        List<Map<String, Object>> responseList = new ArrayList<>();

        for (Points.ScoredPoint point : searchResults) {
            Map<String, Object> result = new HashMap<>();

            // Extract payload data from the ScoredPoint
            Map<String, JsonWithInt.Value> payload = point.getPayloadMap();
            String docId = cleanStringValue(String.valueOf(payload.getOrDefault("docId", value("unknown"))));
            String chunkId = cleanStringValue(String.valueOf(payload.getOrDefault("chunkId", value("unknown"))));

            // Extract vector data (ensuring it's a simple list of floats)
            List<Float> vectorData = new ArrayList<>(point.getVectors().getVector().getDataList());

            // Prepare the result map with only serializable data
            result.put("id", point.getId().getUuid());
            result.put("score", point.getScore());
            result.put("docId", docId);
            result.put("chunkId", chunkId);
            result.put("vectors", vectorData);

            // Add the result to the response list
            responseList.add(result);
        }

        // Return the prepared response
        Map<String, Object> response = new HashMap<>();
        response.put("results", responseList);
        return response;
    }

    // Helper method to clean up string values
    private String cleanStringValue(String rawValue) {
        // Remove "string_value: " and surrounding quotes
        if (rawValue.startsWith("string_value: ")) {
            return rawValue.replace("string_value: ", "").replace("\"", "").trim();
        }
        return rawValue;
    }
}
