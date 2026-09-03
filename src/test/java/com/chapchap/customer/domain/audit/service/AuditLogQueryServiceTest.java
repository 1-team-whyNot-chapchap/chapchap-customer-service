package com.chapchap.customer.domain.audit.service;

import com.chapchap.customer.domain.audit.entity.AuditActionType;
import com.chapchap.customer.domain.audit.entity.AuditLog;
import com.chapchap.customer.domain.audit.repository.AuditLogRepository;
import com.chapchap.customer.domain.audit.request.AuditLogSearchRequest;
import com.chapchap.customer.global.error.custom.audit.AuditLogQueryValidationException;
import com.chapchap.customer.global.security.constant.RolePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogQueryServiceTest {
    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogQueryService auditLogQueryService;

    @BeforeEach
    void setUp() {
        auditLogQueryService = new AuditLogQueryService(auditLogRepository);
    }

    @Test
    void returnsNewestAuditLogsUsingDefaultPageRequest() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 3, 10, 30);
        AuditLog auditLog = AuditLog.faqChange(
                7L,
                AuditActionType.FAQ_UPDATED,
                "19",
                "trace-19",
                Map.of("before", Map.of("published", true), "after", Map.of("published", false)),
                createdAt
        );
        when(auditLogRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(auditLog)));

        var response = auditLogQueryService.search(emptyRequest(), RolePolicy.SUPER_ADMIN);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAll(anySpecification(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
        assertThat(response.content()).singleElement().satisfies(log -> {
            assertThat(log.actionType()).isEqualTo(AuditActionType.FAQ_UPDATED);
            assertThat(log.targetId()).isEqualTo("19");
            assertThat(log.detail()).containsKey("before");
            assertThat(log.createdAt()).isEqualTo(createdAt);
        });
    }

    @Test
    void rejectsReversedSearchPeriod() {
        AuditLogSearchRequest request = new AuditLogSearchRequest(
                null, null, null, null, null, null,
                LocalDateTime.of(2026, 9, 3, 12, 0),
                LocalDateTime.of(2026, 9, 3, 11, 0),
                null, null
        );

        assertThatThrownBy(() -> auditLogQueryService.search(request, RolePolicy.SUPER_ADMIN))
                .isInstanceOf(AuditLogQueryValidationException.class);
    }

    @Test
    void rejectsNonOperatorRoleBeforeRunningQuery() {
        assertThatThrownBy(() -> auditLogQueryService.search(emptyRequest(), RolePolicy.CUSTOMER))
                .isInstanceOf(AccessDeniedException.class);
    }

    private AuditLogSearchRequest emptyRequest() {
        return new AuditLogSearchRequest(null, null, null, null, null, null, null, null, null, null);
    }

    private static Specification<AuditLog> anySpecification() {
        return org.mockito.ArgumentMatchers.any();
    }
}
