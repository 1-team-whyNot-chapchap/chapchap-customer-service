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
        String status = jobRepository.findByConsultationId(consultationId)
                .map(job -> job.getStatus().name()).orElse(enabled ? "NOT_REQUESTED" : "DISABLED");
        return new ConsultationHandoffSummaryResponse(consultationId, status, null);
    }
}
