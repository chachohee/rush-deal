package com.rushcrew.queue.application.service;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.queue.application.command.queue.EnterQueueCommand;
import com.rushcrew.queue.application.dto.QueueRedisResponse;
import com.rushcrew.queue.application.port.in.QueuePort;
import com.rushcrew.queue.common.QueueErrorCode;
import com.rushcrew.queue.domain.entity.QueuePolicy;
import com.rushcrew.queue.domain.entity.QueueToken;
import com.rushcrew.queue.domain.enums.QueueStatus;
import com.rushcrew.queue.domain.repository.QueuePolicyRepository;
import com.rushcrew.queue.domain.repository.QueueRepository;
import com.rushcrew.queue.domain.vo.TokenId;
import com.rushcrew.queue.domain.vo.TrafficSetting;
import com.rushcrew.queue.infrastructure.repository.RedisQueueRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class QueueService implements QueuePort {
    private final QueueRepository queueRepository;
    private final QueuePolicyRepository queuePolicyRepository;

    public QueueService(QueueRepository queueRepository, QueuePolicyRepository queuePolicyRepository) {
        this.queueRepository = queueRepository;
        this.queuePolicyRepository = queuePolicyRepository;
    }

    /**
     * 대기열 진입
     * TODO: product ID는 타임딜 정책(QueuePolicy) 테이블에서 유효성 검증
     * 관리자가 정책을 등록하지 않은 상품은 대기열을 생성할 수 없음
     */
    @Override
    @Transactional(readOnly = true)
    public QueueRedisResponse enterQueue(EnterQueueCommand command) {
        // 재고 품절 여부 확인 (Redis 조회)
        if (queueRepository.isSoldOut(command.productId())) {
            throw new BusinessException(QueueErrorCode.PRODUCT_SOLD_OUT);
        }

        // 대기열 정책 확인 (RDB 조회 - 상품 존재 여부 및 시간 확인)
        QueuePolicy policy = queuePolicyRepository.findByProductId(command.productId())
            .orElseThrow(() -> new BusinessException(QueueErrorCode.NO_TIMEDEAL_PRODUCT));

        QueueToken queueToken = QueueToken.create(command.productId(), command.userId());

        // redis 대기열 저장소 저장 & 중복 진입 차단
        boolean isSuccess = queueRepository.register(queueToken, policy.getTimePeriod().getEndTime(),
            policy.getTrafficSetting().getTtl());
        if (!isSuccess) {
            // 이미 대기열에 있는 경우 예외 처리
            throw new BusinessException(QueueErrorCode.USER_ALREADY_IN_WAITING_QUEUE);
        }

        String tokenValue = queueToken.getId().getValue().toString();
        return getQueueRank(command.productId(), tokenValue, command.userId());
    }

    /**
     * 대기열 순번, 상태 조회 (polling)
     */
    @Override
    public QueueRedisResponse getQueueRank(UUID productId, String token, Long userId) {
        // 토큰 유효성 검증: 본인 확인 (대기열 토큰 소유권 검증)
        boolean isOwner = queueRepository.verifyTokenOwner(productId, userId, token);
        if (!isOwner) {
            log.warn("[QUEUE:ERROR] 토큰 도용 시도 감지: User {}, Token {}", userId, token);
            throw new BusinessException(QueueErrorCode.TOKEN_OWNER_NOT_MATCH);
        }

        TokenId tokenId = extractValidQueueTokenId(token);

        // 활성 상태 여부 확인
        if (queueRepository.isActivatedToken(productId, tokenId)) {
            return QueueRedisResponse.builder()
                .token(tokenId.getValue())
                .productId(productId)
                .rank(0L) // 활성 상태는 순번 0 (이미 활성열에 있으므로)
                .status(QueueStatus.ACTIVE)
                .enteredAt(LocalDateTime.now()) // 활성 상태일 때, 진입시간 현재시간으로 설정
                .build();
        }

        // 대기열 순번 확인 (Redis ZRANK)
        // rank는 0부터 시작 (내 앞의 대기 인원 수 (0이면 내가 1빠))
        Long waitingRank = queueRepository.getWaitingRank(productId, tokenId);
        if (waitingRank == null) {
            // User Index Key는 있는데 Redis에 ZSet에 없는 경우 (만료됨)
            // => 에러를 던지면 클라이언트가 다시 enterQueue를 호출하게 되고,
            // 그때 QueueRepository의 register 메서드에서 Index Key 삭제하고 재진입 처리
            throw new BusinessException(QueueErrorCode.QUEUE_TOKEN_NOT_AVAILABLE);
        }

        // 요청시간 LocalDateTime 타입으로 변환
        Long requestTime = getRequestTime(productId, tokenId);
        LocalDateTime enteredAt = convertLocalDateTime(requestTime);

        return QueueRedisResponse.builder()
            .token(tokenId.getValue())
            .productId(productId)
            .rank(waitingRank + 1) // 사용자 친화적 순번 (0번대신 1번부터 표시)
            .status(QueueStatus.WAITING)
            .enteredAt(enteredAt)
            .build();
    }

    @Override
    public TokenId extractValidQueueTokenId(String token) {
        TokenId tokenId;
        try {
            tokenId = TokenId.of(UUID.fromString(token));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(QueueErrorCode.QUEUE_TOKEN_NOT_AVAILABLE);
        }
        return tokenId;
    }

    @Override
    public boolean activateTokens(UUID productId, TrafficSetting trafficSetting) {
        // 현재 활성 인원 조회
        Long currActiveCount = queueRepository.countActiveTokens(productId);

        // 최대 활성 허용 인원 대비 남은 자리 계산
        Integer maxCapacity = trafficSetting.getMaxCapacity();
        Integer limitSize = trafficSetting.getLimitSize(); // 한 번에 실행할 배치 크기라고 보면 됨

        if (currActiveCount >= maxCapacity) {
            log.warn("[QUEUE] 활성열이 꽉 찼습니다. (Current: {}, Max: {})", currActiveCount, maxCapacity);
            return false;
        }

        // 활성 토큰 N개 계산 => (N = min(배치사이즈, 남은자리))
        long availableCount = maxCapacity - currActiveCount;
        // 최대 활성 허용 수에서 남은 자리(availableCount)가 배치 크기보다 작으면 남은 자리 수의 토큰을 활성열로 이동시키는 로직
        long tokenCountToActivate = Math.min(limitSize, availableCount);

        if (tokenCountToActivate <= 0) {
            return false;
        }

        // 대기열에서 상위 N개 토큰 조회 (Waiting -> Active 대상) : 요청 시점(Score)이 낮은 것
        List<String> waitingTokensToActivate = queueRepository.getWaitingTokens(productId, tokenCountToActivate);

        if (waitingTokensToActivate.isEmpty()) {
            log.debug("[QUEUE] 대기열이 비어있습니다.");
            return false;
        }

        // 활성 상태로 전환
        queueRepository.activateTokens(productId, waitingTokensToActivate, trafficSetting);
        return true;
    }

    /**
     * 대기열 퇴장/취소 (토큰 삭제 처리)
     * 대기 중 취소하거나, 주문 완료 후 호출
     * UserId를 넘겨서 USER_INDEX_KEY까지 확실하게 지움 -> 즉시 재진입 가능
     */
    @Override
    public void exitQueue(UUID productId, String token, Long userId) {
        TokenId tokenId = extractValidQueueTokenId(token);
        // RedisQueueRepository로 캐스팅
        if (queueRepository instanceof RedisQueueRepository) {
            queueRepository.removeTokenWithUserIdxKey(productId, tokenId, userId);
        } else {
            queueRepository.removeToken(productId, tokenId);
        }
    }

    /**
     * 토큰 유효성 검증 (활성화 여부)
     */
    @Override
    public boolean validateActivatedQueueToken(UUID productId, String token, Long userId, String role) {
        TokenId tokenId = extractValidQueueTokenId(token);
        return queueRepository.isActivatedToken(productId, tokenId);
    }

    /**
     * 타임스탬프 -> LocalDateTime 변환
     */
    private LocalDateTime convertLocalDateTime(Long timestamp) {
        // 시스템 기본 타임존 사용 (Asia/Seoul)
        if (timestamp == null) { return null; }
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }

    private Long getRequestTime(UUID productId, TokenId tokenId) {
        // 진입 요청 시간 반환
        Double score = queueRepository.getWaitingScore(productId, tokenId);
        return score != null ? score.longValue() : System.currentTimeMillis();
    }
}
