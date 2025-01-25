package com.docqry.backend.services;

import com.docqry.backend.entities.Document;
import com.docqry.backend.entities.DocumentChunk;
import com.docqry.backend.repositories.DocumentChunkRepository;
import com.docqry.backend.repositories.DocumentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import org.hibernate.StaleObjectStateException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private static final String uploadUrl = "uploads/";

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final PythonServiceClientImpl pythonServiceClient;
    private final QdrantService qdrantService;
    private final DocumentChunkService documentChunkService;

    public Document getDocument(String id) {
        return documentRepository.findById(id).orElseThrow(() -> new RuntimeException("Couldn't find document'"));
    }

    public List<Document> listDocuments() {
        var documents = documentRepository.findAll();
        if (documents.isEmpty()) {
            return Collections.emptyList();
        }
        return documents;
    }


    @Transactional
    public Document uploadDocument(MultipartFile file) throws IOException {
        try {
            // Save file locally
            String filePath = saveFileLocally(file);
            System.out.println(filePath);

            // Create and save Document entity
            Document doc = Document.builder()
                    .name(file.getOriginalFilename())
                    .filePath(filePath)
                    .uploadedAt(LocalDateTime.now())
                    .version(0L)
                    .build();

            // Save the Document entity and get the managed entity with an ID
            doc = documentRepository.save(doc);

            // Split text into chunks
            String content = new String(file.getBytes());


            List<String> chunks = splitTextIntoSemanticChunks(content, 512, 80);

            // Generate embeddings for all chunks in a batch
            List<float[]> embeddings = pythonServiceClient.generateEmbeddings(chunks);

            // Ensure chunks and embeddings are aligned
            if (chunks.size() != embeddings.size()) {
                throw new RuntimeException("Chunks and embeddings are not aligned: mismatched sizes");
            }

//            // Prepare chunk IDs
//            List<String> chunkIds = new ArrayList<>();
//            for (int i = 0; i < chunks.size(); i++) {
//                chunkIds.add(UUID.randomUUID().toString());
//            }

            // Save chunk metadata and store embeddings in Qdrant
            saveDocumentChunksAndEmbeddings(chunks, embeddings, doc);

            return doc;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Retryable(
            value = {StaleObjectStateException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000)
    )
    @Transactional
    public void saveDocumentChunksAndEmbeddings(List<String> chunks, List<float[]> embeddings, Document doc) {
        // Prepare chunks with metadata
        List<DocumentChunk> documentChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk documentChunk = DocumentChunk.builder()
                    .document(doc)
                    .chunkText(chunks.get(i))
                    .version(0L)
                    .chunkIndex(i)  // Add index metadata
                    .build();
            documentChunks.add(documentChunk);
        }

        // Batch save chunks to the database
        List<DocumentChunk> savedChunks = documentChunkRepository.saveAll(documentChunks);

        // Collect chunk IDs
        List<String> chunkIds = savedChunks.stream()
                .map(DocumentChunk::getId)
                .toList();

        // Store embeddings in Qdrant
        try {
            qdrantService.storeEmbeddings("documents", embeddings, doc.getId(), chunkIds);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            // Handle Qdrant errors gracefully (e.g., rollback changes if needed)
        }
    }


    private String saveFileLocally(MultipartFile file) throws IOException {
        Path dir = Paths.get(uploadUrl);
        if (!Files.exists(dir)) Files.createDirectories(dir);

        String filePath = uploadUrl + UUID.randomUUID() + "_" + file.getOriginalFilename();
        Files.write(Paths.get(filePath), file.getBytes());
        return filePath;
    }

    private List<String> splitTextIntoSemanticChunks(String text, int chunkSize, int    overlap) {
        try (InputStream modelIn = new FileInputStream("./models/en-sent.bin")) {
            SentenceModel model = new SentenceModel(modelIn);
            SentenceDetectorME detector = new SentenceDetectorME(model);
            String[] sentences = detector.sentDetect(text);

            List<String> chunks = new ArrayList<>();
            StringBuilder chunk = new StringBuilder();

            for (int i = 0; i < sentences.length; i++) {
                if (chunk.length() + sentences[i].length() > chunkSize) {
                    chunks.add(chunk.toString());
                    chunk = new StringBuilder();

                    // Add overlap by re-including the last few sentences
                    for (int j = Math.max(0, i - overlap); j < i; j++) {
                        chunk.append(sentences[j]).append(" ");
                    }
                }
                chunk.append(sentences[i]).append(" ");
            }
            if (!chunk.isEmpty()) {
                chunks.add(chunk.toString());
            }
            return chunks;

        } catch (IOException e) {
            throw new RuntimeException("Error loading sentence detection model", e);
        }
    }

    @Transactional
    public void deleteDocumentById(String id) throws Exception {
        var doc = documentRepository.findById(id).orElseThrow(()-> new RuntimeException("No such document found for id " + id));
        // Delete the document in the local uploads folder
        Files.deleteIfExists(Paths.get(doc.getFilePath()));

        // Delete the related chunks and their respective vector embeddings in the Qdrant
        documentChunkService.deleteRelatedChunksAndVectorEmbeddingsByDocId(id);

        // Delete the document metadata in DB
        documentRepository.deleteById(id);
    }
}