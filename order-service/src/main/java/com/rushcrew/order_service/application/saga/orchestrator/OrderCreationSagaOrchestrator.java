package com.rushcrew.order_service.application.saga.orchestrator;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.command.dto.command.CreateOrderCommand;
import com.rushcrew.order_service.application.port.out.SagaInstancePort;
import com.rushcrew.order_service.application.saga.dto.OrderCreationSagaData;
import com.rushcrew.order_service.application.saga.dto.SagaContext;
import com.rushcrew.order_service.application.saga.step.RequestStockReservationStep;
import com.rushcrew.order_service.application.saga.step.UsePointStep;
import com.rushcrew.order_service.application.saga.step.ValidateStockStep;
import com.rushcrew.order_service.domain.enums.SagaStatus;
import com.rushcrew.order_service.domain.enums.SagaStepName;
import com.rushcrew.order_service.domain.model.saga.SagaInstance;
import com.rushcrew.order_service.application.saga.dto.SagaStepResult;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreationSagaOrchestrator {

	private final ValidateStockStep validateStockStep;
	private final UsePointStep usePointStep;
	private final RequestStockReservationStep requestStockReservationStep;
	private final SagaInstancePort sagaInstancePort;

	@Transactional
	public UUID execute(CreateOrderCommand command) {
		// 1. Saga 생성
		SagaInstance saga = SagaInstance.create(SagaStepName.CREATE_ORDER.name(), command.userId());
		sagaInstancePort.save(saga);

		// 2. Context / Data 구성
		SagaContext context = SagaContext.builder()
			.sagaId(saga.getSagaId())
			.userId(command.userId())
			.build();

		// 3. 주문 ID 미리 생성
		UUID preGeneratedOrderId = UUID.randomUUID();

		OrderCreationSagaData data = OrderCreationSagaData.builder()
			.command(command)
			.orderId(preGeneratedOrderId)
			.queueToken(command.queueToken())
			.build();

		log.info("[Saga-{}] Saga 시작 (userId={}, orderId={})",
			saga.getSagaId(), command.userId(), preGeneratedOrderId);

		try {
			// Step 1: ValidateStock
			SagaStepResult validateResult = validateStockStep.execute(context, data);
			handleStepResult(saga, validateResult);
			saga.addStep(SagaStepName.VALIDATE_STOCK, SagaStatus.COMPLETED);

			// Step 2: UsePoint
			SagaStepResult pointResult = usePointStep.execute(context, data);
			handleStepResult(saga, pointResult);
			saga.addStep(SagaStepName.USE_POINT, SagaStatus.COMPLETED);

			// Step 3: RequestStockReservation
			SagaStepResult stockReservationResult = requestStockReservationStep.execute(context, data);
			handleStepResult(saga, stockReservationResult);
			saga.addStep(SagaStepName.REQUEST_STOCK_RESERVATION, SagaStatus.WAITING);

			// SagaData 저장
			saga.saveData(data);
			sagaInstancePort.save(saga);

			log.info("[Saga-{}] Saga 초기화 완료 (비동기 대기)", saga.getSagaId());
			return saga.getSagaId();

		} catch (BusinessException e) {
			log.error("[Saga-{}] Saga 실패 (BusinessException): ErrorCode={}, Message={}",
				saga.getSagaId(),
				e.getErrorCode() != null ? e.getErrorCode().getName() : "UNKNOWN",
				e.getErrorCode() != null ? e.getErrorCode().getMessage() : "No message");
			compensateCompletedSteps(saga, context, data);
			throw e;

		} catch (Exception e) {
			log.error("[Saga-{}] Saga 실패 (예상치 못한 오류): {}",
				saga.getSagaId(), e.getMessage(), e);
			compensateCompletedSteps(saga, context, data);
			throw e;
		}
	}

	/**
	 * Step 실행 결과 처리
	 */
	private void handleStepResult(SagaInstance saga, SagaStepResult result) {
		if (result.isFailure()) {
			saga.fail(result.getErrorMessage());
			sagaInstancePort.save(saga);

			if (result.getErrorCode() != null) {
				throw new BusinessException(result.getErrorCode());
			} else {
				throw new RuntimeException(result.getErrorMessage());
			}
		}
	}

	/**
	 * 완료된 Step들에 대한 보상 트랜잭션 실행
	 */
	private void compensateCompletedSteps(SagaInstance saga, SagaContext context, OrderCreationSagaData data) {
		log.info("[Saga-{}] 보상 트랜잭션 시작", saga.getSagaId());

		// USE_POINT가 완료되었다면 보상
		if (saga.hasCompletedStep(SagaStepName.USE_POINT)) {
			try {
				usePointStep.compensate(context, data);
				saga.addStep(SagaStepName.USE_POINT_COMPENSATE, SagaStatus.COMPLETED);
			} catch (Exception compensateError) {
				saga.addStep(SagaStepName.USE_POINT_COMPENSATE, SagaStatus.FAILED);
				log.error("[Saga-{}] 보상 실패: {}",
					saga.getSagaId(), compensateError.getMessage(), compensateError);
			}
		}

		log.info("[Saga-{}] 보상 트랜잭션 완료", saga.getSagaId());
	}
}
