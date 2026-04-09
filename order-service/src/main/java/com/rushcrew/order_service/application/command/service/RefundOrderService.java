package com.rushcrew.order_service.application.command.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.command.dto.command.RefundOrderCommand;
import com.rushcrew.order_service.application.command.dto.result.RefundOrderResult;
import com.rushcrew.order_service.application.port.out.OrderCommandPort;
import com.rushcrew.order_service.application.command.usecase.RefundOrderUseCase;
import com.rushcrew.order_service.application.port.out.OutboxPort;
import com.rushcrew.order_service.application.port.out.PaymentPort;
import com.rushcrew.order_service.application.port.out.PointEventPort;
import com.rushcrew.order_service.application.port.out.StockEventPort;
import com.rushcrew.order_service.domain.enums.ReservationStatus;
import com.rushcrew.order_service.domain.model.order.Order;
import com.rushcrew.order_service.domain.model.order.OrderReservation;
import com.rushcrew.order_service.global.advice.OrderErrorCode;
import com.rushcrew.order_service.infrastructure.messaging.event.OutboxEventType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundOrderService implements RefundOrderUseCase {

	private final OrderCommandPort orderCommandPort;
	private final PaymentPort paymentPort;
	private final PointEventPort pointEventPort;
	private final StockEventPort stockEventPort;
	private final OutboxPort outboxPort;
	private final ObjectMapper objectMapper;

	/**
	 * 주문 환불 처리
	 *
	 * 환불은 PAID 상태에서만 가능하다
	 * 구매확정(PURCHASE_CONFIRMED) 후에는 환불 불가능
	 *
	 * 처리 순서:
	 * 1. 주문 검증 (존재 여부, 소유자 확인)
	 * 2. 환불 가능 상태 검증 (PAID 상태만 가능)
	 * 3. 결제 취소 (Payment Service)
	 * 4. 주문 상태 변경 (PAID → REFUNDED)
	 * 5. 포인트 환불 이벤트 발행
	 * 6. 재고 복구 이벤트 발행
	 * 7. 주문 환불 이벤트 발행
	 */
	@Override
	@Transactional
	public RefundOrderResult refundOrder(RefundOrderCommand command) {
		log.info("환불 처리 시작: orderId={}, userId={}", command.orderId(), command.userId());

		Order order = orderCommandPort.findById(command.orderId())
			.orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

		if (!order.isOwnedBy(command.userId())) {
			throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
		}

		// 환불 가능 상태 검증: PAID 상태만 가능 (구매확정 후 환불 불가)
		if (!order.canRefund()) {
			throw new BusinessException(OrderErrorCode.ORDER_CANNOT_REFUND);
		}

		// 결제 취소
		try {
			paymentPort.cancelPayment(
				order.getOrderId(),
				order.getUserId()
			);
			log.info("결제 취소 완료: orderId={}, refundAmount={}",
				order.getOrderId(), order.getFinalAmount());
		} catch (Exception e) {
			log.error("결제 취소 실패: orderId={}, refundAmount={}",
				order.getOrderId(), order.getFinalAmount(), e);
			throw new RuntimeException("결제 취소 실패: " + e.getMessage(), e);
		}

		// 주문 상태 변경: PAID → REFUNDED
		order.refund(command.reason() != null ? command.reason() : "사용자 요청에 의한 환불");
		Order savedOrder = orderCommandPort.save(order);	// DB 저장

		log.info("주문 환불 완료: orderId={}, refundedAt={}",
			savedOrder.getOrderId(), savedOrder.getRefundedAt());

		// 포인트 환불 이벤트 발행
		if (savedOrder.getPointUsed() != null && savedOrder.getPointUsed() > 0L) {
			try {
				pointEventPort.publishPointRefundRequested(
					savedOrder.getUserId(),
					savedOrder.getOrderId(),
					savedOrder.getSagaId(),
					savedOrder.getPointUsed(),
					"주문 환불에 의한 포인트 환불"
				);
				log.info("포인트 환불 이벤트 발행 완료: orderId={}, pointUsed={}",
					savedOrder.getOrderId(), savedOrder.getPointUsed());
			} catch (Exception e) {
				log.error("포인트 환불 이벤트 발행 실패: orderId={}", savedOrder.getOrderId(), e);
				// 실패해도 주문 환불은 계속 진행
			}
		}

		// 재고 복구 이벤트 발행 (배치)
		try {
			// CONFIRMED 상태인 예약만 수집 (결제 완료된 예약)
			Map<UUID, Long> refundStockReservations = savedOrder.getReservations().stream()
				.filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
				.collect(Collectors.toMap(
					OrderReservation::getTimeDealStockId,
					OrderReservation::getQuantity
				));

			stockEventPort.publishStockReservationCancelledBatch(
				savedOrder.getOrderId(),
				savedOrder.getSagaId(),
				refundStockReservations,
				"주문 환불에 의한 재고 복구"
			);

			log.info("재고 복구 이벤트 발행 완료: orderId={}, itemCount={}",
				savedOrder.getOrderId(), refundStockReservations.size());
		} catch (Exception e) {
			log.error("재고 복구 이벤트 발행 실패: orderId={}", savedOrder.getOrderId(), e);
			// 실패해도 주문 환불은 계속 진행
		}

		// ORDER_REFUNDED 이벤트를 Outbox에 저장
		try {
			Map<String, Object> eventPayload = new HashMap<>();
			eventPayload.put("orderId", savedOrder.getOrderId());
			eventPayload.put("userId", savedOrder.getUserId());
			eventPayload.put("status", savedOrder.getStatus().name());
			eventPayload.put("refundAmount", savedOrder.getFinalAmount());
			eventPayload.put("refundedAt", savedOrder.getRefundedAt());

			outboxPort.createAndSave(
				"ORDER",
				savedOrder.getOrderId(),
				OutboxEventType.ORDER_REFUNDED,
				objectMapper.writeValueAsString(eventPayload)
			);
		} catch (Exception e) {
			log.error("ORDER_REFUNDED 이벤트 저장 실패: orderId={}", savedOrder.getOrderId(), e);
			// 이벤트 저장 실패는 치명적이지 않으므로 계속 진행
		}

		log.info("환불 처리 완료: orderId={}", savedOrder.getOrderId());

		return RefundOrderResult.builder()
			.orderId(savedOrder.getOrderId())
			.message("환불이 완료되었습니다.")
			.build();
	}
}
