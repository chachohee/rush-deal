package com.rushcrew.timedeal.domain.repository;

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

public interface StockRepository {

    void save(TimeDealStock newStock);

    Optional<TimeDealStock> findNotDeletedById(UUID stockId);

    Page<StockResult> findStockResults(
        String keyword, UUID productId, TimeDealStockStatus status, Pageable pageable);

    StockResult findStockResultById(UUID stockId);

    Optional<StockLog> findLastByStockIdAndOrderId(UUID stockId, UUID orderId);

    Page<StockLogResult> findLogByIdAndFilter(UUID stockId, String eventType, Pageable pageable);

    Optional<TimeDealStock> findStockForReservation(UUID stockId);

	List<TimeDealStock> findStocksForReservation(List<UUID> stockIds);
}
