package com.docqry.backend.services;

import com.docqry.backend.exceptions.LLMCommunicationException;
import org.springframework.stereotype.Service;

@Service
public interface LLMService {
    String getLLMResponse(String prompt) throws LLMCommunicationException;
    default String sanitizePrompt(String prompt) {
        if (prompt == null) return "";
        // Remove control characters
        return prompt.replaceAll("\\p{Cntrl}", "").trim();
    }
}
