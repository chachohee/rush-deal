package com.rushcrew.timedeal.application.service;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.timedeal.application.command.ReserveStockCommand;
import com.rushcrew.timedeal.application.port.out.event.StockReservationFailedEvent;
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
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
		try {
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
			String timeDealTitle = timeDeal.getTimeDealInfo().getTitle();
			Long sellerId = timeDeal.getTimeDealInfo().getSellerId();

			// 성공 이벤트 발행
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
							discountPrice,
							discountPrice, // originalPrice: 타임딜 서비스는 원가 미보유, 할인가로 대체
							timeDealTitle,
							sellerId
						)
					)
				)
			);

			return ReserveStockResult.of(
				stock.getStockCounts().getAvailable(),
				"재고가 예약되었습니다.",
				discountPrice
			);
		} catch (BusinessException e) {
			// 비즈니스 예외 (재고 부족 등) → 실패 이벤트 발행
			String errorMsg = e.getMessage() != null ? e.getMessage() : "재고 예약 실패";
			log.error("[Saga-{}] 재고 예약 실패: {}",
				command.orderId().getOrderId(), errorMsg);

			eventPublisher.publishEvent(
				StockReservationFailedEvent.of(
					command.sagaId().toString(),
					command.orderId().getOrderId().toString(),
					command.stockId().toString(), // stockId를 productId 대신 사용
					errorMsg
				)
			);

			throw e;

		} catch (Exception e) {
			// 기타 예외 → 실패 이벤트 발행
			String errorMsg = "재고 예약 중 오류 발생: " + e.getMessage();
			log.error("[Saga-{}] {}", command.orderId().getOrderId(), errorMsg, e);

			eventPublisher.publishEvent(
				StockReservationFailedEvent.of(
					command.sagaId().toString(),
					command.orderId().getOrderId().toString(),
					command.stockId().toString(),
					errorMsg
				)
			);

			throw new RuntimeException("재고 예약 실패", e);
		}
	}
}
