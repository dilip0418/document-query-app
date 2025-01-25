package com.docqry.backend.controllers;

import com.docqry.backend.services.DocumentChunkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chunks")
@RequiredArgsConstructor
public class ChunkController {
    private final DocumentChunkService documentChunkService;

    public ResponseEntity<Map<String, List<String>>> retrieveChunks(
            @RequestParam String documentId,
            @RequestBody List<String> chunkIds) {
        var chunks = documentChunkService.getDocumentChunks(documentId, chunkIds);
        return ResponseEntity.ok(Map.of("chunks", chunks));
    }
}
