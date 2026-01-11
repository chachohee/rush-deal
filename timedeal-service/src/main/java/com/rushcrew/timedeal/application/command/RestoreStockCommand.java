package com.rushcrew.timedeal.application.command;

import com.rushcrew.timedeal.domain.vo.OrderId;
import com.rushcrew.timedeal.domain.vo.Quantity;
import java.util.UUID;

public record RestoreStockCommand(
	UUID sagaId,
    UUID stockId,
    Quantity quantity,
    OrderId orderId,
    String reason
) {

}
