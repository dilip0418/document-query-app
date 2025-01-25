package com.docqry.backend.entities;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmbeddingRequest {
    private String queryText;
    private int limit = 5;
}
