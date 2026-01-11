package com.rushcrew.timedeal.application.service.impl;

import com.rushcrew.common.exception.BusinessException;
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
import com.rushcrew.timedeal.application.port.out.event.StockRestoreFailedEvent;
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
import java.util.Optional;
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
		StockLog stockLog = stockPolicy.getLastLogOrThrow(command.stockId(), orderId);
		stockLog.validateOrderQuantity(command.quantity());

		stock.confirm(OrderId.of(orderId), command.quantity());

		return ConfirmStockResult.of(orderId);
	}

	/**
	 * ✅ 개별 재고 복구 (현재 사용 중)
	 * - Kafka 메시지가 개별 재고 단위로 도착
	 * - 멱등성 보장: 로그 없으면 이미 처리된 것으로 간주
	 */
	@Override
	@Transactional
	public void restoreStock(RestoreStockCommand command) {
		UUID orderId = command.orderId().getOrderId();
		UUID stockId = command.stockId();

		try {
			log.debug("[Restore] 재고 복구 시작: orderId={}, stockId={}", orderId, stockId);

			TimeDealStock stock = stockPolicy.getStockOrThrow(stockId);

			// ✅ 로그 조회 - 없으면 이미 처리된 것으로 간주
			Optional<StockLog> stockLogOpt = stockRepository.findLastByStockIdAndOrderId(stockId, orderId);

			if (stockLogOpt.isEmpty()) {
				log.warn("[Restore][Idempotent] 재고 로그 없음 (이미 처리됨): orderId={}, stockId={}",
					orderId, stockId);
				return;  // ✅ 멱등성: 이미 처리됨
			}

			StockLog stockLog = stockLogOpt.get();

			// ✅ 이미 취소된 경우
			if (stockLog.getEventType() == EventType.RESERVE_CANCEL ||
				stockLog.getEventType() == EventType.PAYMENT_CANCEL ||
				stockLog.getEventType() == EventType.ROLLBACK) {
				log.warn("[Restore][Idempotent] 이미 취소됨: orderId={}, stockId={}, eventType={}",
					orderId, stockId, stockLog.getEventType());
				return;  // ✅ 멱등성: 이미 취소됨
			}

			// ✅ 수량 검증
			stockLog.validateOrderQuantity(command.quantity());

			// ✅ EventType의 applyRestore() 활용
			stockLog.getEventType().applyRestore(
				stock,
				command.orderId(),
				command.quantity(),
				command.reason()
			);

			// ✅ 복구 이벤트 발행
			eventPublisher.publishEvent(
				new StockRestoredEvent(stockId, command.quantity().getQuantity())
			);

			log.info("[Restore] 재고 복구 완료: orderId={}, stockId={}, quantity={}",
				orderId, stockId, command.quantity().getQuantity());

		} catch (BusinessException e) {
			if (e.getErrorCode() == TimeDealErrorCode.NOT_FOUND_ORDER) {
				// 멱등성: 로그가 없으면 이미 처리된 것으로 간주
				log.warn("[Restore][Idempotent] 재고 로그 없음 (예외): orderId={}, stockId={}",
					orderId, stockId);
				return;
			}
			log.error("[Restore] 재고 복구 실패 (비즈니스 예외): orderId={}, stockId={}, error={}",
				orderId, stockId, e.getMessage());
			throw e;

		} catch (Exception e) {
			log.error("[Restore] 재고 복구 실패: orderId={}, stockId={}, error={}",
				orderId, stockId, e.getMessage(), e);
			throw e;
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Page<StockLogResult> getStockLogs(UUID stockId, String eventType, Pageable pageable) {
		return stockRepository.findLogByIdAndFilter(stockId, eventType, pageable);
	}

	/**
	 * ✅ 배치 재고 예약 (현재 사용 중)
	 * - 하나의 주문에 여러 재고를 한 번에 예약
	 * - 하나라도 부족하면 전체 실패
	 */
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

		String sagaId = commands.get(0).sagaId().toString();
		String orderId = commands.get(0).orderId().getOrderId().toString();

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
						orderId,
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

				// 재고 예약 (StockLog는 엔티티 내부에서 자동 생성)
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
					orderId,
					commands.get(0).stockId().toString(), // TODO: 어떤 재고가 품절이었는지
					errorMsg
				)
			);

			throw new RuntimeException("재고 예약 실패", e);
		}
	}

	@Override
	@Transactional
	public void restoreStocksBatch(List<RestoreStockCommand> commands) {
		if (commands.isEmpty()) {
			log.warn("재고 복구 명령이 비어있습니다");
			return;
		}

		UUID sagaId = commands.get(0).sagaId();
		UUID orderId = commands.get(0).orderId().getOrderId();

		try {
			log.info("[Saga-{}] 배치 재고 복구 시작: itemCount={}", sagaId, commands.size());

			// 1. 모든 재고 ID 수집
			List<UUID> stockIds = commands.stream()
				.map(RestoreStockCommand::stockId)
				.toList();

			// 2. 모든 재고 조회 (비관적 락)
			List<TimeDealStock> stocks = stockRepository.findStocksForReservation(stockIds);

			if (stocks.size() != stockIds.size()) {
				String errorMsg = String.format("일부 재고를 찾을 수 없음: expected=%d, found=%d",
					stockIds.size(), stocks.size());
				log.error("[Saga-{}] {}", sagaId, errorMsg);

				// ✅ 실패 이벤트 발행
				eventPublisher.publishEvent(
					StockRestoreFailedEvent.of(
						sagaId.toString(),
						orderId.toString(),
						commands.get(0).stockId().toString(),
						errorMsg
					)
				);

				throw new BusinessException(TimeDealErrorCode.NOT_FOUND_STOCK);
			}

			int successCount = 0;
			int skippedCount = 0;
			List<UUID> restoredStockIds = new ArrayList<>();
			List<String> failureReasons = new ArrayList<>();

			for (RestoreStockCommand command : commands) {
				UUID stockId = command.stockId();

				try {
					TimeDealStock stock = stocks.stream()
						.filter(s -> s.getId().equals(stockId))
						.findFirst()
						.orElseThrow(() -> new BusinessException(TimeDealErrorCode.NOT_FOUND_STOCK));

					// ✅ 디버깅: 쿼리 실행 전 파라미터 로그
					log.info("[Saga-{}] 재고 로그 조회 시도: stockId={}, orderId={}",
						sagaId, stockId, orderId);

					// ✅ 로그 조회
					Optional<StockLog> stockLogOpt = stockRepository.findLastByStockIdAndOrderId(
						stockId,
						orderId
					);

					// ✅ 디버깅: 조회 결과 상세 로그
					if (stockLogOpt.isPresent()) {
						StockLog stockLog = stockLogOpt.get();
						log.info("[Saga-{}] ✅ 재고 로그 발견: stockId={}, orderId={}, logId={}, eventType={}, quantity={}, createdAt={}",
							sagaId, stockId, orderId, stockLog.getId(), stockLog.getEventType(),
							stockLog.getQuantity().getQuantity(), stockLog.getCreatedAt());
					} else {
						log.error("[Saga-{}] ❌ 재고 로그 없음: stockId={}, orderId={}",
							sagaId, stockId, orderId);

						// ✅ 추가: 직접 SQL로 확인 (재확인용)
						log.error("[Saga-{}] 직접 확인 필요 - SQL: " +
								"SELECT * FROM time_deal_schema.p_stock_log " +
								"WHERE time_deal_stock_id = '{}' AND order_id = '{}' " +
								"ORDER BY created_at DESC LIMIT 1",
							sagaId, stockId, orderId);

						skippedCount++;
						continue;
					}

					StockLog stockLog = stockLogOpt.get();
					EventType eventType = stockLog.getEventType();

					// ✅ 이미 취소된 경우 → 스킵
					if (eventType == EventType.RESERVE_CANCEL ||
						eventType == EventType.PAYMENT_CANCEL ||
						eventType == EventType.ROLLBACK) {
						log.warn("[Saga-{}] [Idempotent] 이미 취소됨: stockId={}, orderId={}, eventType={}",
							sagaId, stockId, orderId, eventType);
						skippedCount++;
						continue;
					}

					// ✅ RESERVE 상태만 복구 가능
					if (eventType != EventType.RESERVE) {
						log.warn("[Saga-{}] 복구 불가능한 상태: stockId={}, orderId={}, eventType={}",
							sagaId, stockId, orderId, eventType);
						skippedCount++;
						continue;
					}

					// ✅ 수량 검증
					stockLog.validateOrderQuantity(command.quantity());

					// ✅ 재고 복구 실행
					long beforeAvailable = stock.getStockCounts().getAvailable();
					long beforeReserved = stock.getStockCounts().getReserved();

					stock.restoreFromReserved(
						command.orderId(),
						command.quantity(),
						command.reason()
					);

					long afterAvailable = stock.getStockCounts().getAvailable();
					long afterReserved = stock.getStockCounts().getReserved();

					successCount++;
					restoredStockIds.add(stockId);

					log.info("[Saga-{}] 재고 복구 완료: stockId={}, " +
							"available: {} -> {}, reserved: {} -> {}",
						orderId, stockId,
						beforeAvailable, afterAvailable,
						beforeReserved, afterReserved);

				} catch (BusinessException e) {
					if (e.getErrorCode() == TimeDealErrorCode.NOT_FOUND_ORDER) {
						log.warn("[Saga-{}] [Idempotent] 재고 로그 없음: stockId={}, orderId={}", sagaId, stockId, orderId);
						skippedCount++;
						continue;
					}
					String reason = String.format("stockId=%s, error=%s", stockId, e.getMessage());
					failureReasons.add(reason);
					log.error("[Saga-{}] 재고 복구 실패 (비즈니스 예외): {}", sagaId, reason);
					// 개별 실패는 계속 진행
				} catch (Exception e) {
					String reason = String.format("stockId=%s, error=%s", stockId, e.getMessage());
					failureReasons.add(reason);
					log.error("[Saga-{}] 재고 복구 실패: {}", sagaId, reason, e);
					// 개별 실패는 계속 진행
				}
			}

			// 4. 복구 이벤트 발행 (성공한 것만)
			for (UUID restoredStockId : restoredStockIds) {
				RestoreStockCommand cmd = commands.stream()
					.filter(c -> c.stockId().equals(restoredStockId))
					.findFirst()
					.orElseThrow();

				eventPublisher.publishEvent(
					new StockRestoredEvent(
						restoredStockId,
						cmd.quantity().getQuantity()
					)
				);
			}

			log.info("[Saga-{}] 배치 재고 복구 완료: orderId={}, total={}, success={}, skipped={}",
				sagaId, orderId, commands.size(), successCount, skippedCount);

			if (successCount == 0) {
				if (skippedCount == commands.size()) {
					log.info("[Saga-{}] [Idempotent] 모든 재고가 이미 처리되었습니다", sagaId);
				} else {
					log.warn("[Saga-{}] 복구된 재고가 없습니다", sagaId);

					// ✅ 모두 실패했고 스킵도 아닌 경우 실패 이벤트 발행
					if (!failureReasons.isEmpty()) {
						String errorMsg = "모든 재고 복구 실패: " + String.join(", ", failureReasons);
						eventPublisher.publishEvent(
							StockRestoreFailedEvent.of(
								sagaId.toString(),
								orderId.toString(),
								commands.get(0).stockId().toString(),
								errorMsg
							)
						);
					}
				}
			}

		} catch (BusinessException e) {
			log.error("[Saga-{}] 배치 재고 복구 실패 (비즈니스 예외): {}", sagaId, e.getMessage());

			// ✅ 실패 이벤트 발행
			eventPublisher.publishEvent(
				StockRestoreFailedEvent.of(
					sagaId.toString(),
					orderId.toString(),
					commands.get(0).stockId().toString(),
					e.getMessage()
				)
			);

			throw e;

		} catch (Exception e) {
			String errorMsg = "재고 복구 중 오류 발생: " + e.getMessage();
			log.error("[Saga-{}] 배치 재고 복구 실패: {}", sagaId, errorMsg, e);

			// ✅ 실패 이벤트 발행
			eventPublisher.publishEvent(
				StockRestoreFailedEvent.of(
					sagaId.toString(),
					orderId.toString(),
					commands.get(0).stockId().toString(), // TODO: 실패한 재고 ID 목록들로 변경
					errorMsg
				)
			);

			throw new RuntimeException("재고 복구 실패", e);
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
