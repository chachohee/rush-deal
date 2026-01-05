package com.rushcrew.order_service.application.saga.step;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rushcrew.order_service.application.command.dto.command.CreateOrderCommand;
import com.rushcrew.order_service.application.command.mapper.CommandToDomainMapper;
import com.rushcrew.order_service.application.command.port.out.OrderCommandPort;
import com.rushcrew.order_service.application.port.out.OutboxPort;
import com.rushcrew.order_service.application.saga.dto.OrderCreationSagaData;
import com.rushcrew.order_service.application.saga.dto.SagaContext;
import com.rushcrew.order_service.domain.model.order.Order;
import com.rushcrew.order_service.domain.model.order.OrderItem;
import com.rushcrew.order_service.domain.model.order.OrderReservation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.order_service.domain.vo.ShippingInfo;
import com.rushcrew.order_service.infrastructure.messaging.event.OutboxEventType;
import com.rushcrew.order_service.infrastructure.messaging.event.StockReservedEvent;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateOrderStep {

	private final OrderCommandPort orderCommandPort;
	private final OutboxPort outboxPort;
	private final ObjectMapper objectMapper;
	private final CommandToDomainMapper domainMapper;

	@Transactional
	public void execute(SagaContext context, OrderCreationSagaData data, StockReservedEvent event) {
		log.info("[Saga-{}] CreateOrderStep 시작", context.getSagaId());

		CreateOrderCommand command = data.getCommand();

		// 1. OrderItem 생성 stock.reserved 수신한 StockReservedEvent 사용
		var orderItems = event.reservedItems().stream()
			.map(reservedItem -> OrderItem.create(
				UUID.fromString(reservedItem.timeDealStockId()),
				reservedItem.quantity(),
				reservedItem.discountedPrice(),
				reservedItem.productSnapshot()
			))
			.toList();

		// 2. Command -> Domain VO 변환
		ShippingInfo shippingInfo = domainMapper.toShippingInfo(
			command.shippingInfo()
		);

		// 3. Order 생성
		Order order = Order.create(
			data.getOrderId(),
			command.userId(),
			orderItems,
			command.pointUsed(),
			shippingInfo
		);

		// 4. Saga ID 저장 (구매확정 시 멱등성 보장용)
		order.assignSagaId(context.getSagaId());

		// 5. 재고 예약 정보 추가
		event.reservedItems().forEach(reservedItem ->
			order.addReservation(
				OrderReservation.create(
					UUID.fromString(reservedItem.timeDealStockId()),
					reservedItem.quantity()
				)
			)
		);

		// 6. Order 저장
		Order savedOrder = orderCommandPort.save(order);

		log.info("[Saga-{}] 주문 저장 완료: orderId={}", context.getSagaId(), savedOrder.getOrderId());

		// 7. Outbox 이벤트
		try {
			outboxPort.createAndSave(
				"ORDER",
				savedOrder.getOrderId(),
				OutboxEventType.ORDER_CREATED,
				objectMapper.writeValueAsString(
					toOrderCreatedPayload(savedOrder, orderItems)
				)
			);
			log.info("[Saga-{}] ORDER_CREATED 이벤트 발행 완료", context.getSagaId());
		} catch (Exception e) {
			log.error("[Saga-{}] ORDER_CREATED Outbox 실패", context.getSagaId(), e);
			throw new IllegalStateException("ORDER_CREATED Outbox 실패", e);
		}

		log.info("[Saga-{}] CreateOrderStep 완료: orderId={}", context.getSagaId(), savedOrder.getOrderId());
	}

	private Map<String, Object> toOrderCreatedPayload(
		Order order,
		List<OrderItem> items
	) {
		Map<String, Object> payload = new HashMap<>();

		payload.put("orderId", order.getOrderId());
		payload.put("userId", order.getUserId());
		payload.put("totalAmount", order.getTotalAmount());
		payload.put("pointUsed", order.getPointUsed());
		payload.put("finalAmount", order.getFinalAmount());
		payload.put("status", order.getStatus().name());
		payload.put("orderedAt", order.getOrderedAt());

		payload.put("items", items.stream()
			.map(item -> {
				Map<String, Object> itemMap = new HashMap<>();
				itemMap.put("timeDealStockId", item.getTimeDealStockId());
				itemMap.put("quantity", item.getQuantity());
				itemMap.put("unitPrice", item.getUnitPrice());
				itemMap.put("discountPrice", item.getDiscountPrice()); // null 허용
				return itemMap;
			})
			.toList()
		);
		return payload;
	}

}
