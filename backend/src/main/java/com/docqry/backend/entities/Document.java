package com.docqry.backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;
    private String filePath;

    @Builder.Default
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Version
    @Builder.Default
    private Long version = 0L; // Initialize version to 0
}