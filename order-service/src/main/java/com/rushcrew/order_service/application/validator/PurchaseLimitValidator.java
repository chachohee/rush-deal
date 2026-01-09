package com.rushcrew.order_service.application.validator;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.command.dto.command.CreateOrderCommand;
import com.rushcrew.order_service.application.command.port.out.OrderCommandPort;
import com.rushcrew.order_service.application.port.dto.TimeDealInfo;
import com.rushcrew.order_service.global.advice.OrderErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseLimitValidator {

	private final OrderCommandPort orderCommandPort;

	public void validate(
		Long userId,
		UUID productId,
		List<CreateOrderCommand.OrderItemCommand> items,
		TimeDealInfo timeDeal
	) {
		long requestQuantity = items.stream()
			.mapToLong(CreateOrderCommand.OrderItemCommand::quantity).sum();

		log.debug("[PurchaseLimit] 구매 제한 검증 시작 - userId={}, productId={}, requestQty={}",
			userId, productId, requestQuantity);

		Long totalPurchased = orderCommandPort.getTotalPurchasedQuantity(userId, productId);
		Long limitQuantity = timeDeal.limitQuantity();

		log.debug("[PurchaseLimit] 검증 데이터 - totalPurchased={}, limitQuantity={}",
			totalPurchased, limitQuantity);

		if (limitQuantity != null && (totalPurchased + requestQuantity) > limitQuantity) {
			log.warn("[PurchaseLimit] 구매 제한 초과 감지!");
			log.warn("  - userId: {}", userId);
			log.warn("  - 기존 구매: {}개", totalPurchased);
			log.warn("  - 요청 수량: {}개", requestQuantity);
			log.warn("  - 총 수량: {}개", totalPurchased + requestQuantity);
			log.warn("  - 제한 수량: {}개", limitQuantity);
			throw new BusinessException(OrderErrorCode.PURCHASE_LIMIT_EXCEEDED);
		}

		log.debug("[PurchaseLimit] 구매 제한 검증 통과 - userId={}", userId);
	}
}
