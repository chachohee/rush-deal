package com.rushcrew.order_service.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.rushcrew.order_service.application.query.dto.OrderSagaResult;

public interface SagaQueryPort {
	Optional<OrderSagaResult> findBySagaId(UUID sagaId);
}
