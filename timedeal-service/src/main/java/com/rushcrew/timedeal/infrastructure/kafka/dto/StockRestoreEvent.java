package com.rushcrew.timedeal.infrastructure.kafka.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record StockRestoreEvent(
	@NotNull UUID orderId,
	@NotNull UUID sagaId,
	@NotBlank String reason,
	@NotNull List<StockRestoreItem> items
) {
	public record StockRestoreItem(
		@NotNull UUID stockId,
		@NotNull Long quantity
	) {}
}
