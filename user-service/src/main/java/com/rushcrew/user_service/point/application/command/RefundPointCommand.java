package com.rushcrew.user_service.point.application.command;

public record RefundPointCommand(
	Long userId,
	String orderId,
	String sagaId
) {
}
