package com.rushcrew.order_service.application.query.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.rushcrew.order_service.application.query.dto.OrderSagaResult;
import com.rushcrew.order_service.application.port.out.SagaQueryPort;
import com.rushcrew.order_service.application.query.usecase.GetOrderSagaUseCase;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderSagaQueryService implements GetOrderSagaUseCase {

	private final SagaQueryPort sagaQueryPort;

	@Override
	public OrderSagaResult getSaga(UUID sagaId) {
		return sagaQueryPort.findBySagaId(sagaId)
			.orElseThrow(() ->
				new IllegalArgumentException("존재하지 않는 Saga 입니다.")
				// new BusinessException(OrderErrorCode.SAGA_NOT_FOUND)
			);
	}
}
