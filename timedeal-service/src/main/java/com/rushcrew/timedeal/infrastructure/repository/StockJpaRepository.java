package com.rushcrew.timedeal.infrastructure.repository;

import com.rushcrew.timedeal.application.result.StockLogResult;
import com.rushcrew.timedeal.application.result.StockResult;
import com.rushcrew.timedeal.domain.entity.StockLog;
import com.rushcrew.timedeal.domain.entity.TimeDealStock;
import com.rushcrew.timedeal.domain.vo.TimeDealStockStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface StockJpaRepository extends JpaRepository<TimeDealStock, UUID> {

    @Query("""
                  SELECT tds
                  FROM TimeDealStock tds
                  WHERE tds.id = :stockId
                    AND tds.deletedAt IS NULL
        """)
    Optional<TimeDealStock> findNotDeletedById(
        @Param("stockId") UUID stockId
    );

	@Query("""
			SELECT new com.rushcrew.timedeal.application.result.StockResult(
				tds.id,
				tds.itemIds.productId,
				tds.itemIds.optionId,
				tdp.timeDeal.timeDealInfo.sellerId,
				tds.stockCounts.available,
				tds.stockCounts.reserved,
				tds.stockCounts.sold,
				tdp.status,
				tds.updatedAt
			)
			FROM TimeDealStock tds
			JOIN tds.timeDealProduct tdp
			JOIN tdp.timeDeal td
			WHERE (:keyword IS NULL OR CAST(td.timeDealInfo.title AS STRING) LIKE :keyword)
			  AND (:productId IS NULL OR tds.itemIds.productId = :productId)
			  AND (:status IS NULL OR tds.status = :status)
			  AND tds.deletedAt IS NULL
		""")
	Page<StockResult> findStockResults(
		@Param("keyword") String keyword,
		@Param("productId") UUID productId,
		@Param("status") TimeDealStockStatus status,
		Pageable pageable
	);


	@Query("""
			SELECT new com.rushcrew.timedeal.application.result.StockResult(
				tds.id,
				tds.itemIds.productId,
				tds.itemIds.optionId,
				tdp.timeDeal.timeDealInfo.sellerId,
				tds.stockCounts.available,
				tds.stockCounts.reserved,
				tds.stockCounts.sold,
				tdp.status,
				tds.updatedAt
			)
			FROM TimeDealStock tds
			JOIN tds.timeDealProduct tdp
			WHERE tds.id = :stockId
			  AND tds.deletedAt IS NULL
		""")
	StockResult findStockResultById(
		@Param("stockId") UUID stockId
	);

	@Query("""
			SELECT new com.rushcrew.timedeal.application.result.StockResult(
				tds.id,
				tds.itemIds.productId,
				tds.itemIds.optionId,
				tdp.timeDeal.timeDealInfo.sellerId,
				tds.stockCounts.available,
				tds.stockCounts.reserved,
				tds.stockCounts.sold,
				tdp.status,
				tds.updatedAt
			)
			FROM TimeDealStock tds
			JOIN tds.timeDealProduct tdp
			JOIN tdp.timeDeal td
			WHERE tdp.timeDeal.timeDealInfo.sellerId = :sellerId
			  AND tds.stockCounts.available <= :threshold
			  AND td.status IN ('SCHEDULED', 'IN_PROGRESS')
			  AND tds.deletedAt IS NULL
			ORDER BY tds.stockCounts.available ASC
		""")
	List<StockResult> findLowStockBySellerId(
		@Param("sellerId") Long sellerId,
		@Param("threshold") Long threshold,
		Pageable pageable
	);

	@Query(value = """
			SELECT * 
			FROM time_deal_schema.p_stock_log 
			WHERE time_deal_stock_id = :stockId 
			  AND order_id = :orderId 
			ORDER BY created_at DESC 
			LIMIT 1
		""", nativeQuery = true)
	Optional<StockLog> findLastByStockIdAndOrderId(
		@Param("stockId") UUID stockId,
		@Param("orderId") UUID orderId
	);

    @Query("""
                    SELECT new com.rushcrew.timedeal.application.result.StockLogResult(
                                       sl.id,
                                       sl.timeDealStock.id,
                                       sl.orderId.orderId,
                                       sl.eventType,
                                       sl.quantity.quantity,
                                       sl.description
                                  )
                    FROM StockLog sl
                    WHERE (:stockId IS NULL OR sl.timeDealStock.id = :stockId)
                      AND (:eventType IS NULL OR sl.eventType = :eventType)
        """)
    Page<StockLogResult> findLogByIdAndFilter(
        @Param("stockId") UUID stockId,
        @Param("eventType") String eventType,
        Pageable pageable
    );


    @Query("""
                SELECT tds
                FROM TimeDealStock tds
                JOIN FETCH tds.timeDealProduct tdp
                JOIN FETCH tdp.timeDeal td
                WHERE tds.id = :stockId
                  AND tds.deletedAt IS NULL
     """)
    Optional<TimeDealStock> findStockForReservation(@Param("stockId") UUID stockId);

	/**
	 * ✅ 배치 재고 조회 (비관적 락)
	 * - 여러 재고를 한 번에 조회하여 성능 향상
	 * - PESSIMISTIC_WRITE 락으로 동시성 제어
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM TimeDealStock s WHERE s.id IN :ids")
	List<TimeDealStock> findStocksForReservation(@Param("ids") List<UUID> ids);
}
