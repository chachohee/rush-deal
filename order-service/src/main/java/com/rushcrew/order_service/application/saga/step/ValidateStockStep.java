package com.rushcrew.order_service.application.saga.step;

import org.springframework.stereotype.Component;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.command.dto.command.CreateOrderCommand;
import com.rushcrew.order_service.application.port.dto.TimeDealInfo;
import com.rushcrew.order_service.application.port.out.TimeDealStockPort;
import com.rushcrew.order_service.application.saga.dto.OrderCreationSagaData;
import com.rushcrew.order_service.application.saga.dto.SagaContext;
import com.rushcrew.order_service.application.saga.dto.SagaStepResult;
import com.rushcrew.order_service.application.validator.OrderItemValidator;
import com.rushcrew.order_service.application.validator.PurchaseLimitValidator;
import com.rushcrew.order_service.application.validator.QueueTokenValidator;
import com.rushcrew.order_service.application.validator.TimeDealValidator;
import com.rushcrew.order_service.domain.enums.SagaStepName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateStockStep {

	private final TimeDealStockPort timeDealStockPort;
	private final QueueTokenValidator queueTokenValidator;
	private final TimeDealValidator timeDealValidator;
	private final OrderItemValidator orderItemValidator;
	private final PurchaseLimitValidator purchaseLimitValidator;

	public SagaStepResult execute(SagaContext context, OrderCreationSagaData data) {
		log.info("[Saga-{}] Step 1: {} 시작", context.getSagaId(), SagaStepName.VALIDATE_STOCK.getDescription());
		try {
			CreateOrderCommand command = data.getCommand();
			// 1. 큐 토큰 검증
			log.info("[Saga-{}] Step 1-1: 큐 토큰 검증 시작 (userId={}, productId={})",
				context.getSagaId(), command.userId(), command.productId());
			queueTokenValidator.validate(
				command.productId(),
				command.userId(),
				command.queueToken(),
				command.role()
			);
			log.info("[Saga-{}] Step 1-1: 큐 토큰 검증 완료", context.getSagaId());

			// 2. 타임딜 조회 및 검증
			log.info("[Saga-{}] Step 1-2: 타임딜 조회 시작 (timeDealId={})",
				context.getSagaId(), command.timeDealId());
			TimeDealInfo timeDeal = timeDealStockPort.getTimeDeal(command.timeDealId());
			timeDealValidator.validate(timeDeal);
			log.info("[Saga-{}] Step 1-2: 타임딜 검증 완료", context.getSagaId());

			// 3. 주문 아이템 검증
			log.info("[Saga-{}] Step 1-3: 주문 아이템 검증 시작 (itemCount={})",
				context.getSagaId(), command.orderItems().size());
			orderItemValidator.validate(command.orderItems());
			log.info("[Saga-{}] Step 1-3: 주문 아이템 검증 완료", context.getSagaId());

			// 4. 구매 제한 검증
			log.info("[Saga-{}] Step 1-4: 구매 제한 검증 시작 (userId={}, productId={})",
				context.getSagaId(), command.userId(), command.productId());
			purchaseLimitValidator.validate(
				command.userId(),
				command.productId(),
				command.orderItems(),
				timeDeal
			);
			log.info("[Saga-{}] Step 1-4: 구매 제한 검증 완료", context.getSagaId());

			log.info("[Saga-{}] Step 1: {} 완료", context.getSagaId(), SagaStepName.VALIDATE_STOCK.getDescription());
			return SagaStepResult.success();

		} catch (BusinessException e) {
			String errorMsg = e.getMessage() != null ? e.getMessage() : "비즈니스 검증 실패";
			log.error("[Saga-{}] Step 1: {} 실패 - ExceptionType: {}, Message: {}",
				context.getSagaId(), SagaStepName.VALIDATE_STOCK.getDescription(),
				e.getClass().getSimpleName(), errorMsg, e);
			return SagaStepResult.failure(errorMsg);

		} catch (Exception e) {
			String errorMsg = "재고 검증 중 오류 발생: " + e.getClass().getSimpleName();
			log.error("[Saga-{}] Step 1: {} 실패 - {}",
				context.getSagaId(), SagaStepName.VALIDATE_STOCK.getDescription(), errorMsg, e);
			return SagaStepResult.failure(errorMsg);
		}
	}
}
