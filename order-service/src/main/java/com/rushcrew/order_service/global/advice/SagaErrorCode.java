package com.rushcrew.order_service.global.advice;

import org.springframework.http.HttpStatus;

import com.rushcrew.common.global.error.ErrorCode;

public enum SagaErrorCode implements ErrorCode {
	SAGA_NOT_FOUND(HttpStatus.BAD_REQUEST, "SAGA_NOT_FOUND", "해당 SAGA를 찾을 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String name;
	private final String message;

	SagaErrorCode(HttpStatus httpStatus, String name, String message) {
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
