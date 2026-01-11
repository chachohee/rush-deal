package com.rushcrew.timedeal.application.port.out.event;

public record StockRestoreFailedEvent(
	String sagaId,
	String orderId,
	String stockId,  // TODO: 실패한 재고 ID 목록들로 변경
	String reason
) {
	public static StockRestoreFailedEvent of(String sagaId, String orderId, String stockId, String reason) {
		return new StockRestoreFailedEvent(sagaId, orderId, stockId, reason);
	}
}
