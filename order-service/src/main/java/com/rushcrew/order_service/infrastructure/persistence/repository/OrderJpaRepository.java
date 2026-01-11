package com.rushcrew.order_service.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rushcrew.order_service.domain.enums.OrderStatus;
import com.rushcrew.order_service.domain.model.order.Order;

public interface OrderJpaRepository extends JpaRepository<Order, UUID> {

	/* 사용자의 특정 타임딜 누적 구매 수량 조회 */ // TODO: product_snapshot에 productId 저장
	@Query(value = """
       SELECT COALESCE(SUM(oi.quantity), 0)
       FROM order_schema.p_order_item oi
       JOIN order_schema.p_order o ON oi.order_id = o.order_id
       WHERE o.user_id = :userId
         AND oi.product_snapshot ->> 'productId' = CAST(:productId AS TEXT)
         AND o.status IN ('PAID', 'PURCHASE_CONFIRMED')
   """, nativeQuery = true)
	Long getTotalPurchasedQuantity(
		@Param("userId") Long userId,
		@Param("productId") UUID productId);

	Page<Order> findByUserId(Long userId, Pageable pageable);

	/** 자동 구매확정 대상 조회 --> autoConfirmTargetOrderReader()에서 메서드 네임으로 사용 */
	Page<Order> findAllByStatusAndAutoConfirmScheduledAtBefore(OrderStatus status, Instant scheduledAt, Pageable pageable);



	// ==================== 캐시 워밍 쿼리 ====================

	/**
	 * 최근 주문 ID 목록 조회 (캐시 워밍용)
	 *
	 * 용도:
	 * - CacheWarmingScheduler.warmupCacheOnStartup (최근 24시간)
	 * - CacheWarmingScheduler.refreshHotDataCache (최근 6시간)
	 *
	 * 장점:
	 * - ID만 조회하므로 메모리 효율적
	 * - 실제 데이터는 OrderQueryPort로 조회
	 */
	@Query("""
        SELECT o.orderId 
        FROM Order o 
        WHERE o.orderedAt >= :since 
        ORDER BY o.orderedAt DESC
    """)
	List<UUID> findRecentOrderIds(@Param("since") Instant since);

	/**
	 * 오래된 주문 ID 목록 조회 (Cold Data 정리용)
	 *
	 * 용도:
	 * - CacheWarmingScheduler.cleanupColdDataCache (7일 이상 경과)
	 *
	 * 참고:
	 * - 실제 주문 삭제가 아닌 캐시만 삭제
	 */
	@Query("""
        SELECT o.orderId 
        FROM Order o 
        WHERE o.orderedAt < :before 
        ORDER BY o.orderedAt ASC
    """)
	List<UUID> findOrderIdsBefore(@Param("before") Instant before);

	/**
	 * 타임아웃된 PENDING 주문 조회
	 *
	 * 용도: PendingOrderTimeoutScheduler
	 *
	 * 조건:
	 * - PENDING 상태
	 * - 생성 시간이 기준 시간보다 이전
	 */
	// @Query("""
    //     SELECT o
    //     FROM Order o
    //     WHERE o.status = :status
    //     AND o.orderedAt < :createdBefore
    //     ORDER BY o.orderedAt ASC
    // """)
	// List<Order> findByStatusAndOrderedAtBefore(
	// 	@Param("status") OrderStatus status,
	// 	@Param("createdBefore") Instant createdBefore,
	// 	Pageable pageable
	// );
	@Query("""
    SELECT DISTINCT o 
    FROM Order o 
    LEFT JOIN FETCH o.reservations
    WHERE o.status = :status 
    AND o.orderedAt < :createdBefore 
    ORDER BY o.orderedAt ASC
""")
	List<Order> findByStatusAndOrderedAtBefore(
		@Param("status") OrderStatus status,
		@Param("createdBefore") Instant createdBefore,
		Pageable pageable
	);

}
