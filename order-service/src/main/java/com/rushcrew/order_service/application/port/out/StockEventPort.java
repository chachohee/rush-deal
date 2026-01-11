package com.rushcrew.order_service.application.port.out;

import java.util.Map;
import java.util.UUID;

public interface StockEventPort {
	/** 재고 예약 취소 이벤트 발행 - 재고 복구 */
	void publishStockReservationCancelledBatch(UUID orderId, UUID sagaId, Map<UUID, Long> stockReservations, String reason);
}
