package com.rushcrew.user_service.point.domain.repository;

import com.rushcrew.user_service.point.domain.entity.PointHistory;
import com.rushcrew.user_service.point.domain.enums.PointType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PointHistoryQueryRepository {

	// 사용자의 가장 최신 포인트 이력(적립/사용/취소 포함)을 조회
	Optional<PointHistory> findLatestByUserId(Long userId);

	// 해당 주문에 대해 이미 확정 적립 이력이 존재하는지 확인
	boolean existsEarnedHistoryForOrderId(String orderId);

	boolean existsBySagaId(String sagaId);

	// 해당 주문에 대해 어떤 포인트 이력이라도 존재하는지 확인
	boolean existsHistoryByOrderId(String orderId);

	// 해당 주문과 관련된 모든 포인트 이력을 생성 시각 기준 오름차순으로 조회
	List<PointHistory> findAllByOrderId(String orderId);

	// 배치 처리용: 특정 시점 이전의 미확정 포인트 이력을 타입/유저 기준으로 조회
	List<PointHistory> findPendingHistories(PointType type, LocalDateTime threshold, int limit);

	// 배치 처리용: 전달받은 이력 ID들을 한 번에 업데이트
	void bulkUpdateToConfirmed(List<UUID> ids, LocalDateTime confirmTime);

	/**
	 * 특정 주문의 특정 타입 포인트 이력 조회
	 *
	 * @param orderId 주문 ID
	 * @param type 포인트 타입 (예: USE_PENDING)
	 * @return 해당하는 포인트 이력 목록
	 */
	List<PointHistory> findByOrderIdAndType(String orderId, PointType type);
}