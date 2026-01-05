package com.rushcrew.order_service.global.error;

import org.springframework.http.HttpStatus;

import com.rushcrew.common.global.error.ErrorCode;

public enum OrderErrorCode implements ErrorCode {

	INVALID_QUEUE_TOKEN(HttpStatus.BAD_REQUEST, "INVALID_QUEUE_TOKEN", "대기열 토큰이 유효하지 않습니다."),
	INVALID_TIME_DEAL(HttpStatus.BAD_REQUEST, "INVALID_TIME_DEAL", "진행중인 타임딜이 아닙니다."),
	INVALID_PRODUCT(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT", "존재하지 않거나 판매 중단된 상품입니다."),
	DUPLICATE_ORDER_ITEM(HttpStatus.BAD_REQUEST, "DUPLICATE_ORDER_ITEM", "이미 장바구니에 담긴 상품입니다."),
	PURCHASE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "PURCHASE_LIMIT_EXCEEDED", "1인당 최대 구매 가능 수량을 초과했습니다."),
	STOCK_DEPLETED(HttpStatus.CONFLICT, "STOCK_DEPLETED", "재고가 부족합니다."),
	NOT_ENOUGH_POINTS(HttpStatus.BAD_REQUEST, "NOT_ENOUGH_POINTS", "포인트 잔액이 부족합니다."),
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
	ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ORDER_ACCESS_DENIED", "본인의 주문만 접근할 수 있습니다."),
	ORDER_CANNOT_PAY(HttpStatus.BAD_REQUEST, "ORDER_CANNOT_PAY", "결제 가능한 주문 상태가 아닙니다."),
	ORDER_CANNOT_CONFIRM_PURCHASE(HttpStatus.BAD_REQUEST, "ORDER_CANNOT_CONFIRM_PURCHASE", "구매확정 가능한 주문 상태가 아닙니다."),
	ORDER_CANNOT_CANCEL(HttpStatus.BAD_REQUEST, "ORDER_CANNOT_CANCEL", "취소 가능한 주문 상태가 아닙니다."),
	ORDER_CANNOT_REFUND(HttpStatus.BAD_REQUEST, "ORDER_CANNOT_REFUND", "환불 가능한 주문 상태가 아닙니다."),
	PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT_FAILED", "결제에 실패했습니다."),
	INVALID_ORDER_STATE(HttpStatus.BAD_REQUEST, "INVALID_ORDER_STATE", "결제 가능한 주문 상태가 아닙니다."),
	UNAUTHORIZED(HttpStatus.FORBIDDEN, "UNAUTHORIZED", "본인의 주문만 결제할 수 있습니다."),
	RESERVATION_EXPIRED(HttpStatus.BAD_REQUEST, "RESERVATION_EXPIRED", "재고 예약이 만료되었습니다. 주문을 다시 생성해주세요."),
	SOLD_OUT_PRODUCT(HttpStatus.BAD_REQUEST, "SOLD_OUT_PRODUCT", "품절된 상품입니다."),
	ORDER_CANNOT_UPDATE(HttpStatus.BAD_REQUEST, "ORDER_CANNOT_UPDATE", "수정 가능한 주문 상태가 아닙니다."),
	ORDER_CANNOT_UPDATE_POINT(HttpStatus.BAD_REQUEST, "ORDER_CANNOT_UPDATE_POINT", "포인트 사용량은 결제 전(PENDING 상태)에서만 수정할 수 있습니다."),
	ORDER_UPDATE_TIME_EXPIRED(HttpStatus.BAD_REQUEST, "ORDER_UPDATE_TIME_EXPIRED", "주문 수정 가능 시간이 지났습니다."),
	ORDER_UPDATE_NO_CHANGES(HttpStatus.BAD_REQUEST, "ORDER_UPDATE_NO_CHANGES", "수정할 항목이 없습니다."),
	ORDER_CANNOT_UPDATE_POINT_AFTER_CREATION(HttpStatus.BAD_REQUEST, "ORDER_CANNOT_UPDATE_POINT_AFTER_CREATION", "주문 생성 후 포인트 사용량은 수정할 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String name;
	private final String message;

	OrderErrorCode(HttpStatus httpStatus, String name, String message) {
		this.httpStatus = httpStatus;
		this.name = name;
		this.message = message;
	}

	@Override
	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getMessage() {
		return message;
	}
}
