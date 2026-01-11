package com.rushcrew.order_service.application.saga.handler;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.rushcrew.order_service.application.port.out.MetricsPort;
import com.rushcrew.order_service.application.port.out.QueueEventPort;
import com.rushcrew.order_service.application.port.out.SagaInstancePort;
import com.rushcrew.order_service.application.saga.dto.OrderCreationSagaData;
import com.rushcrew.order_service.application.saga.dto.SagaContext;
import com.rushcrew.order_service.application.saga.step.CreateOrderStep;
import com.rushcrew.order_service.application.saga.step.RequestStockReservationStep;
import com.rushcrew.order_service.application.saga.step.UsePointStep;
import com.rushcrew.order_service.domain.enums.SagaStatus;
import com.rushcrew.order_service.domain.enums.SagaStepName;
import com.rushcrew.order_service.domain.model.saga.SagaInstance;
import com.rushcrew.order_service.domain.vo.ProductSnapshot;
import com.rushcrew.order_service.infrastructure.messaging.event.StockReservationFailedEvent;
import com.rushcrew.order_service.infrastructure.messaging.event.StockReservedEvent;
import com.rushcrew.order_service.infrastructure.messaging.event.StockRestoreFailedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaEventHandler {

	private final SagaInstancePort sagaInstancePort;
	private final UsePointStep usePointStep;
	private final RequestStockReservationStep requestStockReservationStep;
	private final CreateOrderStep createOrderStep;
	private final MetricsPort metricsPort;
	private final QueueEventPort queueEventPort;

	@Transactional
	public void handleStockReserved(StockReservedEvent event) {
		SagaInstance saga = sagaInstancePort.findBySagaId(event.sagaId());

		if (saga.isCompleted() || saga.isFailed()) {
			log.warn("[Saga-{}] 이미 완료 또는 실패한 Saga입니다. 현재 상태: {}",
				event.sagaId(), saga.getStatus());
			return;
		}

		// 반드시 restore
		SagaContext context = SagaContext.restore(saga);
		OrderCreationSagaData data = saga.restoreData();

		// 🔴 여기서 스냅샷 구성
		List<ProductSnapshot> snapshots =
			event.reservedItems().stream()
				.map(item -> ProductSnapshot.builder()
					.timeDealStockId(item.timeDealStockId())
					.productId(item.productId())
					.optionId(item.optionId())
					.originalPrice(item.discountedPrice()) // 임시
					.timeDealId(event.timeDealId())

					// ❗ 지금은 못 채우는 필드들
					.productName(null)
					.productDescription(null)
					.optionName(null)
					.sellerId(null)
					.sellerName(null)
					.discountRate(null)
					.category(null)
					.timeDealTitle(null)
					.build()
				)
				.toList();

		data.setProductSnapshots(snapshots);

		try {
			// Step 4: 주문 생성
			createOrderStep.execute(context, data, event);
			saga.addStep(SagaStepName.CREATE_ORDER, SagaStatus.COMPLETED);

			saga.complete();
			sagaInstancePort.save(saga);

			// 주문 생성 완료 후 토큰 만료 이벤트 발행
			publishTokenRemoveEvent(data);

			metricsPort.recordSagaSuccess();
			log.info("[Saga-{}] 주문 생성 완료", event.sagaId());

		} catch (Exception e) {
			log.error("[Saga-{}] 주문 생성 실패: {}", event.sagaId(), e.getMessage(), e);
			// 재고 예약 성공 상태 마킹 (보상 대상임을 명시)
			saga.addStep(SagaStepName.REQUEST_STOCK_RESERVATION, SagaStatus.COMPLETED);
			// 보상 트랜잭션 실행
			executeCompensation(saga, context, data, "주문 생성 실패: " + e.getMessage());
			throw e;
		}
	}

	@Transactional
	public void handleStockReservationFailed(StockReservationFailedEvent event) {
		SagaInstance saga = sagaInstancePort.findBySagaId(event.sagaId());

		if (saga.isCompleted() || saga.isFailed()) {
			log.warn("[Saga-{}] 이미 완료 또는 실패한 Saga입니다. 현재 상태: {}",
				event.sagaId(), saga.getStatus());
			return;
		}

		SagaContext context = SagaContext.restore(saga);
		OrderCreationSagaData data = saga.restoreData();

		log.error("[Saga-{}] 재고 예약 실패: orderId{}, 이유:{}", event.sagaId(), event.orderId(), event.reason());

		// 보상 트랜잭션 실행
		executeCompensation(saga, context, data, "재고 예약 실패: " + event.reason());
	}

	/**
	 * 보상 트랜잭션 실행
	 */
	private void executeCompensation(
		SagaInstance saga,
		SagaContext context,
		OrderCreationSagaData data,
		String failureReason
	) {
		log.info("[Saga-{}] 보상 트랜잭션 시작", saga.getSagaId());

		try {
			// 1. 재고 복구 (REQUEST_STOCK_RESERVATION이 완료된 경우만)
			if (saga.hasCompletedStep(SagaStepName.REQUEST_STOCK_RESERVATION)) {
				log.info("[Saga-{}] 재고 예약 취소 실행", saga.getSagaId());
				requestStockReservationStep.compensate(context, data);
				saga.addStep(SagaStepName.REQUEST_STOCK_RESERVATION_COMPENSATE, SagaStatus.COMPLETED);
			}

			// 2. 포인트 복구 (USE_POINT가 완료된 경우)
			if (saga.hasCompletedStep(SagaStepName.USE_POINT)) {
				log.info("[Saga-{}] 포인트 보상 트랜잭션 실행", saga.getSagaId());
				usePointStep.compensate(context, data);
				saga.addStep(SagaStepName.USE_POINT_COMPENSATE, SagaStatus.COMPLETED);
			}

			saga.fail(failureReason);
			sagaInstancePort.save(saga);
			metricsPort.recordSagaFailure();

			log.info("[Saga-{}] 보상 트랜잭션 완료", saga.getSagaId());

		} catch (Exception compensateError) {
			log.error("[Saga-{}] 보상 트랜잭션 실패: {}",
				saga.getSagaId(), compensateError.getMessage(), compensateError);

			saga.fail("보상 트랜잭션 실패: " + compensateError.getMessage());
			sagaInstancePort.save(saga);
			metricsPort.recordSagaFailure();

			// 보상 실패는 별도 모니터링/알림 필요
			throw new RuntimeException("보상 트랜잭션 실패", compensateError);
		}
	}

	/* 토큰 만료 이벤트 발행 */
	private void publishTokenRemoveEvent(OrderCreationSagaData data) {
		try {
			if (data.getQueueToken() != null) {
				queueEventPort.publishTokenRemoveEvent(
					data.getCommand().userId(),
					data.getCommand().productId(),
					data.getQueueToken()
				);
				log.info("[Saga] 토큰 만료 이벤트 Outbox 저장 완료 - UserId: {}",
					data.getCommand().userId());
			} else {
				log.warn("[Saga] 토큰 정보가 없어 이벤트 발행 생략");
			}
		} catch (Exception e) {
			log.error("[Saga] 토큰 만료 이벤트 Outbox 저장 실패 (주문은 성공)", e);
		}
	}

	@Transactional
	public void handleStockRestoreFailed(StockRestoreFailedEvent event) {
		SagaInstance saga = sagaInstancePort.findBySagaId(event.sagaId());

		if (saga.isCompleted() || saga.isFailed()) {
			log.warn("[Saga-{}] 이미 완료 또는 실패한 Saga입니다. 현재 상태: {}",
				event.sagaId(), saga.getStatus());
			return;
		}

		log.error("[Saga-{}] 재고 복구 실패: orderId={}, stockId={}, reason={}",
			event.sagaId(), event.orderId(), event.stockId(), event.reason());

		// 재고 복구 실패는 심각한 상황이므로 별도 처리 필요
		// 1. Saga 상태를 FAILED로 마킹
		saga.addStep(SagaStepName.REQUEST_STOCK_RESERVATION_COMPENSATE, SagaStatus.FAILED);
		saga.fail("재고 복구 실패: " + event.reason());
		sagaInstancePort.save(saga);

		// 2. 메트릭 기록
		metricsPort.recordSagaFailure();

		// 3. 알림/모니터링 (선택사항)
		log.error("[CRITICAL][Saga-{}] 재고 복구 실패 - 수동 확인 필요! orderId={}, stockId={}",
			event.sagaId(), event.orderId(), event.stockId());
	}
}
