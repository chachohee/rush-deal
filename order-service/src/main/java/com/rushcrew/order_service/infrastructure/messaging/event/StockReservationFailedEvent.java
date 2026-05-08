package com.rushcrew.order_service.infrastructure.messaging.event;

import java.time.Instant;
import java.util.List;

public record StockReservationFailedEvent(
	String sagaId,
	String orderId,
	List<String> stockIds,
	String reason,
	Instant occurredAt
) {

	public static StockReservationFailedEvent of(
		String sagaId,
		String orderId,
		List<String> stockIds,
		String reason
	) {
		return new StockReservationFailedEvent(
			sagaId,
			orderId,
			stockIds,
			reason,
			Instant.now()
		);
	}
}
