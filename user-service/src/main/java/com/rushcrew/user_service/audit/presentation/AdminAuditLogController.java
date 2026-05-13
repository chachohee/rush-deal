package com.rushcrew.user_service.audit.presentation;

import com.rushcrew.user_service.audit.application.AdminAuditLogService;
import com.rushcrew.user_service.audit.domain.AdminAction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditLogService service;

    @GetMapping
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Page<AdminAuditLogResponse>> list(
        @RequestParam(required = false) AdminAction action,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "30") int size
    ) {
        return ResponseEntity.ok(service.list(action, PageRequest.of(page, size)));
    }
}
