package com.rushcrew.order_service.infrastructure.persistence.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.rushcrew.order_service.application.port.out.OrderCommandPort;
import com.rushcrew.order_service.domain.enums.OrderStatus;
import com.rushcrew.order_service.domain.model.order.Order;
import com.rushcrew.order_service.infrastructure.persistence.order.OrderJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderCommandAdapter implements OrderCommandPort {
	private final OrderJpaRepository orderJpaRepository;

	@Override
	public Long getTotalPurchasedQuantity(Long userId, UUID productId) {
		return orderJpaRepository.getTotalPurchasedQuantity(userId, productId);
	}

	@Override
	public Order save(Order order) {
		return orderJpaRepository.save(order);
	}

	@Override
	public Optional<Order> findById(UUID orderId) {
		return orderJpaRepository.findById(orderId);
	}

	@Override
	public List<Order> findTimedOutPendingOrders(OrderStatus orderStatus, Instant timeoutThreshold, PageRequest of) {
		return orderJpaRepository.findByStatusAndOrderedAtBefore(orderStatus, timeoutThreshold, of);
	}
}
