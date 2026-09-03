package com.chapchap.customer.domain.audit.response;

import com.chapchap.customer.domain.audit.entity.AuditLog;
import org.springframework.data.domain.Page;

import java.util.List;

public record AuditLogPageResponse(
        List<AuditLogResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static AuditLogPageResponse from(Page<AuditLog> auditLogs) {
        return new AuditLogPageResponse(
                auditLogs.getContent().stream().map(AuditLogResponse::from).toList(),
                auditLogs.getNumber(),
                auditLogs.getSize(),
                auditLogs.getTotalElements(),
                auditLogs.getTotalPages()
        );
    }
}
