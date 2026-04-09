package com.rushcrew.order_service.infrastructure.client.point.dto;

import lombok.Builder;

@Builder
public record UsePointRequest(
	Long userId,
	String orderId,
	Long amount,
	String sagaId
) {}
