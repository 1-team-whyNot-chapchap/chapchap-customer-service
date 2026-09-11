package com.chapchap.customer.domain.audit.response;

import com.chapchap.customer.domain.audit.constant.AuditActionType;
import com.chapchap.customer.domain.audit.constant.AuditActorType;
import com.chapchap.customer.domain.audit.entity.AuditLog;
import com.chapchap.customer.domain.audit.constant.AuditResult;
import com.chapchap.customer.domain.audit.constant.AuditTargetType;

import java.time.LocalDateTime;
import java.util.Map;

public record AuditLogResponse(
        Long auditLogId,
        Long actorUserId,
        AuditActorType actorType,
        AuditActionType actionType,
        AuditTargetType targetType,
        String targetId,
        AuditResult result,
        String traceId,
        Map<String, Object> detail,
        LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorUserId(),
                auditLog.getActorType(),
                auditLog.getActionType(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getResult(),
                auditLog.getTraceId(),
                auditLog.getDetail(),
                auditLog.getCreatedAt()
        );
    }
}
