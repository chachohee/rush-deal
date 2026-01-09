package com.rushcrew.order_service.application.command.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.command.dto.command.CancelOrderCommand;
import com.rushcrew.order_service.application.command.dto.result.CancelOrderResult;
import com.rushcrew.order_service.application.command.port.out.OrderCommandPort;
import com.rushcrew.order_service.application.command.usecase.CancelOrderUseCase;
import com.rushcrew.order_service.application.port.out.OutboxPort;
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
public class CancelOrderService implements CancelOrderUseCase {

	private final OrderCommandPort orderCommandPort;
	private final StockEventPort stockEventPort;
	private final PointEventPort pointEventPort;
	private final OutboxPort outboxPort;
	private final ObjectMapper objectMapper;

	@Override
	@Transactional
	public CancelOrderResult cancelOrder(CancelOrderCommand command) {
		log.info("주문 취소 처리 시작: orderId={}, userId={}", command.orderId(), command.userId());

		Order order = orderCommandPort.findById(command.orderId())
			.orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

		if (!order.isOwnedBy(command.userId())) {
			throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
		}

		// 취소 가능 상태 검증 (PENDING 상태만 가능)
		if (!order.canCancelBeforePayment()) {
			throw new BusinessException(OrderErrorCode.ORDER_CANNOT_CANCEL);
		}

		// 주문 상태 변경 (PENDING → CANCELLED)
		order.cancelBeforePayment("시스템에 의한 주문 취소");

		// 각 예약된 재고에 대해 예약 취소 처리
		for (OrderReservation reservation : order.getReservations()) {
			if (reservation.getStatus() == ReservationStatus.RESERVED) {
				// 예약 상태를 CANCELLED로 변경
				reservation.cancel();
			}
		}

		// Order와 OrderReservation 변경사항 DB에 저장 (cascade로 함께 저장됨)
		Order savedOrder = orderCommandPort.save(order);

		log.info("주문 취소 완료: orderId={}", savedOrder.getOrderId());

		// 포인트 환불 이벤트 발행
		if (savedOrder.getPointUsed() != null && savedOrder.getPointUsed() > 0L) {
			try {
				pointEventPort.publishPointRefundRequested(
					savedOrder.getUserId(),
					savedOrder.getOrderId(),
					savedOrder.getSagaId(),
					savedOrder.getPointUsed(),
					"주문 취소에 의한 포인트 환불"
				);
				log.info("포인트 환불 이벤트 발행 완료: orderId={}, pointUsed={}",
					savedOrder.getOrderId(), savedOrder.getPointUsed());
			} catch (Exception e) {
				log.error("포인트 환불 이벤트 발행 실패: orderId={}", savedOrder.getOrderId());
			}
		}
		// 각 예약된 재고에 대해 예약 해제 이벤트 발행
		for (OrderReservation reservation : savedOrder.getReservations()) {
			if (reservation.getStatus() == ReservationStatus.CANCELLED) {
				try {
					stockEventPort.publishStockReservationCancelled(
						savedOrder.getOrderId(),
						reservation.getTimeDealStockId(),
						reservation.getQuantity(),
						"주문 취소에 의한 재고 예약 해제"
					);
				} catch (Exception e) {
					log.error("재고 예약 취소 이벤트 발행 실패: reservationId={}", reservation.getOrderReservationId(), e);
				}
			}
		}
		log.info("재고 예약 취소 이벤트 발행 완료: orderId={}, reservationCount={}",
			savedOrder.getOrderId(), savedOrder.getReservations().size());

		// outbox에 ORDER_CANCELLED 이벤트 저장
		try {
			Map<String, Object> eventPayload = new HashMap<>();
			eventPayload.put("orderId", savedOrder.getOrderId());
			eventPayload.put("userId", savedOrder.getUserId());
			eventPayload.put("status", savedOrder.getStatus().name());
			eventPayload.put("cancelledAt", savedOrder.getCancelledAt());
			eventPayload.put("pointRefunded", savedOrder.getPointUsed());

			outboxPort.createAndSave(
				"ORDER",
				savedOrder.getOrderId(),
				OutboxEventType.ORDER_CANCELLED,
				objectMapper.writeValueAsString(eventPayload)
			);
		} catch (Exception e) {
			log.error("ORDER_CANCELLED 이벤트 저장 실패: orderId={}", savedOrder.getOrderId(), e);
		}

		log.info("주문 취소 처리 완료: orderId={}, pointRefunded={}",
			savedOrder.getOrderId(), savedOrder.getPointUsed());

		return CancelOrderResult.builder()
			.orderId(savedOrder.getOrderId())
			.orderStatus(savedOrder.getStatus().name())
			.cancelledAt(savedOrder.getCancelledAt())
			.build();
	}
}
