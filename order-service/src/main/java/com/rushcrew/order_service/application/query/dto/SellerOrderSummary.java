package com.rushcrew.order_service.application.query.dto;

import java.math.BigDecimal;

public record SellerOrderSummary(
	long totalOrders,
	BigDecimal totalRevenue,
	long ordersLast7Days,
	BigDecimal revenueLast7Days
) {}
