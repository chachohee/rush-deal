package com.rushcrew.queue.infrastructure.repository;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.queue.common.QueueErrorCode;
import com.rushcrew.queue.domain.entity.QueueToken;
import com.rushcrew.queue.domain.repository.QueueRepository;
import com.rushcrew.queue.domain.vo.TokenId;
import com.rushcrew.queue.domain.vo.TrafficSetting;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class RedisQueueRepository implements QueueRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String WAITING_KEY = "queue:wait:product:%s";
    private static final String ACTIVE_KEY = "queue:active:product:%s";
    private static final String USER_INDEX_KEY = "queue:user:product:%s:%s"; // String (중복방지용)
    private static final String PRODUCT_STATUS_KEY = "queue:product:status:%s"; // 카프카로부터 받아옴 - Value: "AVAILABLE" or "SOLDOUT"


    public RedisQueueRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 대기열 등록 (ZSet : Sorted Set)
     *
     * 추가 처리 : Lazy Cleanup으로 인해 ZSet(대기열/활성열)에서는 토큰이 삭제되었는데
     * USER_INDEX_KEY만 덩그러니 남아있는 상황이 발생하면, 해당 유저는 영원히 재진입이 불가능해짐.
     * 따라서 진입 시도 시 USER_INDEX_KEY가 이미 있다면,
     * "진짜 대기열/활성열에 살아있는지" 더블 체크. 없다면 좀비 키로 간주하고 삭제 후 재진입을 허용하는 방식으로 수정
     */
    @Override
    public boolean register(QueueToken token, LocalDateTime dealEndTime, Integer activeTtl, Integer maxCapacity) {
        // TTL 계산 : (이벤트 종료 시간 - 현재 시간)
        long secondsUntilClose = Duration.between(LocalDateTime.now(), dealEndTime).getSeconds();

        if (secondsUntilClose < 0) {
            // 이미 종료된 이벤트면 진입 불가 처리
            log.warn("[QUEUE:ERROR] 이미 종료된 이벤트입니다. productId={}", token.getProductId());
            throw new BusinessException(QueueErrorCode.NO_TIMEDEAL_PRODUCT);
        }

        // redis 사용자 인덱스 키 생성
        String userIndexKey = getUserIndexKey(token.getProductId(), token.getUserId());
        String newTokenValue = token.getId().getValue().toString();

        // 중복 방지 : 유저별 대기열 키 생성 (SETNX)
        // KEY: queue:user:product:{productId}:{userId} / VALUE: 토큰 UUID
        Boolean isNewUser = redisTemplate.opsForValue().setIfAbsent(
            userIndexKey,
            newTokenValue,
            Duration.ofSeconds(secondsUntilClose) // TTL 설정: 타임딜 종료 시간에 맞춰 자동 만료
        );

        // 유저별 대기열 키(USER_INDEX_KEY)가 이미 존재하는 경우 -> 진짜 유효한지 검증
        if (Objects.equals(isNewUser, Boolean.FALSE)) {
            // USER_INDEX_KEY가 바라보는 큐 토큰 (해당 유저가 가지고 있던 토큰의 ID(UUID))
            String oldToken = redisTemplate.opsForValue().get(userIndexKey);

            // 기존 토큰이 대기열이나 활성열에 실제로 존재하는지 확인
            if (oldToken != null && !isTokenAlive(token.getProductId(), oldToken)) {
                // 존재하지 않음 = 만료되었거나 삭제된 좀비 키(USER_INDEX_KEY)임 -> 삭제 후 재등록 허용
                log.info("[QUEUE:REPAIR] 좀비 키(USER_INDEX_KEY) 발견. 삭제 후 재진입 처리 userId={}, oldToken={}", token.getUserId(), oldToken);
                redisTemplate.delete(userIndexKey);

                // 삭제하고 재시도 (재귀 호출)
                return register(token, dealEndTime, activeTtl, maxCapacity);
            }

            // 이미 대기 중인 유저 (중복 진입 거부)
            log.warn("[QUEUE:ERROR] 이미 대기 중인 사용자입니다. userId={}", token.getUserId());
            return false;
        }

        // FAST TRACK 판단 : 활성열 인원 조회
        Long activeCount = countActiveTokens(token.getProductId());

        log.info("[QUEUE:INFO] 현재 활성열 인원 개수 count={}", activeCount);
        if (activeCount != null && activeCount < maxCapacity) {
            // [Fast Track] 대기 없이 바로 활성 상태 진입
            return registerFastTrack(token, dealEndTime, activeTtl, userIndexKey);
        } else {
            // 대기열 등록 (ZSet)
            return registerWaitingQueue(token, userIndexKey);
        }
    }

    /**
     * USER_INDEX_KEY가 가리키는 토큰이 진짜 ZSet(대기열 or 활성열)에 있는지 확인
     */
    private boolean isTokenAlive(UUID productId, String tokenValue) {
        String waitingKey = getWaitingKey(productId);
        String activeKey = getActiveKey(productId);

        // 대기열 확인
        Double waitingScore = redisTemplate.opsForZSet().score(waitingKey, tokenValue);
        if (waitingScore != null) return true;

        // 활성열 확인
        Double activeScore = redisTemplate.opsForZSet().score(activeKey, tokenValue);
        return activeScore != null;
    }

    @Override
    public void activateTokens(UUID productId, List<String> tokens, TrafficSetting setting) {
        if (tokens == null || tokens.isEmpty()) { return; }

        String waitingKey = getWaitingKey(productId);
        String activeKey = getActiveKey(productId);

        // TrafficSetting의 TTL을 사용하여 만료 시간 계산
        double expireAt = getExpireAt(setting.getTtl());

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            // StringRedisConnection으로 형변환
            StringRedisConnection strConnection = (StringRedisConnection) connection;
                for (String token : tokens) {
                    // 활성열 추가 (Score = 만료 예정 시간)
                    strConnection.zAdd(activeKey, expireAt, token);

                    // 대기열 제거
                    strConnection.zRem(waitingKey, token) ;
                }
                return null; // 파이프라인은 반환값이 null이어야 함
        });
        log.info("[QUEUE:SUCCESS:ACTIVE] 상품({}) :: {}명 활성화 완료 (Max: {}, Limit: {})",
            productId, tokens.size(), setting.getMaxCapacity(), setting.getLimitSize());
    }

    /**
     * 활성 대상 토큰 조회
     * ZSet에서 점수가 가장 낮은 N명 조회
     */
    @Override
    public List<String> getWaitingTokens(UUID productId, long count) {
        // Score(타임스탬프)가 낮은 순서(진입 요청 시점이 이른 것부터)대로 조회 (FIFO)
        // 0번부터 count-1명까지 (상위 N명) -> Redis ZRANGE key 0 N (Score가 가장 낮은 순서부터 N개 가져옴)
        Set<String> tokens = redisTemplate.opsForZSet().range(
            getWaitingKey(productId),
            0,
            count - 1
        );
        return tokens == null ? List.of() : new ArrayList<>(tokens);
    }

    @Override
    public boolean isActivatedToken(UUID productId, TokenId tokenId) {
        // ZSet에서 Score(만료시간) 조회
        String activeKey = getActiveKey(productId);
        Double expireTime = redisTemplate.opsForZSet().score(activeKey, tokenId.getValue().toString());

        // 활성열에 없음
        if (expireTime == null) return false;

        // 만료 시간 지났는지 확인 (Lazy Validation)
        long now = System.currentTimeMillis();
        if (expireTime < now) {
            // 만료되었으면 삭제
            removeToken(productId, tokenId);
            log.info("[QUEUE:EXPIRE:ACTIVE] 활성 토큰 만료됨. token={}", tokenId);
            return false;
        }
        return true;
    }

    /**
     * 활성 토큰 수 확인 (ZSet Size 조회)
     * 조회 직전에 이미 만료된 토큰을 일괄적으로 삭제하여 정확한 수를 반환함 (Lazy cleanup)
     */
    @Override
    public Long countActiveTokens(UUID productId) {
        String activeKey = getActiveKey(productId);

        // lazy cleanup
        // ZSet의 Score(만료시간)가 현재 시간보다 작은(과거인) 멤버들 삭제
        // ZREMRANGEBYSCORE key -inf current_timestamp
        double now = System.currentTimeMillis();
        redisTemplate.opsForZSet().removeRangeByScore(
            activeKey,
            Double.NEGATIVE_INFINITY,
            now
        );

        // 청소 후 남은 개수 반환
        Long count = redisTemplate.opsForZSet().zCard(activeKey);
        return count != null ? count : 0L;
    }

    @Override
    public Long getWaitingRank(UUID productId, TokenId tokenId) {
        // ZRANK key member (ZSet 조회)
        // Redis ZRANK 통하여 대기 순번 조회 성능을 O(log N)으로 최적화
        return redisTemplate.opsForZSet()
            .rank(getWaitingKey(productId),
                tokenId.getValue().toString()
            );
    }

    @Override
    public Double getWaitingScore(UUID productId, TokenId tokenId) {
        // Redis ZSCORE로 진입 시간 조회
        return redisTemplate.opsForZSet()
            .score(getWaitingKey(productId),
                tokenId.getValue().toString()
            );
    }

    /**
     * 활성열의 토큰 삭제
     */
    @Override
    public void removeToken(UUID productId, TokenId tokenId) {
        // 활성열(ZSet)에서 해당 토큰 삭제
        redisTemplate.opsForZSet()
            .remove(
                getActiveKey(productId),
                tokenId.getValue().toString()
            );
    }

    /**
     * 명시적 퇴장 : 활성열/대기열 토큰 삭제 + USER_INDEX_KEY 삭제
     * QueueService에서 사용자가 직접 취소하거나 주문 완료 시 호출
     * 이 경우, USER_INDEX_KEY도 같이 삭제해야 바로 다시 해당 유저가 대기열 재진입 가능
     */
    @Override
    public void removeTokenWithUserIdxKey(UUID productId, TokenId tokenId, Long userId) {
        String tokenValue = tokenId.getValue().toString();

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection strConnection = (StringRedisConnection) connection;
            // 활성열에서 삭제
            strConnection.zRem(getActiveKey(productId), tokenValue);
            // 대기열에서 삭제 (활성열에서 이미 삭제되어있다면 대기열에도 없겠지만 혹시 모르니 둘 다 삭제 처리)
            strConnection.zRem(getWaitingKey(productId), tokenValue);
            // 유저 인덱스 키 삭제 (재진입 허용 목적)
            strConnection.del(getUserIndexKey(productId, userId));
            return null;
        });
        log.info("[QUEUE:EXIT] 대기/활성열 퇴장 처리 완료: userId={}, token={}", userId, tokenId);
    }

    /**
     * 상품 품절 처리 (Kafka 수신 시 호출)
     * @param productId
     */
    @Override
    public void setSoldOut(UUID productId, String status, LocalDateTime dealEndTime) {
        String productStatusKey = getProductStatusKey(productId);

        // TTL 계산 : (이벤트 종료 시간 - 현재 시간)
        long secondsUntilClose = Duration.between(LocalDateTime.now(), dealEndTime).getSeconds();

        // 이미 시간이 지난 경우 (음수가 나오면 에러 나거나 바로 만료 처리)
        if (secondsUntilClose < 0) {
            secondsUntilClose = 0;
        }

        // TTL 설정: 타임딜 종료 시간에 맞춰 자동 만료
        redisTemplate.opsForValue().set(productStatusKey, status, Duration.ofSeconds(secondsUntilClose));
        log.info("[QUEUE:SOLDOUT] 상품({}) 품절 상태로 변경", productId);
    }

    /**
     * 품절 여부 확인 (enterQueue 진입 시 호출)
     */
    @Override
    public boolean isSoldOut(UUID productId) {
        String key = String.format(PRODUCT_STATUS_KEY, productId);
        String status = redisTemplate.opsForValue().get(key);
        return "SOLDOUT".equals(status);
    }


    /**
     * 본인 확인 (대기열 토큰 소유권 검증)
     */
    @Override
    public boolean verifyTokenOwner(UUID productId, Long userId, String token) {
        // redis에 저장된 해당 유저 토큰 조회
        String savedToken = redisTemplate.opsForValue().get(
            getUserIndexKey(productId, userId)
        );

        // 저장된 토큰 없거나, 요청 토큰과 다르면 본인 아님
        return savedToken != null && savedToken.equals(token);
    }

    @Override
    public String findExistingTokenForUser(UUID productId, Long userId) {
        String saved = redisTemplate.opsForValue().get(getUserIndexKey(productId, userId));
        if (saved == null) return null;
        // 좀비 키일 수도 있으니 실제 ZSet 에 있는지 확인
        return isTokenAlive(productId, saved) ? saved : null;
    }

    /**
     * Fast Track: 즉시 활성열 등록 (ZSet 등록)
     */
    private boolean registerFastTrack(QueueToken token, LocalDateTime dealEndTime, Integer activeTtl,
        String userIndexKey) {
        // ActiveKey(활성열 키) 생성
        String activeKey = getActiveKey(token.getProductId());
        try {
            // 만료 시간(score) 계산: 현재시간 기준 + activeTtl
            double expireAt = getExpireAt(activeTtl);

            // active(활성열) ZSet에 저장 (Score = 만료시간)
            redisTemplate.opsForZSet().add(
                activeKey,
                token.getId().getValue().toString(),
                expireAt
            );
            log.info("[QUEUE:REDIS] FAST TRACK 활성열 등록 성공: user={}, token={}", token.getUserId(), token.getId());
            return true;
        } catch (Exception e) {
            // 롤백: 문제 발생 시 유저 인덱스 삭제 (재진입 허용)
            // 보상 트랜잭션 : Set 저장 실패 시, 중복 방지 키(userIndexKey)와 activeKey도 삭제해줘야 유저가 다시 시도 가능
            log.error("[QUEUE:REDIS:ERROR] FAST TRACK 활성열 진입 실패로 인한 롤백 수행: userId={}, tokenId={}", token.getUserId(), token.getId());
            redisTemplate.delete(userIndexKey);
            redisTemplate.opsForSet().remove(activeKey, token.getId().toString());
            throw e;
        }
    }

    /**
     * 대기열 등록 (ZSet 등록)
     */
    private boolean registerWaitingQueue(QueueToken token, String userIndexKey) {
        try {
            double score = System.currentTimeMillis() * 1_000_000.0 + (System.nanoTime() % 1_000_000);
            redisTemplate.opsForZSet().add(
                getWaitingKey(token.getProductId()),
                token.getId().getValue().toString(),
                score
            );
            log.info("[QUEUE:REDIS] 대기열 진입 성공: user={}, score={}", token.getUserId(), score);
            return true;
        } catch (Exception e) {
            // 보상 트랜잭션 : ZSet 저장 실패 시, 중복 방지 키(userIndexKey)도 삭제해줘야 유저가 다시 시도 가능
            log.error("[QUEUE:REDIS:ERROR] 대기열 진입 실패로 인한 롤백 수행: userId={}, tokenId={}", token.getUserId(), token.getId());
            redisTemplate.delete(userIndexKey);
            throw e;
        }
    }

    private String getWaitingKey(UUID productId) {
        return String.format(WAITING_KEY, productId);
    }

    private String getActiveKey(UUID productId) {
        return String.format(ACTIVE_KEY, productId.toString());
    }

    private String getUserIndexKey(UUID productId, Long userId) {
        return String.format(USER_INDEX_KEY, productId, userId);
    }

    private String getProductStatusKey(UUID productId) {
        return String.format(PRODUCT_STATUS_KEY, productId);
    }

    private double getExpireAt(Integer activeTtl) {
        long now = System.currentTimeMillis();
        // 밀리초 단위 (예: 1730000000000 (13자리))
        return now + (activeTtl * 1000L);
    }
}
