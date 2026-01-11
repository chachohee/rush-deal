package com.rushcrew.order_service.infrastructure.adapter.out.messaging;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.order_service.application.port.out.StockEventPort;
import com.rushcrew.order_service.domain.model.order.OrderReservation;
import com.rushcrew.order_service.infrastructure.messaging.event.OutboxEventType;
import com.rushcrew.order_service.infrastructure.persistence.outbox.entity.OutboxEventEntity;
import com.rushcrew.order_service.infrastructure.persistence.outbox.repository.OutboxEventJpaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventPublisher implements StockEventPort {

	private final OutboxEventJpaRepository outboxRepository;
	private final ObjectMapper objectMapper;

	@Override
	public void publishStockReservationCancelledBatch(UUID orderId, UUID sagaId, Map<UUID, Long> stockReservations, String reason) {
		try {
			log.info("재고 예약 취소 배치 이벤트 발행: orderId={}, itemCount={}",
				orderId, stockReservations.size());

			// 배치 이벤트 구조
			Map<String, Object> event = new HashMap<>();
			event.put("orderId", orderId.toString());
			event.put("sagaId", sagaId.toString());
			event.put("reason", reason);

			// 재고 목록 (stockId와 quantity 포함)
			List<Map<String, Object>> items = stockReservations.entrySet().stream()
				.map(entry -> {
					Map<String, Object> item = new HashMap<>();
					item.put("stockId", entry.getKey().toString());
					item.put("quantity", entry.getValue());
					return item;
				})
				.collect(Collectors.toList());

			event.put("items", items);

			String payload = objectMapper.writeValueAsString(event);

			OutboxEventEntity outbox = OutboxEventEntity.create(
				"ORDER",
				orderId,
				OutboxEventType.STOCK_ROLLBACK_REQUESTED,
				payload
			);

			outboxRepository.save(outbox);

			log.info("재고 예약 취소 배치 이벤트 Outbox 저장 완료: orderId={}, itemCount={}",
				orderId, stockReservations.size());

		} catch (Exception e) {
			log.error("재고 예약 취소 배치 이벤트 발행 실패: orderId={}", orderId, e);
			throw new RuntimeException("재고 예약 취소 배치 이벤트 발행 실패", e);
		}
	}

}
