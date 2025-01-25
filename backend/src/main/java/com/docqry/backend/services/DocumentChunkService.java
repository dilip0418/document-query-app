package com.docqry.backend.services;

import com.docqry.backend.entities.DocumentChunk;
import com.docqry.backend.repositories.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentChunkService {
    Logger log = LoggerFactory.getLogger(DocumentChunkService.class);
    private final DocumentChunkRepository documentChunkRepository;
    private final QdrantService qdrantService;

    public List<String> getDocumentChunks(String documentId){
        var documentChunks = documentChunkRepository.findByDocumentId(documentId);
        return getChunkTexts(documentChunks);
    }

    public List<String> getDocumentChunks(String documentId, List<String> chunkIds) {
        var documentChunks = documentChunkRepository.findByDocumentIdAndIdIn(documentId, chunkIds);
        if(documentChunks.isEmpty()){
            log.warn("No document chunks found for document");
            return Collections.emptyList();
        }else{
            log.info(" {} Relevant document chunks found for document", documentChunks.size());
            return getChunkTexts(documentChunks);
        }
    }

    private List<String> getChunkTexts(List<DocumentChunk> documentChunks){
        return documentChunks.stream()
                .map(DocumentChunk::getChunkText)
                .toList();
    }

    public void deleteRelatedChunksAndVectorEmbeddingsByDocId(String id) throws Exception {
        // Delete the relevant vector embeddings from Qdrant Vector store
        qdrantService.deleteVectorEmbeddingsByFilter("documents","docId", id);

        // Delete the relevant chunks from DB
        documentChunkRepository.deleteByDocumentId(id);
    }
}
