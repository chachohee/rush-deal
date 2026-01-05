package com.rushcrew.timedeal.application.service;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.timedeal.application.command.ReserveStockCommand;
import com.rushcrew.timedeal.application.port.out.event.StockReservedEvent;
import com.rushcrew.timedeal.application.port.out.event.StockSoldOutEvent;
import com.rushcrew.timedeal.application.result.ReserveStockResult;
import com.rushcrew.timedeal.domain.entity.TimeDeal;
import com.rushcrew.timedeal.domain.entity.TimeDealProduct;
import com.rushcrew.timedeal.domain.entity.TimeDealStock;
import com.rushcrew.timedeal.domain.exception.TimeDealErrorCode;
import com.rushcrew.timedeal.domain.repository.StockRepository;
import java.math.BigDecimal;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RetryStockService {

    private final StockRepository stockRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Retryable(
        retryFor = {ObjectOptimisticLockingFailureException.class},
        maxAttempts = 5,
        backoff = @Backoff(delay = 5, maxDelay = 20, multiplier = 2)
    )
    @Transactional
    public ReserveStockResult reserveWithRetry(ReserveStockCommand command) {
        TimeDealStock stock = stockRepository.findStockForReservation(command.stockId())
            .orElseThrow(() -> new BusinessException(TimeDealErrorCode.NOT_FOUND_STOCK));

        // 재고 예약
        stock.reserve(command.quantity(), command.orderId());

		// 품절 이벤트
        if (stock.getStockCounts().getAvailable() == 0) {
            eventPublisher.publishEvent(
                new StockSoldOutEvent(
                    stock.getTimeDealProduct().getId(),
                    stock.getStatus().name(),
                    stock.getUpdatedAt()
                )
            );
        }

		// 타임딜 서비스에서 스냅샷 생성을 위해 접근 가능한 정보
		TimeDealProduct product = stock.getTimeDealProduct();
		TimeDeal timeDeal = product.getTimeDeal();
		BigDecimal discountPrice = BigDecimal.valueOf(timeDeal.getPrice().getAmount());

		// 이벤트 발행
		eventPublisher.publishEvent(
			StockReservedEvent.of(
				command.orderId().getOrderId().toString(), // sagaId
				timeDeal.getId().toString(),
				List.of(
					new StockReservedEvent.ReservedStockItem(
						stock.getId().toString(),
						product.getItemIds().getProductId().toString(),
						product.getItemIds().getOptionId().toString(),
						command.quantity().getQuantity(),
						discountPrice
					)
				)
			)
		);

        return ReserveStockResult.of(
            stock.getStockCounts().getAvailable(),
            "재고가 예약되었습니다.",
            discountPrice
        );
    }
}
