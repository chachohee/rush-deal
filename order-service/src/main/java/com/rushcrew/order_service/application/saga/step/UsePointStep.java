package com.rushcrew.order_service.application.saga.step;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rushcrew.order_service.application.port.out.PointPort;
import com.rushcrew.order_service.application.saga.dto.OrderCreationSagaData;
import com.rushcrew.order_service.application.saga.dto.SagaContext;
import com.rushcrew.order_service.application.saga.dto.SagaStepResult;
import com.rushcrew.order_service.domain.enums.SagaStepName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class UsePointStep {

	private final PointPort pointPort;

	public SagaStepResult execute(SagaContext context, OrderCreationSagaData data) {
		log.info("[Saga-{}] Step 2: {} 시작", context.getSagaId(), SagaStepName.USE_POINT.getDescription());
		try {
			Long pointUsed = data.getCommand().pointUsed();

			// 포인트 사용이 없으면 스킵
			if (pointUsed == null || pointUsed <= 0) {
				log.info("[Saga-{}] Step 2: 포인트 사용 없음, 스킵", context.getSagaId());
				return SagaStepResult.success();
			}

			Long userId = context.getUserId();
			UUID sagaId = context.getSagaId();
			UUID orderId = data.getOrderId();

			log.info("[Saga-{}] Step 2: 포인트 차감 요청 (userId={}, pointUsed={}, orderId={})",
				sagaId, userId, pointUsed, orderId);
			pointPort.usePoint(userId, orderId.toString(), pointUsed, sagaId);

			log.info("[Saga-{}] Step 2: {} 완료 (orderId={}, pointUsed={})",
				sagaId, SagaStepName.USE_POINT.getDescription(), orderId, pointUsed);
			return SagaStepResult.success();

		} catch (IllegalArgumentException e) {
			log.warn("[Saga-{}] Step 2: {} 실패 - {}",
				context.getSagaId(), SagaStepName.USE_POINT.getDescription(), e.getMessage());
			return SagaStepResult.failure(e.getMessage());

		} catch (Exception e) {
			log.error("[Saga-{}] Step 2: {} 실패 - {}",
				context.getSagaId(), SagaStepName.USE_POINT.getDescription(), e.getMessage(), e);
			return SagaStepResult.failure("포인트 서비스 호출 실패: " + e.getMessage());
		}
	}

	public void compensate(SagaContext context, OrderCreationSagaData data) {
		try {
			Long pointUsed = data.getCommand().pointUsed();

			if (pointUsed == null || pointUsed == 0) {
				log.info("[Saga-{}] 보상: 포인트 사용 없음, 스킵", context.getSagaId());
				return;
			}

			Long userId = context.getUserId();
			UUID sagaId = context.getSagaId();
			UUID orderId = data.getOrderId();

			log.info("[Saga-{}] 보상: {} 시작 (orderId={}, pointUsed={})",
				sagaId, SagaStepName.USE_POINT_COMPENSATE.getDescription(), orderId, pointUsed);

			pointPort.cancelPointUse(userId, orderId.toString(), sagaId);

			log.info("[Saga-{}] 보상: {} 완료",
				sagaId, SagaStepName.USE_POINT_COMPENSATE.getDescription());

		} catch (Exception e) {
			log.error("[Saga-{}] 보상: {} 실패 - {}",
				context.getSagaId(), SagaStepName.USE_POINT_COMPENSATE.getDescription(), e.getMessage(), e);
			throw new RuntimeException("포인트 보상 실패", e);
		}
	}
}
