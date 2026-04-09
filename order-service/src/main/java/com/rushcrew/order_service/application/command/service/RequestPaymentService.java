package com.rushcrew.order_service.application.command.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.command.dto.command.RequestPaymentCommand;
import com.rushcrew.order_service.application.command.dto.result.RequestPaymentResult;
import com.rushcrew.order_service.application.port.out.OrderCommandPort;
import com.rushcrew.order_service.application.command.usecase.RequestPaymentUseCase;
import com.rushcrew.order_service.application.port.out.OutboxPort;
import com.rushcrew.order_service.application.port.out.PaymentPort;
import com.rushcrew.order_service.domain.model.order.Order;
import com.rushcrew.order_service.global.advice.OrderErrorCode;
import com.rushcrew.order_service.infrastructure.messaging.event.OutboxEventType;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestPaymentService implements RequestPaymentUseCase {

	private final OrderCommandPort orderCommandPort;
	private final PaymentPort paymentPort;
	private final OutboxPort outboxPort;
	private final ObjectMapper objectMapper;

	@Override
	@Transactional
	public RequestPaymentResult requestPayment(RequestPaymentCommand command) {
		log.info("결제 요청 처리 시작: orderId={}, userId={}", command.orderId(), command.userId());

		Order order = orderCommandPort.findById(command.orderId())
			.orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

		if (!order.isOwnedBy(command.userId())) {
			throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
		}

		if (!order.canPay()) {
			throw new BusinessException(OrderErrorCode.ORDER_CANNOT_PAY);
		}

		// 결제 서비스에 결제 요청 (feign 동기 통신)
		boolean paymentSuccess = paymentPort.requestPayment(
			order.getOrderId(),
			order.getUserId(),
			order.getFinalAmount()
		);

		if (!paymentSuccess) {
			log.error("결제 실패: orderId={}", order.getOrderId());
			throw new BusinessException(OrderErrorCode.PAYMENT_FAILED);
		}

		// 주문 상태 변경 PENDING -> PAID
		order.completePayment();
		Order savedOrder = orderCommandPort.save(order);

		// ORDER_PAID 이벤트 outbox에 저장
		try {
			Map<String, Object> eventPayload = new HashMap<>();
			eventPayload.put("orderId", savedOrder.getOrderId());
			eventPayload.put("userId", savedOrder.getUserId());
			eventPayload.put("status", savedOrder.getStatus().name());
			eventPayload.put("paymentAmount", savedOrder.getFinalAmount());
			eventPayload.put("paymentCompletedAt", savedOrder.getPaymentCompletedAt());
			eventPayload.put("autoConfirmScheduledAt", savedOrder.getAutoConfirmScheduledAt());

			outboxPort.createAndSave(
				"ORDER",
				savedOrder.getOrderId(),
				OutboxEventType.ORDER_PAID,
				objectMapper.writeValueAsString(eventPayload)
			);
		} catch (Exception e) {
			log.error("ORDER_PAID 이벤트 저장 실패: orderId={}", savedOrder.getOrderId(), e);
		}

		// 업데이트 된 Order 반환
		return RequestPaymentResult.builder()
			.orderId(savedOrder.getOrderId())
			.orderStatus(savedOrder.getStatus().name())
			.paymentAmount(savedOrder.getFinalAmount())
			.paymentCompletedAt(savedOrder.getPaymentCompletedAt())
			.autoConfirmScheduledAt(savedOrder.getAutoConfirmScheduledAt())
			.build();
	}
}
