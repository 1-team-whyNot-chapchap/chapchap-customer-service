package com.chapchap.customer.domain.audit.service;

import com.chapchap.customer.domain.audit.entity.AuditActionType;
import com.chapchap.customer.domain.audit.entity.AuditLog;
import com.chapchap.customer.domain.audit.repository.AuditLogRepository;
import com.chapchap.customer.domain.faq.entity.Faq;
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
}
