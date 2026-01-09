package com.rushcrew.order_service.application.saga.dto;

import com.rushcrew.common.global.error.ErrorCode;

import lombok.*;

@Getter
public class SagaStepResult {
	private final boolean success;
	private final String errorMessage;
	private final ErrorCode errorCode;

	private SagaStepResult(boolean success, String errorMessage, ErrorCode errorCode) {
		this.success = success;
		this.errorMessage = errorMessage;
		this.errorCode = errorCode;
	}

	public static SagaStepResult success() {
		return new SagaStepResult(true, null, null);
	}

	public static SagaStepResult failure(String errorMessage) {
		return new SagaStepResult(false, errorMessage, null);
	}

	public static SagaStepResult failure(ErrorCode errorCode) {
		return new SagaStepResult(false, errorCode.getMessage(), errorCode);
	}

	public boolean isFailure() {
		return !success;
	}
}
