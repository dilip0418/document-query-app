package com.docqry.backend.repositories;

import com.docqry.backend.entities.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, String> {

    List<DocumentChunk> findByDocumentId(String documentId);
    List<DocumentChunk> findByDocumentIdAndIdIn(String documentId, List<String> chunkIds);

    void deleteByDocumentId(String id);
}
