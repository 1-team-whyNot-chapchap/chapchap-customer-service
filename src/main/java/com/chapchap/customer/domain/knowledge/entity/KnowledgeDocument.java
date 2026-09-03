package com.chapchap.customer.domain.knowledge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "knowledge_documents",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_knowledge_documents_source_service_document_key",
                columnNames = {"source_service", "document_key"}
        )
)
public class KnowledgeDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "knowledge_document_id")
    private Long id;

    @Column(name = "document_key", nullable = false, length = 100)
    private String documentKey;

    @Column(name = "source_service", nullable = false, length = 50)
    private String sourceService;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected KnowledgeDocument() {
    }

    private KnowledgeDocument(
            String documentKey,
            String sourceService,
            String category,
            String title,
            LocalDateTime now
    ) {
        this.documentKey = documentKey;
        this.sourceService = sourceService;
        this.category = category;
        this.title = title;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static KnowledgeDocument create(
            String documentKey,
            String sourceService,
            String category,
            String title,
            LocalDateTime now
    ) {
        return new KnowledgeDocument(documentKey, sourceService, category, title, now);
    }

    public Long getId() {
        return id;
    }

    public String getDocumentKey() {
        return documentKey;
    }

    public String getSourceService() {
        return sourceService;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }
}
