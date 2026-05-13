package com.rushcrew.user_service.audit.presentation;

import com.rushcrew.user_service.audit.domain.AdminAction;
import com.rushcrew.user_service.audit.domain.AdminAuditLog;
import java.time.Instant;

public record AdminAuditLogResponse(
    Long id,
    Long adminId,
    AdminAction action,
    Long targetUserId,
    String details,
    Instant createdAt
) {
    public static AdminAuditLogResponse from(AdminAuditLog log) {
        return new AdminAuditLogResponse(
            log.getId(),
            log.getAdminId(),
            log.getAction(),
            log.getTargetUserId(),
            log.getDetails(),
            log.getCreatedAt()
        );
    }
}
