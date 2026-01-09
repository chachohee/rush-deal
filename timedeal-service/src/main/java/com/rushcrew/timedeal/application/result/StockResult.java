package com.rushcrew.timedeal.application.result;

import com.rushcrew.timedeal.domain.vo.TimeDealProductStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record StockResult(
    UUID stockId,
    UUID productId,
	UUID optionId,
    Long sellerId,
    Long availableStock,
    Long reservedStock,
    Long soldStock,
    TimeDealProductStatus status,
    LocalDateTime updatedAt
) {

}
