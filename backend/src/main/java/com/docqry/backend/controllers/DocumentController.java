package com.docqry.backend.controllers;

import com.docqry.backend.entities.Document;
import com.docqry.backend.services.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DocumentController {
    @Autowired
    private DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Document> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            Document document = documentService.uploadDocument(file);
            return ResponseEntity.ok(document);
        } catch (IOException e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<Document>> listDocuments() {
        var documents = documentService.listDocuments();
        if (documents != null) {
            return ResponseEntity.ok(documents);
        }else{
            return ResponseEntity.status(204).build();
        }
    }


    @DeleteMapping("/{documentId}")
    public ResponseEntity<Map<String, String>> deleteDocumentById(@PathVariable String documentId){
        try{
            documentService.deleteDocumentById(documentId);
            return ResponseEntity.ok().build();
        }catch(Exception e){
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public String Hello() {
        return "Hello";
    }
}
