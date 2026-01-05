package com.rushcrew.order_service.infrastructure.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StockReservedEvent(
	String sagaId,
	String timeDealId,
	List<ReservedStockItem> reservedItems,
	Instant occurredAt
) {
	public record ReservedStockItem(
		String timeDealStockId,
		String productId,
		String optionId,
		Long quantity,
		BigDecimal discountedPrice
	) {}
}
