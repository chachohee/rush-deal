package com.rushcrew.order_service.domain.enums;

import lombok.Getter;

@Getter
public enum SagaStepName {

	CREATE_ORDER("주문 생성"),
	VALIDATE_STOCK("재고 검증"),
	USE_POINT("포인트 사용"),
	REQUEST_STOCK_RESERVATION("재고 예약 요청"),
	USE_POINT_COMPENSATE("포인트 사용 보상"),
	REQUEST_STOCK_RESERVATION_COMPENSATE("재고 예약 요청 보상");

	private final String description;

	SagaStepName(String description) {
		this.description = description;
	}

}
