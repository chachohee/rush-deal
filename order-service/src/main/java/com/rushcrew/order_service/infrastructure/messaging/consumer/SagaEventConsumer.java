package com.rushcrew.order_service.infrastructure.messaging.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.order_service.application.saga.handler.OrderSagaEventHandler;
import com.rushcrew.order_service.infrastructure.messaging.event.StockReservationFailedEvent;
import com.rushcrew.order_service.infrastructure.messaging.event.StockReservedEvent;
import com.rushcrew.order_service.infrastructure.messaging.event.StockRestoreFailedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaEventConsumer {

	private final OrderSagaEventHandler handler;
	private final ObjectMapper objectMapper;

	@KafkaListener(topics = "stock.reserved", groupId = "order-saga")
	public void onStockReserved(String message) {
		try {
			StockReservedEvent event =
				objectMapper.readValue(message, StockReservedEvent.class);
			log.info("[Saga-{}] stock.reserved 수신", event.sagaId());
			handler.handleStockReserved(event);
		} catch (Exception e) {
			log.error("stock.reserved 처리 실패: {}", message, e);
		}
	}

	@KafkaListener(topics = "stock.reservation.failed", groupId = "order-saga")
	public void onStockReservationFailed(String message) {
		try {
			StockReservationFailedEvent event =
				objectMapper.readValue(message, StockReservationFailedEvent.class);
			log.warn("[Saga-{}] stock.reservation.failed 수신", event.sagaId());
			handler.handleStockReservationFailed(event);
		} catch (Exception e) {
			log.error("stock.reservation.failed 처리 실패: {}", message, e);
		}
	}

	@KafkaListener(topics = "stock.restore.failed", groupId = "order-saga")
	public void onStockRestoreFailed(String message) {
		try {
			StockRestoreFailedEvent event =
				objectMapper.readValue(message, StockRestoreFailedEvent.class);
			log.error("[Saga-{}] stock.restore.failed 수신 - orderId={}, reason={}",
				event.sagaId(), event.orderId(), event.reason());
			handler.handleStockRestoreFailed(event);
		} catch (Exception e) {
			log.error("stock.restore.failed 처리 실패: {}", message, e);
		}
	}
}
