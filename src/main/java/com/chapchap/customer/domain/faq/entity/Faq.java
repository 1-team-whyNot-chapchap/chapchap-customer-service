package com.chapchap.customer.domain.faq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "faqs")
public class Faq {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "faq_id")
    private Long id;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 500)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_published", nullable = false)
    private boolean published;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Faq() {
    }

    private Faq(
            String category,
            String question,
            String answer,
            int displayOrder,
            boolean published,
            Long createdByUserId,
            LocalDateTime now
    ) {
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.displayOrder = displayOrder;
        this.published = published;
        this.active = true;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Faq create(
            String category,
            String question,
            String answer,
            int displayOrder,
            boolean published,
            Long createdByUserId,
            LocalDateTime now
    ) {
        return new Faq(category, question, answer, displayOrder, published, createdByUserId, now);
    }

    public void update(
            String category,
            String question,
            String answer,
            int displayOrder,
            boolean published,
            Long updatedByUserId,
            LocalDateTime now
    ) {
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.displayOrder = displayOrder;
        this.published = published;
        this.updatedByUserId = updatedByUserId;
        this.updatedAt = now;
    }

    public boolean deactivate(Long updatedByUserId, LocalDateTime now) {
        if (!active) {
            return false;
        }

        active = false;
        this.updatedByUserId = updatedByUserId;
        this.updatedAt = now;
        return true;
    }

    public Faq snapshot() {
        Faq snapshot = new Faq(
                category,
                question,
                answer,
                displayOrder,
                published,
                createdByUserId,
                createdAt
        );
        snapshot.id = id;
        snapshot.active = active;
        snapshot.updatedByUserId = updatedByUserId;
        snapshot.updatedAt = updatedAt;
        return snapshot;
    }

    public Long getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isPublished() {
        return published;
    }

    public boolean isActive() {
        return active;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public Long getUpdatedByUserId() {
        return updatedByUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
