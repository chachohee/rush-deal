package com.rushcrew.queue.domain.repository;

import com.rushcrew.queue.domain.entity.QueueToken;
import com.rushcrew.queue.domain.vo.TokenId;
import com.rushcrew.queue.domain.vo.TrafficSetting;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface QueueRepository {
    /**
     * Redis의 대기열에 Sorted Set (ZSet) 타입으로 저장
     * Score에 타임스탬프를 사용하는 구조 (선착순 진입 순서 보장)
     * 진입 정책(Fast Track) : 현재 활성(Active) 상태인 토큰 수가 maxCapacity 미만이면 곧바로 활성열로 추가
     */
    boolean register(QueueToken token, LocalDateTime dealEndTime, Integer activeTtl, Integer maxCapacity);

    /**
     * 토큰 활성화 (대기열 -> 활성열)
     * 스케줄러용: 대기열에서 N명을 활성열로 이동
     * @param productId
     * @param tokens
     */
    void activateTokens(UUID productId, List<String> tokens, TrafficSetting trafficSetting);

    /**
     * 대기열에서 Score 정렬 순서대로 N개의 토큰 조회 (범위 조회)
     * 활성화 대상 토큰을 선별하기 위함
     * @param productId
     * @param count
     * @return
     */
    List<String> getWaitingTokens(UUID productId, long count);

    /**
     * 활성 토큰 검증 (주문 서비스에서 검증 요청 시 사용)
     * @param productId
     * @param tokenId
     * @return
     */
    boolean isActivatedToken(UUID productId, TokenId tokenId);

    /**
     * 현재 활성화된 인원 수 조회 (ZCard)
     * @param productId
     * @return
     */
    Long countActiveTokens(UUID productId);

    /**
     * 대기열 토큰 소유권 검증 (본인 확인)
     * @param productId
     * @param userId
     * @param token
     * @return
     */
    boolean verifyTokenOwner(UUID productId, Long userId, String token);

    /**
     * 토큰 상태/대기 순번 확인 (ZSet rank -> polling)
     * (ZSet 조회 - O(logN))
     * @param productId
     * @param tokenId
     * @return
     */
    Long getWaitingRank(UUID productId, TokenId tokenId);

    /**
     * 토큰 Score 조회 (요청 시간)
     * Redis ZSCORE로 진입 시간 조회
     * @param productId
     * @param tokenId
     * @return
     */
    Double getWaitingScore(UUID productId, TokenId tokenId);

    /**
     * 토큰 만료 삭제
     * @param productId
     * @param tokenId
     */
    void removeToken(UUID productId, TokenId tokenId);

    /**
     * 명시적 퇴장 : 활성열/대기열 토큰 삭제 + USER_INDEX_KEY 삭제
     * QueueService에서 사용자가 직접 취소하거나 주문 완료 시 호출
     * @param productId
     * @param tokenId
     * @param userId
     */
    void removeTokenWithUserIdxKey(UUID productId, TokenId tokenId, Long userId);

    /**
     * 상품 품절 처리 (Kafka 수신 시 호출)
     * @param productId
     */
    void setSoldOut(UUID productId, String status, LocalDateTime dealEndTime);

    /**
     * 품절 여부 확인 (대기열 진입 시 호출)
     * @param productId
     * @return
     */
    boolean isSoldOut(UUID productId);

    /**
     * USER_INDEX_KEY 에서 기존 발급된 토큰 조회 (재진입 시 사용)
     * @return 기존 토큰 (없으면 null)
     */
    String findExistingTokenForUser(UUID productId, Long userId);
}
