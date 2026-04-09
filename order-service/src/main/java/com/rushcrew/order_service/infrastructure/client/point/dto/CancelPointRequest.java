package com.rushcrew.order_service.infrastructure.client.point.dto;

import lombok.Builder;

@Builder
public record CancelPointRequest(
	Long userId,
	String orderId,
	String sagaId
) {}
