package com.rushcrew.order_service.application.query.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.command.port.out.OrderCachePort;
import com.rushcrew.order_service.application.query.dto.OrderDetailDto;
import com.rushcrew.order_service.application.query.dto.OrderListDto;
import com.rushcrew.order_service.application.query.dto.OrderSearchCriteria;
import com.rushcrew.order_service.application.query.usecase.GetOrderDetailUseCase;
import com.rushcrew.order_service.application.query.usecase.GetOrderListUseCase;
import com.rushcrew.order_service.application.query.port.out.OrderQueryPort;
import com.rushcrew.order_service.global.advice.OrderErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderQueryService implements GetOrderDetailUseCase, GetOrderListUseCase {

	private final OrderQueryPort orderQueryPort;
	private final OrderCachePort orderCachePort;

	/**
	 * 주문 상세 조회 (L1 Caffeine → L2 Redis → DB 순서)
	 *
	 * 캐시 미스 시 DB에서 조회 후 캐시에 자동 적재
	 */
	@Override
	public OrderDetailDto getOrderDetail(UUID orderId, Long userId, String role) {
		OrderDetailDto dto = orderCachePort.getFromCache(orderId)
			.orElseGet(() -> {
				log.debug("[Cache] 캐시 미스, DB 조회: orderId={}", orderId);
				OrderDetailDto fetched = orderQueryPort.findOrderDetail(orderId)
					.orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
				orderCachePort.updateOrderCache(orderId, fetched);
				return fetched;
			});

		if (!"MASTER".equals(role) && !dto.getUserId().equals(userId)) {
			throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
		}
		return dto;
	}

	@Override
	public Page<OrderListDto> getOrderList(OrderSearchCriteria criteria, Pageable pageable) {
		return orderQueryPort.findByCriteria(criteria, pageable);
	}
}
