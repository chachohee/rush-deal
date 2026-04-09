package com.rushcrew.order_service.application.port.out;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.rushcrew.order_service.application.query.dto.OrderDetailDto;
import com.rushcrew.order_service.application.query.dto.OrderListDto;
import com.rushcrew.order_service.application.query.dto.OrderSearchCriteria;

public interface OrderQueryPort {

	Optional<OrderDetailDto> findOrderDetail(UUID orderId);

	Page<OrderListDto> findByCriteria(OrderSearchCriteria criteria, Pageable pageable);
}
