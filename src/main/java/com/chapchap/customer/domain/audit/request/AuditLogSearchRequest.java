package com.chapchap.customer.domain.audit.request;

import com.chapchap.customer.domain.audit.entity.AuditActionType;
import com.chapchap.customer.domain.audit.entity.AuditActorType;
import com.chapchap.customer.domain.audit.entity.AuditResult;
import com.chapchap.customer.domain.audit.entity.AuditTargetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public record AuditLogSearchRequest(
        AuditActorType actorType,
        @Min(value = 1, message = "수행자 ID는 1 이상이어야 합니다.") Long actorUserId,
        AuditActionType actionType,
        AuditTargetType targetType,
        @Size(max = 64, message = "대상 ID는 64자 이하여야 합니다.") String targetId,
        AuditResult result,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") Integer size
) {
    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? 20 : size;
    }
}
