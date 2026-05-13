package com.rushcrew.order_service.application.query.usecase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rushcrew.order_service.application.query.dto.SellerOrderSummary;
import com.rushcrew.order_service.infrastructure.persistence.order.OrderJpaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetSellerOrderSummaryUseCase {

	private final OrderJpaRepository orderJpaRepository;

	@Transactional(readOnly = true)
	public SellerOrderSummary getSummary(Long sellerId) {
		Instant since = Instant.now().minus(7, ChronoUnit.DAYS);

		long totalOrders = nullSafeLong(orderJpaRepository.countSellerPaidOrders(sellerId));
		BigDecimal totalRevenue = nullSafe(orderJpaRepository.sumSellerRevenue(sellerId));
		long ordersLast7 = nullSafeLong(orderJpaRepository.countSellerOrdersSince(sellerId, since));
		BigDecimal revenueLast7 = nullSafe(orderJpaRepository.sumSellerRevenueSince(sellerId, since));

		return new SellerOrderSummary(totalOrders, totalRevenue, ordersLast7, revenueLast7);
	}

	private BigDecimal nullSafe(BigDecimal value) {
		return value != null ? value : BigDecimal.ZERO;
	}

	private long nullSafeLong(Long value) {
		return value != null ? value : 0L;
	}
}
