package com.rushcrew.timedeal.application.port.out.event;

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
