package com.rushcrew.order_service.application.validator;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.command.dto.command.CreateOrderCommand;
import com.rushcrew.order_service.application.command.port.out.OrderCommandPort;
import com.rushcrew.order_service.application.port.dto.TimeDealInfo;
import com.rushcrew.order_service.global.error.OrderErrorCode;

import lombok.RequiredArgsConstructor;

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
		// 요청 수량 합계
		long requestQuantity = items.stream()
			.mapToLong(CreateOrderCommand.OrderItemCommand::quantity).sum();
		// 기존 구매 수량
		Long totalPurchased = orderCommandPort.getTotalPurchasedQuantity(userId, productId);
		// 제한 수량 확인
		Long limitQuantity = timeDeal.limitQuantity();

		if (limitQuantity != null && (totalPurchased + requestQuantity) > limitQuantity) {
			throw new BusinessException(OrderErrorCode.PURCHASE_LIMIT_EXCEEDED);
		}
	}
}
