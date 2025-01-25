package com.docqry.backend.controllers;

import com.docqry.backend.services.ContextManagerService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SessionController {

    private final ContextManagerService contextManagerService;
    Logger log = LoggerFactory.getLogger(SessionController.class);

    @PostMapping("/select-document/{documentId}")
    public ResponseEntity<Map<String, String>> selectDocument(@PathVariable String documentId) {
        try {
            // Set the active document ID
            contextManagerService.setActiveDocumentId(documentId);

            // Initialize the context with the first few chunks from the selected document
            contextManagerService.initializeContext(documentId);

            // Return a success response
            return ResponseEntity.ok(Map.of("status", "success", "activeDocument",contextManagerService.getActiveDocumentId(),"message", "Document selected and context initialized"));

        } catch (Exception ex) {
            log.error("Error initializing context: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to initialize context"));
        }
    }

    @GetMapping("/current-context")
    public String getContext() {
        var context = contextManagerService.getContext();
        if(context == null) {
            return "No context available";
        }else{
            return context;
        }
    }
}
