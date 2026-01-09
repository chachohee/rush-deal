package com.rushcrew.timedeal.application.service.impl;

import com.rushcrew.common.enums.UserRole;
import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.common.global.error.CommonErrorCode;
import com.rushcrew.timedeal.application.command.ConfirmStockCommand;
import com.rushcrew.timedeal.application.command.CreateStockCommand;
import com.rushcrew.timedeal.application.command.ReserveStockCommand;
import com.rushcrew.timedeal.application.command.RestoreStockCommand;
import com.rushcrew.timedeal.application.command.UpdateStockCountCommand;
import com.rushcrew.timedeal.application.event.StockChangedEvent;
import com.rushcrew.timedeal.application.event.StockCreatedEvent;
import com.rushcrew.timedeal.application.event.StockDeletedEvent;
import com.rushcrew.timedeal.application.event.StockRestoredEvent;
import com.rushcrew.timedeal.application.port.out.event.StockReservationFailedEvent;
import com.rushcrew.timedeal.application.port.out.event.StockReservedEvent;
import com.rushcrew.timedeal.application.port.out.event.StockSoldOutEvent;
import com.rushcrew.timedeal.application.result.ConfirmStockResult;
import com.rushcrew.timedeal.application.result.CreateStockResult;
import com.rushcrew.timedeal.application.result.ReserveStockResult;
import com.rushcrew.timedeal.application.result.StockLogResult;
import com.rushcrew.timedeal.application.result.StockResult;
import com.rushcrew.timedeal.application.result.UpdateStockCountResult;
import com.rushcrew.timedeal.application.service.RetryStockService;
import com.rushcrew.timedeal.application.service.StockPolicy;
import com.rushcrew.timedeal.application.service.StockService;
import com.rushcrew.timedeal.domain.entity.StockLog;
import com.rushcrew.timedeal.domain.entity.TimeDeal;
import com.rushcrew.timedeal.domain.entity.TimeDealProduct;
import com.rushcrew.timedeal.domain.entity.TimeDealStock;
import com.rushcrew.timedeal.domain.exception.TimeDealErrorCode;
import com.rushcrew.timedeal.domain.port.StockCache;
import com.rushcrew.timedeal.domain.repository.StockRepository;
import com.rushcrew.timedeal.domain.repository.TimeDealRepository;
import com.rushcrew.timedeal.domain.vo.EventType;
import com.rushcrew.timedeal.domain.vo.OrderId;
import com.rushcrew.timedeal.domain.vo.TimeDealStockStatus;
import jakarta.persistence.OptimisticLockException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private final TimeDealRepository timeDealRepository;
    private final StockRepository stockRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StockCache stockCache;

    private final StockPolicy stockPolicy;
    private final RetryStockService retryStockService;

    @Override
    @Transactional
    public CreateStockResult createStock(CreateStockCommand command) {
        TimeDealProduct timeDealProduct =
            timeDealRepository.findProductByProductId(command.productId())
                .orElseThrow(() -> new BusinessException(TimeDealErrorCode.NOT_FOUND_PRODUCT));

        TimeDealStock newStock = TimeDealStock.create(command, timeDealProduct);
        stockRepository.save(newStock);

        eventPublisher.publishEvent(new StockCreatedEvent(newStock.getId(), command.totalStock()));

        return CreateStockResult.of(newStock, command.totalStock());
    }

    @Override
    @Transactional
    public UpdateStockCountResult changeStockCount(UUID stockId, UpdateStockCountCommand command) {
        TimeDealStock stock = stockPolicy.getStockOrThrow(stockId);
        Long previousStock = stock.getStockCounts().getAvailable();

        Long quantity = command.quantity().getQuantity();
        stock.validQuantity(quantity);

        stock.changeAvailable(quantity, command.reason());

        eventPublisher.publishEvent(new StockChangedEvent(stockId, quantity));

        return UpdateStockCountResult.of(
            stock.getId(), stock.getTimeDealProduct().getId(),
            previousStock, stock.getStockCounts().getAvailable(), quantity
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockResult> getStocks(
        String keyword, UUID productId, TimeDealStockStatus status, Pageable pageable
    ) {
        String pattern = (keyword == null || keyword.isBlank()) ? null : "%" + keyword + "%";
        return stockRepository.findStockResults(pattern, productId, status, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public StockResult getStock(Long userId, String role, UUID stockId) {
        StockResult result = stockRepository.findStockResultById(stockId);
        // if (role.equals(UserRole.SELLER.getDescription())
        //     && !Objects.equals(userId, result.sellerId())) {
        //     throw new BusinessException(CommonErrorCode.FORBIDDEN);
        // }
         return result;
    }

    @Override
    @Transactional
    public void deleteStock(UUID stockId, Long userId) {
        TimeDealStock stock = stockPolicy.getStockOrThrow(stockId);

        stock.delete(userId);
        eventPublisher.publishEvent(new StockDeletedEvent(stockId));
    }

    @Override
    public ReserveStockResult reserveStock(ReserveStockCommand command) {
        UUID stockId = command.stockId();
        Long quantity = command.quantity().getQuantity();

        if (!stockCache.decrease(stockId, quantity)) {
            throw new BusinessException(TimeDealErrorCode.OUT_OF_STOCK);
        }

        try {
            return retryStockService.reserveWithRetry(command);
        } catch (OptimisticLockException e) {
            stockCache.increase(stockId, quantity);
            throw e;
        } catch (Exception e) {
            stockCache.increase(stockId, quantity);
            throw e;
        }
    }

    @Override
    @Transactional
    public ConfirmStockResult confirmStock(ConfirmStockCommand command) {
        UUID orderId = command.orderId().getOrderId();
        TimeDealStock stock = stockPolicy.getStockOrThrow(command.stockId());
        StockLog log = stockPolicy.getLastLogOrThrow(command.stockId(), orderId);
        log.validateOrderQuantity(command.quantity());

        stock.confirm(OrderId.of(orderId), command.quantity());

        return ConfirmStockResult.of(orderId);
    }

    @Override
    @Transactional
    public void restoreStock(RestoreStockCommand command) {
        UUID orderId = command.orderId().getOrderId();
        TimeDealStock stock = stockPolicy.getStockOrThrow(command.stockId());
        StockLog log = stockPolicy.getLastLogOrThrow(command.stockId(), orderId);
        log.validateOrderQuantity(command.quantity());

        if (log.getEventType() == EventType.RESERVE) {
            stock.restoreFromReserved(OrderId.of(orderId), command.quantity(), command.reason());
        } else if (log.getEventType() == EventType.SELL) {
            stock.restoreFromSold(OrderId.of(orderId), command.quantity(), command.reason());
        } else {
            throw new BusinessException(TimeDealErrorCode.INVALID_ORDER_STATE);
        }

        eventPublisher.publishEvent(
            new StockRestoredEvent(command.stockId(), command.quantity().getQuantity())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockLogResult> getStockLogs(UUID stockId, String eventType, Pageable pageable) {
        return stockRepository.findLogByIdAndFilter(stockId, eventType, pageable);
    }

	@Override
	@Retryable(
		retryFor = {ObjectOptimisticLockingFailureException.class},
		maxAttempts = 5,
		backoff = @Backoff(delay = 5, maxDelay = 20, multiplier = 2)
	)
	@Transactional
	public void reserveStocksBatch(List<ReserveStockCommand> commands) {
		if (commands.isEmpty()) {
			throw new IllegalArgumentException("재고 예약 명령이 비어있습니다");
		}

		String sagaId = commands.get(0).orderId().getOrderId().toString();

		try {
			log.info("[Saga-{}] 배치 재고 예약 시작: itemCount={}", sagaId, commands.size());

			// 1. 모든 재고 조회 (비관적 락)
			List<UUID> stockIds = commands.stream()
				.map(ReserveStockCommand::stockId)
				.toList();

			List<TimeDealStock> stocks = stockRepository.findStocksForReservation(stockIds);

			if (stocks.size() != stockIds.size()) {
				throw new BusinessException(TimeDealErrorCode.NOT_FOUND_STOCK);
			}

			// 2. 재고 검증 (모든 옵션이 충분한지 확인)
			List<StockValidationResult> validationResults = new ArrayList<>();

			for (ReserveStockCommand command : commands) {
				TimeDealStock stock = stocks.stream()
					.filter(s -> s.getId().equals(command.stockId()))
					.findFirst()
					.orElseThrow(() -> new BusinessException(TimeDealErrorCode.NOT_FOUND_STOCK));

				long available = stock.getStockCounts().getAvailable();
				long requested = command.quantity().getQuantity();
				boolean isAvailable = available >= requested;

				validationResults.add(new StockValidationResult(
					stock.getId().toString(),
					stock.getTimeDealProduct().getItemIds().getOptionId().toString(),
					requested,
					available,
					isAvailable
				));
			}

			// 3. 하나라도 재고 부족이면 전체 실패
			List<StockValidationResult> insufficientStocks = validationResults.stream()
				.filter(r -> !r.isAvailable())
				.toList();

			if (!insufficientStocks.isEmpty()) {
				String errorMsg = "재고 부족: " + insufficientStocks.stream()
					.map(r -> String.format("옵션 %s (요청:%d, 재고:%d)",
						r.optionId(), r.requestedQty(), r.availableQty()))
					.collect(Collectors.joining(", "));

				log.error("[Saga-{}] {}", sagaId, errorMsg);

				// 실패 이벤트 발행
				eventPublisher.publishEvent(
					StockReservationFailedEvent.of(
						sagaId,
						commands.get(0).stockId().toString(),
						errorMsg
					)
				);

				throw new BusinessException(TimeDealErrorCode.OUT_OF_STOCK);
			}

			log.info("[Saga-{}] 재고 검증 완료: 모든 옵션 충분", sagaId);

			// 4. 모든 재고가 충분함 → 실제 예약 수행
			List<StockReservedEvent.ReservedStockItem> reservedItems = new ArrayList<>();

			for (ReserveStockCommand command : commands) {
				TimeDealStock stock = stocks.stream()
					.filter(s -> s.getId().equals(command.stockId()))
					.findFirst()
					.orElseThrow();

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

				TimeDealProduct product = stock.getTimeDealProduct();
				TimeDeal timeDeal = product.getTimeDeal();
				BigDecimal discountPrice = BigDecimal.valueOf(timeDeal.getPrice().getAmount());

				reservedItems.add(new StockReservedEvent.ReservedStockItem(
					stock.getId().toString(),
					product.getItemIds().getProductId().toString(),
					product.getItemIds().getOptionId().toString(),
					command.quantity().getQuantity(),
					discountPrice
				));

				log.debug("[Saga-{}] 재고 예약 완료: stockId={}, quantity={}",
					sagaId, stock.getId(), command.quantity().getQuantity());
			}

			// 5. 성공 이벤트 발행 (전체 성공)
			TimeDeal timeDeal = stocks.get(0).getTimeDealProduct().getTimeDeal();
			eventPublisher.publishEvent(
				StockReservedEvent.of(
					sagaId,
					timeDeal.getId().toString(),
					reservedItems
				)
			);

			log.info("[Saga-{}] 배치 재고 예약 완료: itemCount={}", sagaId, reservedItems.size());

		} catch (BusinessException e) {
			log.error("[Saga-{}] 배치 재고 예약 실패 (비즈니스 예외): {}", sagaId, e.getMessage());
			throw e;

		} catch (Exception e) {
			String errorMsg = "재고 예약 중 오류 발생: " + e.getMessage();
			log.error("[Saga-{}] 배치 재고 예약 실패: {}", sagaId, errorMsg, e);

			eventPublisher.publishEvent(
				StockReservationFailedEvent.of(
					sagaId,
					commands.get(0).stockId().toString(),
					errorMsg
				)
			);

			throw new RuntimeException("재고 예약 실패", e);
		}
	}

	/**
	 * 재고 검증 결과 DTO
	 */
	private record StockValidationResult(
		String stockId,
		String optionId,
		Long requestedQty,
		Long availableQty,
		boolean isAvailable
	) {}
}
