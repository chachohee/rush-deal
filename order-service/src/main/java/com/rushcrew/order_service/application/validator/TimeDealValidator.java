package com.rushcrew.order_service.application.validator;

import org.springframework.stereotype.Component;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.port.dto.TimeDealInfo;
import com.rushcrew.order_service.application.port.dto.TimeDealStatus;
import com.rushcrew.order_service.global.advice.OrderErrorCode;

@Component
public class TimeDealValidator {
	public void validate(TimeDealInfo timeDeal) {
		if (timeDeal.status() != TimeDealStatus.IN_PROGRESS) {
			throw new BusinessException(OrderErrorCode.INVALID_TIME_DEAL);
		}
	}
}
