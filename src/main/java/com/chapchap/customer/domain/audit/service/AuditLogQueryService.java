package com.chapchap.customer.domain.audit.service;

import com.chapchap.customer.domain.audit.entity.AuditLog;
import com.chapchap.customer.domain.audit.entity.AuditTargetType;
import com.chapchap.customer.domain.audit.repository.AuditLogRepository;
import com.chapchap.customer.domain.audit.request.AuditLogSearchRequest;
import com.chapchap.customer.domain.audit.response.AuditLogPageResponse;
import com.chapchap.customer.global.error.custom.audit.AuditLogQueryValidationException;
import com.chapchap.customer.global.security.constant.RolePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {
    private static final EnumSet<AuditTargetType> ADMIN_ALLOWED_TARGET_TYPES = EnumSet.of(
            AuditTargetType.FAQ,
            AuditTargetType.CONSULTATION,
            AuditTargetType.QUALITY_INQUIRY,
            AuditTargetType.KNOWLEDGE_VERSION
    );

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public AuditLogPageResponse search(AuditLogSearchRequest request, RolePolicy requesterRole) {
        validateRequesterRole(requesterRole);
        validatePeriod(request);

        Pageable pageable = PageRequest.of(
                request.pageOrDefault(),
                request.sizeOrDefault(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        return AuditLogPageResponse.from(auditLogRepository.findAll(buildSpecification(request, requesterRole), pageable));
    }

    private Specification<AuditLog> buildSpecification(AuditLogSearchRequest request, RolePolicy requesterRole) {
        return (root, query, criteriaBuilder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (requesterRole == RolePolicy.ADMIN) {
                predicates.add(root.get("targetType").in(ADMIN_ALLOWED_TARGET_TYPES));
            }
            if (request.actorType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("actorType"), request.actorType()));
            }
            if (request.actorUserId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("actorUserId"), request.actorUserId()));
            }
            if (request.actionType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("actionType"), request.actionType()));
            }
            if (request.targetType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("targetType"), request.targetType()));
            }
            if (StringUtils.hasText(request.targetId())) {
                predicates.add(criteriaBuilder.equal(root.get("targetId"), request.targetId().trim()));
            }
            if (request.result() != null) {
                predicates.add(criteriaBuilder.equal(root.get("result"), request.result()));
            }
            if (request.from() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), request.from()));
            }
            if (request.to() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), request.to()));
            }

            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private void validatePeriod(AuditLogSearchRequest request) {
        if (request.from() != null && request.to() != null && request.from().isAfter(request.to())) {
            throw new AuditLogQueryValidationException("조회 시작 시각은 종료 시각보다 늦을 수 없습니다.");
        }
    }

    private void validateRequesterRole(RolePolicy requesterRole) {
        if (requesterRole != RolePolicy.ADMIN && requesterRole != RolePolicy.SUPER_ADMIN) {
            throw new AccessDeniedException("감사 로그 조회 권한이 없습니다.");
        }
    }
}
