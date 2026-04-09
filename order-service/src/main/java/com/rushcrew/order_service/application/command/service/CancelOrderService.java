package com.rushcrew.order_service.application.command.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.order_service.application.command.dto.command.CancelOrderCommand;
import com.rushcrew.order_service.application.command.dto.result.CancelOrderResult;
import com.rushcrew.order_service.application.port.out.OrderCommandPort;
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
		// log.info("주문 취소 처리 시작: orderId={}, userId={}", command.orderId(), command.userId());
		StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
		log.info("=== 주문 취소 처리 시작 ===");
		log.info("orderId={}, userId={}", command.orderId(), command.userId());
		log.info("호출 위치: {}.{}({}:{})",
			caller.getClassName(),
			caller.getMethodName(),
			caller.getFileName(),
			caller.getLineNumber());

		Order order = orderCommandPort.findById(command.orderId())
			.orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));

		if (!order.isOwnedBy(command.userId())) {
			throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
		}

		if (!order.canCancelBeforePayment()) {
			throw new BusinessException(OrderErrorCode.ORDER_CANNOT_CANCEL);
		}

		// 주문 상태 변경 (PENDING → CANCELLED)
		order.cancelBeforePayment("시스템에 의한 주문 취소");

		// ✅ RESERVED 상태인 예약들만 수집 (상태 변경 전)
		List<OrderReservation> cancelledReservations = order.getReservations().stream()
			.filter(reservation -> reservation.getStatus() == ReservationStatus.RESERVED)
			.collect(Collectors.toList());

		// ✅ 예약 상태 변경 (RESERVED → CANCELLED)
		cancelledReservations.forEach(OrderReservation::cancel);

		// Order 저장 (cascade로 OrderReservation도 함께 저장됨)
		Order savedOrder = orderCommandPort.save(order);

		log.info("주문 취소 완료: orderId={}, cancelledReservations={}",
			savedOrder.getOrderId(), cancelledReservations.size());

		// 포인트 사용 취소 이벤트 발행
		if (savedOrder.getPointUsed() != null && savedOrder.getPointUsed() > 0L) {
			try {
				pointEventPort.publishPointUseCancellRequested(
					savedOrder.getUserId(),
					savedOrder.getOrderId(),
					savedOrder.getSagaId(),
					savedOrder.getPointUsed(),
					"주문 취소에 의한 포인트 사용 취소"
				);
				log.info("포인트 사용 취소 이벤트 발행 완료: orderId={}, pointUsed={}",
					savedOrder.getOrderId(), savedOrder.getPointUsed());
			} catch (Exception e) {
				log.error("포인트 사용 취소 이벤트 발행 실패: orderId={}", savedOrder.getOrderId(), e);
			}
		}

		if (!cancelledReservations.isEmpty()) {
			try {
				Map<UUID, Long> stockReservations = cancelledReservations.stream()
					.collect(Collectors.toMap(
						OrderReservation::getTimeDealStockId,
						OrderReservation::getQuantity
					));

				stockEventPort.publishStockReservationCancelledBatch(
					savedOrder.getOrderId(),
					savedOrder.getSagaId(),
					stockReservations,
					"주문 취소에 의한 재고 예약 해제"
				);
				log.info("재고 예약 취소 배치 이벤트 발행 완료: orderId={}, itemCount={}",
					savedOrder.getOrderId(), stockReservations.size());
			} catch (Exception e) {
				log.error("재고 예약 취소 배치 이벤트 발행 실패: orderId={}", savedOrder.getOrderId(), e);
			}
		}

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
