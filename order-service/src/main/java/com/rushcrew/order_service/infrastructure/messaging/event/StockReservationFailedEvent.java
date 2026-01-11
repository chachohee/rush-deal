package com.rushcrew.order_service.infrastructure.messaging.event;

import java.time.Instant;

public record StockReservationFailedEvent(
	String sagaId,
	String orderId,
	String stockId,
	String reason,
	Instant occurredAt
) {

	public static StockReservationFailedEvent of(
		String sagaId,
		String orderId,
		String stockId,
		String reason
	) {
		return new StockReservationFailedEvent(
			sagaId,
			orderId,
			stockId,
			reason,
			Instant.now()
		);
	}
}
