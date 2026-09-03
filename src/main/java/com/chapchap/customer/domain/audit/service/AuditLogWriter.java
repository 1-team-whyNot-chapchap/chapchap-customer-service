package com.chapchap.customer.domain.audit.service;

import com.chapchap.customer.domain.audit.entity.AuditActionType;
import com.chapchap.customer.domain.audit.entity.AuditActorType;
import com.chapchap.customer.domain.audit.entity.AuditLog;
import com.chapchap.customer.domain.audit.repository.AuditLogRepository;
import com.chapchap.customer.domain.faq.entity.Faq;
import com.chapchap.customer.domain.knowledge.entity.KnowledgeVersion;
import com.chapchap.customer.domain.consultation.entity.Consultation;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogWriter {
    private static final String TRACE_ID_KEY = "traceId";

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFaqCreated(Long actorUserId, Faq faq, LocalDateTime now) {
        auditLogRepository.save(AuditLog.faqChange(
                actorUserId,
                AuditActionType.FAQ_CREATED,
                String.valueOf(faq.getId()),
                MDC.get(TRACE_ID_KEY),
                changeDetail(null, faq),
                now
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFaqUpdated(Long actorUserId, Faq before, Faq after, LocalDateTime now) {
        auditLogRepository.save(AuditLog.faqChange(
                actorUserId,
                AuditActionType.FAQ_UPDATED,
                String.valueOf(after.getId()),
                MDC.get(TRACE_ID_KEY),
                changeDetail(before, after),
                now
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordFaqDeactivated(Long actorUserId, Faq before, Faq after, LocalDateTime now) {
        auditLogRepository.save(AuditLog.faqChange(
                actorUserId,
                AuditActionType.FAQ_DEACTIVATED,
                String.valueOf(after.getId()),
                MDC.get(TRACE_ID_KEY),
                changeDetail(before, after),
                now
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordConsultationEscalated(Long actorUserId, Consultation consultation, String beforeStatus, LocalDateTime now) {
        auditLogRepository.save(AuditLog.consultationChange(actorUserId, AuditActionType.CONSULTATION_ESCALATED,
                String.valueOf(consultation.getId()), MDC.get(TRACE_ID_KEY), AuditActorType.USER,
                consultationDetail(beforeStatus, consultation), now));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordConsultationAccepted(Long actorUserId, Consultation consultation, String beforeStatus, LocalDateTime now) {
        auditLogRepository.save(AuditLog.consultationChange(actorUserId, AuditActionType.CONSULTATION_ACCEPTED,
                String.valueOf(consultation.getId()), MDC.get(TRACE_ID_KEY), AuditActorType.ADMIN,
                consultationDetail(beforeStatus, consultation), now));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordKnowledgeVersionRegistered(Long actorUserId, KnowledgeVersion knowledgeVersion, LocalDateTime now) {
        auditLogRepository.save(AuditLog.knowledgeVersionChange(
                actorUserId,
                AuditActorType.ADMIN,
                AuditActionType.KNOWLEDGE_VERSION_REGISTERED,
                String.valueOf(knowledgeVersion.getId()),
                MDC.get(TRACE_ID_KEY),
                knowledgeDetail(knowledgeVersion),
                now
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordKnowledgeVersionActivated(KnowledgeVersion knowledgeVersion, LocalDateTime now) {
        auditLogRepository.save(AuditLog.knowledgeVersionChange(
                null,
                AuditActorType.SYSTEM,
                AuditActionType.KNOWLEDGE_VERSION_ACTIVATED,
                String.valueOf(knowledgeVersion.getId()),
                MDC.get(TRACE_ID_KEY),
                knowledgeDetail(knowledgeVersion),
                now
        ));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordKnowledgeProcessingFailed(KnowledgeVersion knowledgeVersion, LocalDateTime now) {
        auditLogRepository.save(AuditLog.knowledgeVersionChange(
                null,
                AuditActorType.SYSTEM,
                AuditActionType.KNOWLEDGE_PROCESSING_FAILED,
                String.valueOf(knowledgeVersion.getId()),
                MDC.get(TRACE_ID_KEY),
                knowledgeDetail(knowledgeVersion),
                now
        ));
    }

    private Map<String, Object> changeDetail(Faq before, Faq after) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before == null ? null : snapshot(before));
        detail.put("after", snapshot(after));
        return detail;
    }

    private Map<String, Object> snapshot(Faq faq) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("category", faq.getCategory());
        snapshot.put("question", faq.getQuestion());
        snapshot.put("answer", faq.getAnswer());
        snapshot.put("displayOrder", faq.getDisplayOrder());
        snapshot.put("published", faq.isPublished());
        snapshot.put("active", faq.isActive());
        return snapshot;
    }

    private Map<String, Object> consultationDetail(String beforeStatus, Consultation consultation) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", Map.of("status", beforeStatus));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", consultation.getStatus().name());
        after.put("assignedAdminId", consultation.getAssignedAdminId());
        after.put("escalatedAt", consultation.getEscalatedAt());
        after.put("assignedAt", consultation.getAssignedAt());
        detail.put("after", after);
        return detail;
    }

    private Map<String, Object> knowledgeDetail(KnowledgeVersion knowledgeVersion) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("knowledgeDocumentId", knowledgeVersion.getKnowledgeDocumentId());
        detail.put("version", knowledgeVersion.getVersion());
        detail.put("processingStatus", knowledgeVersion.getProcessingStatus().name());
        detail.put("processingAttemptCount", knowledgeVersion.getProcessingAttemptCount());
        detail.put("failureCode", knowledgeVersion.getFailureCode());
        detail.put("retryable", knowledgeVersion.isRetryable());
        return detail;
    }
}
