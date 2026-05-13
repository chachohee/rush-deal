package com.rushcrew.user_service.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "p_admin_audit_log",
    schema = "user_schema",
    indexes = {
        @Index(name = "idx_audit_created", columnList = "created_at DESC"),
        @Index(name = "idx_audit_admin", columnList = "admin_id"),
        @Index(name = "idx_audit_target", columnList = "target_user_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdminAction action;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(length = 500)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static AdminAuditLog of(Long adminId, AdminAction action, Long targetUserId, String details) {
        return AdminAuditLog.builder()
            .adminId(adminId)
            .action(action)
            .targetUserId(targetUserId)
            .details(details)
            .createdAt(Instant.now())
            .build();
    }
}
