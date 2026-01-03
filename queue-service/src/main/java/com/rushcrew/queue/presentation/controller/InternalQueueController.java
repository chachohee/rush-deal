package com.rushcrew.queue.presentation.controller;

import com.rushcrew.common.dto.ApiResponse;
import com.rushcrew.queue.application.port.in.QueuePort;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/queues")
public class InternalQueueController {
    private final QueuePort queueService;

    // API Gateway에서 인증 후, USER ID와 ROLE을 헤더에 담아 전달
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String QUEUE_TOKEN_HEADER = "X-Queue-Token";

    public InternalQueueController(QueuePort queueService) {
        this.queueService = queueService;
    }

    /**
     * 토큰 유효성 검증 API (주문 진입 시 호출)
     * 주문 요청 시 토큰 유효성 검증
     * Order-Service 쪽에서 활성열에 등록된 토큰이 현재 ACTIVE 상태인지 유효성 검증 (내부 호출)
     */
    @GetMapping("/tokens/verify")
    public ResponseEntity<ApiResponse<Boolean>> validateQueueToken(
        @RequestParam UUID productId,
        @RequestHeader(QUEUE_TOKEN_HEADER) String queueToken,
        @RequestHeader(USER_ID_HEADER) Long currUserId,
        @RequestHeader(USER_ROLE_HEADER) String role
    ) {
        boolean result = queueService.validateActivatedQueueToken(productId, queueToken, currUserId, role);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(result));
    }
}
