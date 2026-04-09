package com.rushcrew.order_service.infrastructure.adapter.cache;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.rushcrew.order_service.application.command.port.out.OrderCachePort;
import com.rushcrew.order_service.application.query.dto.OrderDetailDto;

@Component
@Profile("test")
public class NoOpOrderCacheAdapter implements OrderCachePort {

	@Override
	public Optional<OrderDetailDto> getFromCache(UUID orderId) {
		return Optional.empty();
	}

	@Override
	public void updateOrderCache(UUID orderId, OrderDetailDto orderDetailDto) {
		// do nothing
	}

	@Override
	public boolean existsInCache(UUID orderId) {
		return false;
	}

	@Override
	public void evictOrderCache(UUID orderId) {
		// do nothing
	}
}
