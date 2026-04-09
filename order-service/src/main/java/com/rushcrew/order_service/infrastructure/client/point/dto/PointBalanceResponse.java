package com.rushcrew.order_service.infrastructure.client.point.dto;

import lombok.Builder;

@Builder
public record PointBalanceResponse(
	Long userId,
	Long balance
) {}
