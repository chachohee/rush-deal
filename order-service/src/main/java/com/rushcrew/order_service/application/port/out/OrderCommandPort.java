package com.rushcrew.order_service.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;

import com.rushcrew.order_service.domain.enums.OrderStatus;
import com.rushcrew.order_service.domain.model.order.Order;

public interface OrderCommandPort {

	Long getTotalPurchasedQuantity(Long userId, UUID productId);

	Order save(Order order);

	Optional<Order> findById(UUID uuid);

	List<Order> findTimedOutPendingOrders(OrderStatus orderStatus, Instant timeoutThreshold, PageRequest of);
}
