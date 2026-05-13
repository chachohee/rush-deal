package com.rushcrew.user_service.audit.application;

import com.rushcrew.user_service.audit.domain.AdminAction;
import com.rushcrew.user_service.audit.domain.AdminAuditLog;
import com.rushcrew.user_service.audit.domain.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditLogService {

    private final AdminAuditLogRepository repository;

    public Page<AdminAuditLog> list(AdminAction action, Pageable pageable) {
        return action == null
            ? repository.findAllByOrderByCreatedAtDesc(pageable)
            : repository.findByActionOrderByCreatedAtDesc(action, pageable);
    }
}
