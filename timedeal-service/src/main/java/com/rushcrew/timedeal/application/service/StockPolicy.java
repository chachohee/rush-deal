package com.rushcrew.timedeal.application.service;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.timedeal.domain.entity.StockLog;
import com.rushcrew.timedeal.domain.entity.TimeDealStock;
import com.rushcrew.timedeal.domain.exception.TimeDealErrorCode;
import com.rushcrew.timedeal.domain.repository.StockRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockPolicy {

	private final StockRepository stockRepository;

	public TimeDealStock getStockOrThrow(UUID stockId) {
		return stockRepository.findNotDeletedById(stockId)
			.orElseThrow(() -> new BusinessException(TimeDealErrorCode.NOT_FOUND_STOCK));
	}

	/**
	 * ✅ 마지막 재고 로그 조회 (멱등성 보장)
	 * - 로그가 없으면 BusinessException 발생
	 * - Consumer에서 이 예외를 잡아 이미 처리된 것으로 간주
	 */
	public StockLog getLastLogOrThrow(UUID stockId, UUID orderId) {
		Optional<StockLog> logOpt = stockRepository.findLastByStockIdAndOrderId(stockId, orderId);

		if (logOpt.isEmpty()) {
			log.warn("StockLog not found (may be already processed): stockId={}, orderId={}",
				stockId, orderId);
			throw new BusinessException(TimeDealErrorCode.NOT_FOUND_ORDER);
		}

		return logOpt.get();
	}

	/**
	 * ✅ 마지막 재고 로그 조회 (Optional 반환)
	 * - 멱등성 보장을 위해 Optional 반환
	 * - 서비스 레이어에서 직접 처리 가능
	 */
	public Optional<StockLog> getLastLog(UUID stockId, UUID orderId) {
		return stockRepository.findLastByStockIdAndOrderId(stockId, orderId);
	}
}
