package com.docqry.backend.services;

import com.docqry.backend.exceptions.NoContextAvailableException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromptManager {

    Logger log = LoggerFactory.getLogger(PromptManager.class);

    private final ContextManagerService contextManagerService;

    public String generateProcessedPrompt(String query) throws NoContextAvailableException {
        String context = contextManagerService.getContext();
        if (context == null || context.isEmpty()) {
            throw new NoContextAvailableException("No context available for generating the prompt.");
        }

        // Clean and deduplicate context
        context = cleanAndDeduplicateContext(context);

        // Zephyr works well with this format
        return String.format("""
        <|system|>
        You are a helpful AI assistant. Provide clear and accurate answers based only on the given context.
        
        <|context|>
        %s
        
        <|user|>
        %s
        
        <|assistant|>
        Let me help you with that based on the provided context.
        """,
                context,
                query
        );
    }

    private String cleanAndDeduplicateContext(String context) {
        // Split into sentences or chunks
        String[] chunks = context.split("\\. ");
        // Remove duplicates while maintaining order
        return Arrays.stream(chunks)
                .distinct()
                .filter(s -> !s.trim().isEmpty())
                .collect(Collectors.joining(". "))
                + ".";
    }

}
