package com.rushcrew.timedeal.presentation.dto.response;

import com.rushcrew.timedeal.application.result.StockResult;
import com.rushcrew.timedeal.domain.vo.TimeDealProductStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record StockResponse(
    UUID stockId,
    UUID productId,
	UUID optionId,
    Long availableStock,
    Long reservedStock,
    Long soldStock,
    TimeDealProductStatus status,
    LocalDateTime updatedAt
) {

    public static StockResponse from(StockResult result) {
        return new StockResponse(
            result.stockId(),
            result.productId(),
			result.optionId(),
            result.availableStock(),
            result.reservedStock(),
            result.soldStock(),
            result.status(),
            result.updatedAt()
        );
    }
}
