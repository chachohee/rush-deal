package com.rushcrew.order_service.application.query.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.query.dto.OrderDetailDto;
import com.rushcrew.order_service.application.query.dto.OrderListDto;
import com.rushcrew.order_service.application.query.dto.OrderSearchCriteria;
import com.rushcrew.order_service.application.query.usecase.GetOrderDetailUseCase;
import com.rushcrew.order_service.application.query.usecase.GetOrderListUseCase;
import com.rushcrew.order_service.application.query.port.out.OrderQueryPort;
import com.rushcrew.order_service.global.advice.OrderErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderQueryService implements GetOrderDetailUseCase, GetOrderListUseCase {

	private final OrderQueryPort orderQueryPort;

	@Override
	public OrderDetailDto getOrderDetail(UUID orderId, Long userId, String role) {
		OrderDetailDto dto = orderQueryPort.findOrderDetail(orderId)
			.orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
		// 권한 검증: MASTER가 아니고 본인 주문이 아니면 예외
		boolean isAdmin = "MASTER".equals(role);
		if (!isAdmin && !dto.getUserId().equals(userId)) {
			throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
		}
		return dto;
	}

	@Override
	public Page<OrderListDto> getOrderList(OrderSearchCriteria criteria, Pageable pageable) {
		return orderQueryPort.findByCriteria(criteria, pageable);
	}
}
