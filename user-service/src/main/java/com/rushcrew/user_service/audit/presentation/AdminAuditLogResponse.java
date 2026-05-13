package com.rushcrew.user_service.audit.presentation;

import com.rushcrew.user_service.audit.domain.AdminAction;
import com.rushcrew.user_service.audit.domain.AdminAuditLog;
import java.time.Instant;

public record AdminAuditLogResponse(
    Long id,
    Long adminId,
    String adminEmail,
    AdminAction action,
    Long targetUserId,
    String targetEmail,
    String details,
    Instant createdAt
) {
    public static AdminAuditLogResponse of(AdminAuditLog log, String adminEmail, String targetEmail) {
        return new AdminAuditLogResponse(
            log.getId(),
            log.getAdminId(),
            adminEmail,
            log.getAction(),
            log.getTargetUserId(),
            targetEmail,
            log.getDetails(),
            log.getCreatedAt()
        );
    }
}
