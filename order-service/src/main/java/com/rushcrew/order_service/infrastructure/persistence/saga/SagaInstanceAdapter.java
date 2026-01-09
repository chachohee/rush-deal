package com.rushcrew.order_service.infrastructure.persistence.saga;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.port.out.SagaInstancePort;
import com.rushcrew.order_service.domain.enums.SagaStatus;
import com.rushcrew.order_service.domain.enums.SagaStepName;
import com.rushcrew.order_service.domain.model.saga.SagaInstance;
import com.rushcrew.order_service.global.advice.SagaErrorCode;
import com.rushcrew.order_service.infrastructure.persistence.saga.repository.SagaInstanceJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SagaInstanceAdapter implements SagaInstancePort {

	private final SagaInstanceJpaRepository repository;

	@Override
	public SagaInstance save(SagaInstance sagaInstance) {
		return repository.save(sagaInstance);
	}

	@Override
	public List<SagaInstance> findTimedOutRunningSagas(
		SagaStatus sagaStatus,
		Instant timeoutThreshold,
		SagaStepName sagaStepName
	) {
		return repository.findTimedOutSagas(
			sagaStatus,
			timeoutThreshold,
			sagaStepName.name()
		);
	}

	@Override
	public SagaInstance findBySagaId(String sagaId) {
		return repository.findById(UUID.fromString(sagaId))
			.orElseThrow(() -> new BusinessException(SagaErrorCode.SAGA_NOT_FOUND));
	}
}
