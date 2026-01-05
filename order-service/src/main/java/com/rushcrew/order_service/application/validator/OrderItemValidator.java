package com.rushcrew.order_service.application.validator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.command.dto.command.CreateOrderCommand;
import com.rushcrew.order_service.global.error.OrderErrorCode;

@Component
public class OrderItemValidator {
	public void validate(List<CreateOrderCommand.OrderItemCommand> items) {
		// 중복 상품 검증
		Set<UUID> timeDealStockIds = new HashSet<>();
		for (CreateOrderCommand.OrderItemCommand item : items) {
			if (!timeDealStockIds.add(item.timeDealStockId())) {
				throw new BusinessException(OrderErrorCode.DUPLICATE_ORDER_ITEM);
			}
			if (item.quantity() <= 0) {
				throw new IllegalArgumentException("수량은 1개 이상이어야 합니다.");
			}
		}
	}
}
