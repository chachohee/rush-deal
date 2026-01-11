package com.rushcrew.order_service.infrastructure.adapter.out.messaging;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.order_service.application.port.out.PointEventPort;
import com.rushcrew.order_service.infrastructure.persistence.outbox.entity.OutboxEventEntity;
import com.rushcrew.order_service.infrastructure.persistence.outbox.repository.OutboxEventJpaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointEventPublisher implements PointEventPort {

	private final OutboxEventJpaRepository outboxRepository;
	private final ObjectMapper objectMapper;

	@Override
	public void publishPointEarnRequested(
		Long userId,
		UUID orderId,
		BigDecimal finalAmount,
		UUID sagaId,
		String reason
	) {
		try {
			log.info("포인트 적립 요청 이벤트 발행: userId={}, orderId={}, finalAmount={}", userId, orderId, finalAmount);

			Map<String, Object> event = new HashMap<>();
			event.put("userId", userId);
			event.put("orderId", orderId.toString());
			event.put("finalAmount", finalAmount);
			event.put("sagaId", sagaId);
			// event.put("reason", reason);
			// event.put("timestamp", timestamp.toString());

			String payload = objectMapper.writeValueAsString(event);

			OutboxEventEntity outbox = OutboxEventEntity.create(
				"ORDER",       // aggregateType
				orderId,                     // aggregateId
				"POINT_EARN_REQUESTED",      // eventType
				payload                      // json
			);

			outboxRepository.save(outbox);
			log.info("포인트 적립 요청 이벤트 Outbox 저장 완료: orderId={}", orderId);

		} catch (Exception e) {
			log.error("포인트 적립 요청 이벤트 발행 실패: orderId={}", orderId, e);
			throw new RuntimeException("포인트 적립 요청 이벤트 발행 실패", e);
		}
	}

	@Override
	public void publishPointUseCancellRequested(Long userId, UUID orderId, UUID sagaId, Long pointUsed, String reason) {
		try {
			// log.info("포인트 사용 취소 요청 이벤트 발행: userId={}, orderId={}, pointUsed={}", userId, orderId, pointUsed);

			StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
			log.info("!!! 포인트 취소 이벤트 발행 호출됨 !!!");
			log.info("orderId: {}, userId: {}, pointUsed: {}", orderId, userId, pointUsed);
			log.info("호출 위치: {}.{}({}:{})",
				caller.getClassName(),
				caller.getMethodName(),
				caller.getFileName(),
				caller.getLineNumber());

			Map<String, Object> event = new HashMap<>();
			event.put("userId", userId);
			event.put("orderId", orderId.toString());
			event.put("sagaId", sagaId);

			String payload = objectMapper.writeValueAsString(event);

			OutboxEventEntity outbox = OutboxEventEntity.create(
				"ORDER",         // aggregateType
				orderId,                     // aggregateId
				"POINT_USE_CANCEL_REQUESTED",     // eventType
				payload                      // json
			);

			outboxRepository.save(outbox);
			log.info("포인트 사용 취소 요청 이벤트 Outbox 저장 완료: orderId={}", orderId);

		} catch (Exception e) {
			log.error("포인트 사용 취소 요청 이벤트 발행 실패: orderId={}", orderId, e);
			throw new RuntimeException("포인트 사용 취소 요청 이벤트 발행 실패", e);
		}
	}

	// TODO: 검토 및 수정 필요
	@Override
	public void publishPointRefundRequested(Long userId, UUID orderId, UUID sagaId, Long pointUsed, String reason) {
		try {
			log.info("포인트 환불 요청 이벤트 발행: userId={}, orderId={}, pointUsed={}", userId, orderId, pointUsed);

			Map<String, Object> event = new HashMap<>();
			event.put("userId", userId);
			event.put("orderId", orderId.toString());
			event.put("sagaId", sagaId);
			// event.put("pointUsed", pointUsed);
			// event.put("reason", reason);
			// event.put("timestamp", timestamp.toString());

			String payload = objectMapper.writeValueAsString(event);

			OutboxEventEntity outbox = OutboxEventEntity.create(
				"ORDER",         // aggregateType
				orderId,                     // aggregateId
				"POINT_REFUND_REQUESTED",     // eventType
				payload                      // json
			);

			outboxRepository.save(outbox);
			log.info("포인트 환불 요청 이벤트 Outbox 저장 완료: orderId={}", orderId);

		} catch (Exception e) {
			log.error("포인트 환불 요청 이벤트 발행 실패: orderId={}", orderId, e);
			throw new RuntimeException("포인트 환불 요청 이벤트 발행 실패", e);
		}
	}
}
