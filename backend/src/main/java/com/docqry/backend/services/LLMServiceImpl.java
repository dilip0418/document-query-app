package com.docqry.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService {
    @Override
    public String getLLMResponse(String prompt) {
        return "";
    }
}
