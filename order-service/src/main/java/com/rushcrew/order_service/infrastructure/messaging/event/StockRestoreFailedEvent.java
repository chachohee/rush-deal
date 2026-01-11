package com.rushcrew.order_service.infrastructure.messaging.event;

public record StockRestoreFailedEvent(
	String sagaId,
	String orderId,
	String stockId,
	String reason
) {
}
