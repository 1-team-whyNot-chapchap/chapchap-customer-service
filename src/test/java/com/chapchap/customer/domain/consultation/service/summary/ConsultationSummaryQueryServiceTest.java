package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.service.ConsultationService;
import com.chapchap.customer.domain.consultation.entity.Consultation;
import com.chapchap.customer.domain.consultation.entity.summary.ConsultationSummary;
import com.chapchap.customer.domain.consultation.entity.summary.ConsultationSummaryJob;
import com.chapchap.customer.domain.consultation.repository.ConsultationRepository;
import com.chapchap.customer.domain.consultation.repository.ConsultationMessageRepository;
import com.chapchap.customer.domain.consultation.repository.summary.ConsultationSummaryRepository;
import com.chapchap.customer.domain.consultation.repository.summary.ConsultationSummaryJobRepository;
import com.chapchap.customer.domain.audit.service.AuditLogWriter;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import com.chapchap.customer.global.security.constant.RolePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsultationSummaryQueryServiceTest {
    private final ConsultationRepository consultations = mock(ConsultationRepository.class);
    private final ConsultationSummaryRepository summaries = mock(ConsultationSummaryRepository.class);
    private final ConsultationSummaryJobRepository jobs = mock(ConsultationSummaryJobRepository.class);
    private final ConsultationService lifecycle = new ConsultationService(consultations,
            mock(ConsultationMessageRepository.class), mock(AuditLogWriter.class), mock(ApplicationEventPublisher.class));
    private final ConsultationSummaryQueryService query = new ConsultationSummaryQueryService(lifecycle, summaries, jobs);
    private final GatewayUserPrincipal admin = new GatewayUserPrincipal("11", RolePolicy.ADMIN);

    private void assignedConsultation() {
        Consultation consultation = Consultation.create(77L, LocalDateTime.now());
        ReflectionTestUtils.setField(consultation, "assignedAdminId", 11L);
        when(consultations.findById(501L)).thenReturn(Optional.of(consultation));
    }

    @Test
    void onlyAssignedAdminCanReadSummary() {
        assignedConsultation();
        when(summaries.findByConsultationId(501L)).thenReturn(Optional.of(
                ConsultationSummary.fromAi(501L, "고객 요청 요약", LocalDateTime.now())));
        assertThat(query.find(admin, 501L).summary()).isEqualTo("고객 요청 요약");
        assertThat(query.find(new GatewayUserPrincipal("11", RolePolicy.SUPER_ADMIN), 501L).status()).isEqualTo("COMPLETED");
        assertThatThrownBy(() -> query.find(new GatewayUserPrincipal("12", RolePolicy.ADMIN), 501L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> query.find(new GatewayUserPrincipal("12", RolePolicy.SUPER_ADMIN), 501L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void customerAndAnonymousCannotAccessEvenTheirOwnConversationSummary() {
        assertThatThrownBy(() -> query.find(new GatewayUserPrincipal("77", RolePolicy.CUSTOMER), 501L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> query.find(null, 501L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(consultations, summaries, jobs);
    }

    @Test
    void reportsDisabledAbsentPendingAndFailureWithoutExposingInternalFailureCode() {
        assignedConsultation();
        assertThat(query.find(admin, 501L).status()).isEqualTo("DISABLED");
        ReflectionTestUtils.setField(query, "enabled", true);
        assertThat(query.find(admin, 501L).status()).isEqualTo("NOT_REQUESTED");
        ConsultationSummaryJob job = ConsultationSummaryJob.create(501L, UUID.randomUUID(), LocalDateTime.now());
        when(jobs.findByConsultationId(501L)).thenReturn(Optional.of(job));
        assertThat(query.find(admin, 501L).status()).isEqualTo("PENDING");
        job.markSubmissionFailed("DEPENDENCY_UNAVAILABLE", true, LocalDateTime.now());
        assertThat(query.find(admin, 501L).status()).isEqualTo("SUBMISSION_FAILED");
        assertThat(query.find(admin, 501L).summary()).isNull();
    }
}
