package com.rushcrew.order_service.infrastructure.persistence.saga;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rushcrew.order_service.application.query.dto.OrderSagaResult;
import com.rushcrew.order_service.application.port.out.SagaQueryPort;
import com.rushcrew.order_service.domain.model.saga.SagaInstance;
import com.rushcrew.order_service.infrastructure.persistence.saga.repository.SagaInstanceJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SagaQueryAdapter implements SagaQueryPort {

	private final SagaInstanceJpaRepository repository;

	@Override
	public Optional<OrderSagaResult> findBySagaId(UUID sagaId) {
		return repository.findById(sagaId)
			.map(this::toResult);
	}

	private OrderSagaResult toResult(SagaInstance saga) {
		return OrderSagaResult.builder()
			.sagaId(saga.getSagaId())
			.status(saga.getStatus())
			.orderId(saga.getOrderId())
			.errorMessage(saga.getErrorMessage())
			.createdAt(saga.getCreatedAt())
			.completedAt(saga.getCompletedAt())
			.failedAt(saga.getFailedAt())
			.build();
	}
}
