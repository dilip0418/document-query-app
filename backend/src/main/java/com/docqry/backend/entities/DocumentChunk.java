package com.docqry.backend.entities;

import jakarta.persistence.*;
import lombok.*;
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "doc_id", nullable = false)
    private Document document;

    @Column(length = 10000)
    private String chunkText;

    private Integer chunkIndex;

    @Version
    @Builder.Default
    private Long version = 0L; // Initialize version to 0
}