package com.rushcrew.timedeal.application.port.out.event;

import java.time.Instant;

public record StockReservationFailedEvent(
	String sagaId,
	String orderId,
	String stockId, // TODO: 실패한 재고 ID 목록들로 변경
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
