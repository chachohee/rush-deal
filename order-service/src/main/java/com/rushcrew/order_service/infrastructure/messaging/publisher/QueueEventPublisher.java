package com.rushcrew.order_service.infrastructure.messaging.publisher;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.order_service.application.port.out.QueueEventPort;
import com.rushcrew.order_service.infrastructure.persistence.outbox.entity.OutboxEventEntity;
import com.rushcrew.order_service.infrastructure.persistence.outbox.repository.OutboxEventJpaRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueEventPublisher implements QueueEventPort {

	private final OutboxEventJpaRepository outboxRepository;
	private final ObjectMapper objectMapper;

	@Override
	@Transactional
	public void publishTokenRemoveEvent(Long userId, UUID productId, String token) {
		try {
			// 페이로드 생성
			Map<String, Object> payload = Map.of(
				"userId", userId,
				"productId", productId,
				"token", token
			);

			String payloadJson = objectMapper.writeValueAsString(payload);

			// Outbox 이벤트 생성
			OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
				.eventId(UUID.randomUUID())
				.aggregateType("ORDER")
				.aggregateId(productId)
				.eventType("TOKEN_REMOVE_REQUESTED")
				.payload(payloadJson)
				.status(OutboxEventEntity.OutboxStatus.PENDING)
				.createdAt(Instant.now())
				.build();

			outboxRepository.save(outboxEvent);

			log.info("[Outbox] 토큰 삭제 이벤트 저장 완료 - UserId: {}, ProductId: {}, Token: {}",
				userId, productId, token);

		} catch (Exception e) {
			log.error("[Outbox] 토큰 삭제 이벤트 저장 실패 - UserId: {}, ProductId: {}",
				userId, productId, e);
		}
	}
}
