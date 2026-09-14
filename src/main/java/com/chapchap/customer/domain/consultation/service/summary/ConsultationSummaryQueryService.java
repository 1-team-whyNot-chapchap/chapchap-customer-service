package com.chapchap.customer.domain.consultation.service.summary;

import com.chapchap.customer.domain.consultation.repository.summary.ConsultationSummaryRepository;
import com.chapchap.customer.domain.consultation.repository.summary.ConsultationSummaryJobRepository;
import com.chapchap.customer.domain.consultation.response.summary.ConsultationHandoffSummaryResponse;
import com.chapchap.customer.domain.consultation.service.ConsultationService;
import com.chapchap.customer.global.security.context.GatewayUserPrincipal;
import com.chapchap.customer.global.security.constant.RolePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsultationSummaryQueryService {
    private final ConsultationService consultationService;
    private final ConsultationSummaryRepository summaryRepository;
    private final ConsultationSummaryJobRepository jobRepository;
    @Value("${customer.ai.consultation-summary.async-enabled:false}")
    private boolean enabled;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<ConsultationSummaryStateService> stateService;

    @Transactional(readOnly = true)
    public ConsultationHandoffSummaryResponse find(GatewayUserPrincipal principal, Long consultationId) {
        if (principal == null || (principal.role() != RolePolicy.ADMIN && principal.role() != RolePolicy.SUPER_ADMIN)) {
            throw new AccessDeniedException("담당 상담사만 요약을 조회할 수 있습니다.");
        }
        consultationService.assertWebSocketParticipant(consultationId, principal);
        var summary = summaryRepository.findByConsultationId(consultationId);
        if (summary.isPresent()) {
            return new ConsultationHandoffSummaryResponse(consultationId, "COMPLETED", summary.get().getAiSummary());
        }
        var job = jobRepository.findByConsultationId(consultationId).orElse(null);
        if (job != null) {
            boolean retryAllowed = enabled && job.getCutoffSequenceNo() != null
                    && java.util.Set.of("FAILED", "SUBMISSION_FAILED").contains(job.getStatus().name());
            return new ConsultationHandoffSummaryResponse(consultationId, job.getStatus().name(), null,
                    retryAllowed);
        }
        String status = enabled ? "NOT_REQUESTED" : "DISABLED";
        return new ConsultationHandoffSummaryResponse(consultationId, status, null);
    }

    @Transactional
    public ConsultationHandoffSummaryResponse retry(GatewayUserPrincipal principal, Long consultationId) {
        find(principal, consultationId); // Same role and assignment boundary as reading a summary.
        var service = enabled ? stateService.getIfAvailable() : null;
        if (service == null) throw new com.chapchap.customer.global.exception.consultation.ConsultationStateException(
                "요약 기능이 비활성화되어 있습니다.");
        service.retryManually(consultationId, java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")));
        return find(principal, consultationId);
    }
}
