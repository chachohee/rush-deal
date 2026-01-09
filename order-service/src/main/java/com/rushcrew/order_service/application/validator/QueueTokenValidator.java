package com.rushcrew.order_service.application.validator;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.port.out.QueuePort;
import com.rushcrew.order_service.global.advice.OrderErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class QueueTokenValidator {
	private final QueuePort queuePort;

	public void validate(UUID productId, Long userId, String queueToken, String role) {
		if (!queuePort.validateToken(productId, userId, queueToken, role)) {
			throw new BusinessException(OrderErrorCode.INVALID_QUEUE_TOKEN);
		}
	}
}
