package com.rushcrew.order_service.application.command.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.command.dto.command.ConfirmPurchaseCommand;
import com.rushcrew.order_service.application.command.dto.result.ConfirmPurchaseResult;
import com.rushcrew.order_service.application.port.out.OrderCommandPort;
import com.rushcrew.order_service.application.command.usecase.ConfirmPurchaseUseCase;
import com.rushcrew.order_service.application.port.out.OutboxPort;
import com.rushcrew.order_service.application.port.out.PointEventPort;
import com.rushcrew.order_service.domain.model.order.Order;
import com.rushcrew.order_service.global.advice.OrderErrorCode;
import com.rushcrew.order_service.infrastructure.messaging.event.OutboxEventType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmPurchaseService implements ConfirmPurchaseUseCase {

	private final OrderCommandPort orderCommandPort;
	private final PointEventPort pointEventPort;
	private final OutboxPort outboxPort;
	private final ObjectMapper objectMapper;

	@Override
	@Transactional
	public ConfirmPurchaseResult confirmPurchase(ConfirmPurchaseCommand command) {
		log.info("구매확정 처리 시작: orderId={}, userId={}", command.orderId(), command.userId());

		Order order = orderCommandPort.findById(command.orderId())
			.orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

		if (!order.isOwnedBy(command.userId())) {
			throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
		}

		// 구매확정 가능 상태 검증
		if (!order.canConfirmPurchase()) {
			throw new BusinessException(OrderErrorCode.ORDER_CANNOT_CONFIRM_PURCHASE);
		}

		// 주문 상태 변경 PAID -> PURCHASE_CONFIRMED
		order.confirmPurchase();
		Order savedOrder = orderCommandPort.save(order);	// DB 저장

		log.info("구매확정 완료: orderId={}", savedOrder.getOrderId());

		// 포인트 적립 요청 이벤트 발행 (kafka 비동기 통신 - outbox 패턴)
		pointEventPort.publishPointEarnRequested(
			savedOrder.getUserId(),
			savedOrder.getOrderId(),
			savedOrder.getFinalAmount(),
			savedOrder.getSagaId(),
			"구매확정"
		);

		// ORDER_PURCHASE_CONFIRMED 이벤트를 outbox에 저장
		try {
			Map<String, Object> eventPayload = new HashMap<>();
			eventPayload.put("orderId", savedOrder.getOrderId());
			eventPayload.put("userId", savedOrder.getUserId());
			eventPayload.put("status", savedOrder.getStatus().name());
			eventPayload.put("finalAmount", savedOrder.getFinalAmount());
			eventPayload.put("purchaseConfirmedAt", savedOrder.getPurchaseConfirmedAt());

			outboxPort.createAndSave(
				"ORDER",
				savedOrder.getOrderId(),
				OutboxEventType.ORDER_PURCHASE_CONFIRMED,
				objectMapper.writeValueAsString(eventPayload)
			);
		} catch (Exception e) {
			log.error("ORDER_PURCHASE_CONFIRMED 이벤트 저장 실패: orderId={}", savedOrder.getOrderId(), e);
		}

		return ConfirmPurchaseResult.builder()
			.orderId(savedOrder.getOrderId())
			.orderStatus(savedOrder.getStatus().name())
			.purchaseConfirmedAt(savedOrder.getPurchaseConfirmedAt())
			.build();
	}
}
