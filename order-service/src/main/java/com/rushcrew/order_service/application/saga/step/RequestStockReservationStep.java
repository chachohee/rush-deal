package com.rushcrew.order_service.application.saga.step;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.order_service.application.command.dto.command.CreateOrderCommand;
import com.rushcrew.order_service.application.port.out.OutboxPort;
import com.rushcrew.order_service.application.port.out.StockEventPort;
import com.rushcrew.order_service.application.port.out.TimeDealStockPort;
import com.rushcrew.order_service.application.saga.dto.OrderCreationSagaData;
import com.rushcrew.order_service.application.saga.dto.SagaContext;
import com.rushcrew.order_service.application.saga.dto.SagaStepResult;
import com.rushcrew.order_service.domain.enums.SagaStepName;
import com.rushcrew.order_service.infrastructure.messaging.event.OutboxEventType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestStockReservationStep {

	private final OutboxPort outboxPort;
	private final ObjectMapper objectMapper;
	private final StockEventPort stockEventPort;

	public SagaStepResult execute(SagaContext context, OrderCreationSagaData data) {
		log.info("[Saga-{}] Step 3: {} 시작",
			context.getSagaId(), SagaStepName.REQUEST_STOCK_RESERVATION.getDescription());
		try {
			CreateOrderCommand command = data.getCommand();

			// 페이로드 구성
			StockReservationRequestPayload payload = StockReservationRequestPayload.builder()
				.sagaId(context.getSagaId().toString())
				.userId(command.userId())
				.timeDealId(command.timeDealId().toString())
				.productId(command.productId().toString())
				.orderItems(command.orderItems().stream()
					.map(item -> new StockReservationRequestPayload.StockItem(
						item.timeDealStockId().toString(),
						item.quantity()
					))
					.toList())
				.build();

			log.info("[Saga-{}] Step 3: Outbox 이벤트 저장 (itemCount={})",
				context.getSagaId(), command.orderItems().size());

			outboxPort.createAndSave(
				"ORDER_SAGA",
				context.getSagaId(),
				OutboxEventType.STOCK_RESERVATION_REQUESTED,
				objectMapper.writeValueAsString(payload)
			);

			log.info("[Saga-{}] Step 3: {} 완료 (비동기 대기)",
				context.getSagaId(), SagaStepName.REQUEST_STOCK_RESERVATION.getDescription());
			return SagaStepResult.success();

		} catch (Exception e) {
			log.error("[Saga-{}] Step 3: {} 실패 - {}",
				context.getSagaId(), SagaStepName.REQUEST_STOCK_RESERVATION.getDescription(),
				e.getMessage(), e);
			return SagaStepResult.failure("재고 예약 요청 실패: " + e.getMessage());
		}
	}

	public void compensate(SagaContext context, OrderCreationSagaData data) {
		log.info("[Saga-{}] 보상: {} 시작",
			context.getSagaId(), SagaStepName.REQUEST_STOCK_RESERVATION_COMPENSATE.getDescription());
		try {
			CreateOrderCommand command = data.getCommand();

			// 각 주문 아이템에 대해 재고 예약 취소 이벤트 발행
			command.orderItems().forEach(item -> {
				try {
					log.info("[Saga-{}] 보상: 재고 예약 취소 요청 (timeDealStockId={}, quantity={})",
						context.getSagaId(), item.timeDealStockId(), item.quantity());

					stockEventPort.publishStockReservationCancelled(
						data.getOrderId(),
						item.timeDealStockId(),
						item.quantity(),
						"Saga 보상 트랜잭션"
					);

					log.info("[Saga-{}] 보상: 재고 예약 취소 완료 (timeDealStockId={})",
						context.getSagaId(), item.timeDealStockId());

				} catch (Exception e) {
					log.error("[Saga-{}] 보상: 재고 예약 취소 실패 (timeDealStockId={}) - {}",
						context.getSagaId(), item.timeDealStockId(), e.getMessage(), e);
					throw e;
				}
			});

			log.info("[Saga-{}] 보상: {} 완료",
				context.getSagaId(), SagaStepName.REQUEST_STOCK_RESERVATION_COMPENSATE.getDescription());

		} catch (Exception e) {
			log.error("[Saga-{}] 보상: {} 실패 - {}",
				context.getSagaId(), SagaStepName.REQUEST_STOCK_RESERVATION_COMPENSATE.getDescription(),
				e.getMessage(), e);
			throw new RuntimeException("재고 예약 취소 실패", e);
		}
	}

	@Getter
	@Builder
	public static class StockReservationRequestPayload {
		private String sagaId;
		private Long userId;
		private String timeDealId;
		private String productId;
		private java.util.List<StockItem> orderItems;

		@Getter
		@AllArgsConstructor
		public static class StockItem {
			private String timeDealStockId;
			private Long quantity;
		}
	}
}
