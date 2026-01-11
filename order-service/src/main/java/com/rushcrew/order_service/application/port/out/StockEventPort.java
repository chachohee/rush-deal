package com.rushcrew.order_service.application.port.out;

import java.util.Map;
import java.util.UUID;

public interface StockEventPort {

	void publishStockReservationCancelledBatch(UUID orderId, UUID sagaId, Map<UUID, Long> stockReservations, String reason);

}
