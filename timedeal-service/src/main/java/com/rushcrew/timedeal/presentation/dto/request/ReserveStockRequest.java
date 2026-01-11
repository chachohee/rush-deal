package com.rushcrew.timedeal.presentation.dto.request;

import com.rushcrew.timedeal.application.command.ReserveStockCommand;
import com.rushcrew.timedeal.domain.vo.OrderId;
import com.rushcrew.timedeal.domain.vo.Quantity;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReserveStockRequest(
    @NotNull
    UUID orderId,

	@NotNull
	UUID sagaId,

    @NotNull
    UUID timeDealStockId,

    @NotNull
    Long quantity,

    @NotNull
    Long userId
) {

    public ReserveStockCommand toCommand() {
        return new ReserveStockCommand(
            OrderId.of(this.orderId),
			this.sagaId,
            this.timeDealStockId,
            Quantity.positive(this.quantity),
            this.userId
        );
    }
}
