package com.rushcrew.order_service.infrastructure.messaging.event;

import java.util.List;

public record StockRestoreFailedEvent(
	String sagaId,
	String orderId,
	List<String> stockIds,
	String reason
) {
}
