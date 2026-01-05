package com.rushcrew.order_service.application.port.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;

@Builder
public record TimeDealInfo(
	UUID timeDealId,
	String title,
	BigDecimal originalPrice,
	BigDecimal discountPrice,
	Integer discountRate,
	Long limitQuantity,
	TimeDealStatus status,
	boolean isActive
) {}
