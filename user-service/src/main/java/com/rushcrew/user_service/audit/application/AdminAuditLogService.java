package com.rushcrew.user_service.audit.application;

import com.rushcrew.user_service.audit.domain.AdminAction;
import com.rushcrew.user_service.audit.domain.AdminAuditLog;
import com.rushcrew.user_service.audit.domain.AdminAuditLogRepository;
import com.rushcrew.user_service.audit.presentation.AdminAuditLogResponse;
import com.rushcrew.user_service.user.domain.entity.User;
import com.rushcrew.user_service.user.infrastructure.repository.UserJpaRepository;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final UserJpaRepository userJpaRepository;

    public Page<AdminAuditLogResponse> list(AdminAction action, Pageable pageable) {
        Page<AdminAuditLog> page = action == null
            ? repository.findAllByOrderByCreatedAtDesc(pageable)
            : repository.findByActionOrderByCreatedAtDesc(action, pageable);

        Set<Long> userIds = new HashSet<>();
        page.forEach(log -> {
            userIds.add(log.getAdminId());
            userIds.add(log.getTargetUserId());
        });

        Map<Long, String> emailById = userJpaRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getUserId, User::getEmail, (a, b) -> a));

        return page.map(log -> AdminAuditLogResponse.of(
            log,
            emailById.get(log.getAdminId()),
            emailById.get(log.getTargetUserId())
        ));
    }
}
