package com.rushcrew.timedeal.infrastructure.repository;

import com.rushcrew.timedeal.application.result.StockLogResult;
import com.rushcrew.timedeal.application.result.StockResult;
import com.rushcrew.timedeal.domain.entity.StockLog;
import com.rushcrew.timedeal.domain.entity.TimeDealStock;
import com.rushcrew.timedeal.domain.repository.StockRepository;
import com.rushcrew.timedeal.domain.vo.TimeDealStockStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockRepositoryAdapter implements StockRepository {

    private final StockJpaRepository stockJpaRepository;

    @Override
    public void save(TimeDealStock newStock) {
        stockJpaRepository.save(newStock);
    }

    @Override
    public Optional<TimeDealStock> findNotDeletedById(UUID stockId) {
        return stockJpaRepository.findNotDeletedById(stockId);
    }

    @Override
    public Page<StockResult> findStockResults(
        String keyword, UUID productId, TimeDealStockStatus status, Pageable pageable
    ) {
        return stockJpaRepository.findStockResults(keyword, productId, status, pageable);
    }

    @Override
    public StockResult findStockResultById(UUID stockId) {
        return stockJpaRepository.findStockResultById(stockId);
    }

    @Override
    public Optional<StockLog> findLastByStockIdAndOrderId(UUID stockId, UUID orderId) {
        return stockJpaRepository.findLastByStockIdAndOrderId(stockId, orderId);
    }

    @Override
    public Page<StockLogResult> findLogByIdAndFilter(
        UUID stockId, String eventType, Pageable pageable
    ) {
        return stockJpaRepository.findLogByIdAndFilter(stockId, eventType, pageable);
    }

    @Override
    public Optional<TimeDealStock> findStockForReservation(UUID stockId) {
        return stockJpaRepository.findStockForReservation(stockId);
    }

	@Override
	public List<TimeDealStock> findStocksForReservation(List<UUID> stockIds) {
		return stockJpaRepository.findStocksForReservation(stockIds);
	}
}
