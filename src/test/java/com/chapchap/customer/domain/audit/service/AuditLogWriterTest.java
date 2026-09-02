package com.chapchap.customer.domain.audit.service;

import com.chapchap.customer.domain.audit.entity.AuditActionType;
import com.chapchap.customer.domain.audit.entity.AuditLog;
import com.chapchap.customer.domain.audit.repository.AuditLogRepository;
import com.chapchap.customer.domain.faq.entity.Faq;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogWriterTest {
    @Mock
    private AuditLogRepository auditLogRepository;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void recordsFaqDeactivationWithBeforeAfterStateAndTraceId() {
        AuditLogWriter auditLogWriter = new AuditLogWriter(auditLogRepository);
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 2, 12, 0);
        Faq before = Faq.create("DELIVERY", "배송은 언제 오나요?", "배송 일정에 따라 도착합니다.", 1, true, 1L, createdAt);
        Faq after = before.snapshot();
        after.deactivate(10L, createdAt.plusMinutes(1));
        MDC.put("traceId", "faq-trace-1");

        auditLogWriter.recordFaqDeactivated(10L, before, after, createdAt.plusMinutes(1));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog auditLog = captor.getValue();

        assertThat(auditLog.getActorUserId()).isEqualTo(10L);
        assertThat(auditLog.getActionType()).isEqualTo(AuditActionType.FAQ_DEACTIVATED);
        assertThat(auditLog.getTraceId()).isEqualTo("faq-trace-1");
        Map<?, ?> beforeDetail = (Map<?, ?>) auditLog.getDetail().get("before");
        Map<?, ?> afterDetail = (Map<?, ?>) auditLog.getDetail().get("after");
        assertThat(beforeDetail.get("active")).isEqualTo(true);
        assertThat(afterDetail.get("active")).isEqualTo(false);
    }
}
