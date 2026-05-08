package com.rushcrew.timedeal.application.port.out.event;

import java.util.List;

public record StockRestoreFailedEvent(
	String sagaId,
	String orderId,
	List<String> stockIds,
	String reason
) {
	public static StockRestoreFailedEvent of(String sagaId, String orderId, List<String> stockIds, String reason) {
		return new StockRestoreFailedEvent(sagaId, orderId, stockIds, reason);
	}
}
