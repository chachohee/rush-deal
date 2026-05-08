package com.rushcrew.queue.presentation.controller;

import com.rushcrew.common.dto.ApiResponse;
import com.rushcrew.queue.domain.entity.QueueToken;
import com.rushcrew.queue.domain.vo.TokenId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/test/queues")
public class QueueTestController {

    private final StringRedisTemplate redisTemplate;
    private static final String ACTIVE_KEY = "queue:active:product:%s";
    private static final String USER_INDEX_KEY = "queue:user:product:%s:%s";

    public QueueTestController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 테스트용: 활성열에 N개의 더미 토큰 미리 채우기
     * 용도: Fast Track(100명 미만 즉시 활성) 우회하여 대기열 테스트 가능
     */
    @PostMapping("/seed-active-tokens")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<ApiResponse<String>> seedActiveTokens(
        @RequestParam UUID productId,
        @RequestParam(defaultValue = "110") int count,
        @RequestParam(defaultValue = "300") int ttlSeconds // 5분 (300초)
    ) {
        String activeKey = String.format(ACTIVE_KEY, productId);
        long now = System.currentTimeMillis();
        long expireAt = now + (ttlSeconds * 1000L);

        List<String> createdTokens = new ArrayList<>();

        try {
            // 파이프라인으로 한 번에 처리 (성능 최적화)
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                org.springframework.data.redis.connection.StringRedisConnection strConn =
                    (org.springframework.data.redis.connection.StringRedisConnection) connection;

                for (int i = 0; i < count; i++) {
                    // 더미 유저 ID (10000번부터 시작)
                    Long dummyUserId = 10000L + i;

                    // 더미 토큰 생성
                    String tokenValue = UUID.randomUUID().toString();

                    // 활성열에 추가
                    strConn.zAdd(activeKey, expireAt, tokenValue);

                    // USER_INDEX_KEY 설정 (중복 진입 방지용)
                    String userIndexKey = String.format(USER_INDEX_KEY, productId, dummyUserId);
                    strConn.setEx(userIndexKey, ttlSeconds, tokenValue);

                    createdTokens.add(tokenValue);
                }
                return null;
            });

            log.info("[TEST:SEED] 활성열 더미 토큰 {}개 생성 완료 (productId: {})", count, productId);

            return ResponseEntity.ok(
                ApiResponse.success(
                    String.format("✅ 활성열에 %d개 더미 토큰 생성 완료 (TTL: %d초)", count, ttlSeconds)
                )
            );

        } catch (Exception e) {
            log.error("[TEST:SEED:ERROR] 더미 토큰 생성 실패", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("더미 토큰 생성 실패: " + e.getMessage()));
        }
    }

    /**
     * 테스트용: 특정 상품의 대기열/활성열 전체 초기화
     */
    @PostMapping("/clear-queues")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<ApiResponse<String>> clearQueues(
        @RequestParam UUID productId
    ) {
        try {
            String waitingKey = String.format("queue:wait:product:%s", productId);
            String activeKey = String.format(ACTIVE_KEY, productId);

            // 대기열/활성열 삭제
            redisTemplate.delete(waitingKey);
            redisTemplate.delete(activeKey);

            // USER_INDEX_KEY 패턴 삭제 (전체 유저)
            String pattern = String.format(USER_INDEX_KEY, productId, "*");
            redisTemplate.keys(pattern).forEach(redisTemplate::delete);

            log.info("[TEST:CLEAR] 상품({}) 대기열/활성열 초기화 완료", productId);

            return ResponseEntity.ok(
                ApiResponse.success("✅ 대기열/활성열 초기화 완료")
            );

        } catch (Exception e) {
            log.error("[TEST:CLEAR:ERROR] 큐 초기화 실패", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("초기화 실패: " + e.getMessage()));
        }
    }

    /**
     * 테스트용: 현재 대기열/활성열 상태 조회
     */
    @PostMapping("/queue-status")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> getQueueStatus(
        @RequestParam UUID productId
    ) {
        String waitingKey = String.format("queue:wait:product:%s", productId);
        String activeKey = String.format(ACTIVE_KEY, productId);

        Long waitingCount = redisTemplate.opsForZSet().zCard(waitingKey);
        Long activeCount = redisTemplate.opsForZSet().zCard(activeKey);

        QueueStatusResponse status = new QueueStatusResponse(
            productId,
            waitingCount != null ? waitingCount : 0L,
            activeCount != null ? activeCount : 0L
        );

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    // DTO
    public record QueueStatusResponse(
        UUID productId,
        Long waitingCount,
        Long activeCount
    ) {}
}