package com.rushcrew.order_service.infrastructure.persistence.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.rushcrew.order_service.application.port.dto.OutboxEvent;
import com.rushcrew.order_service.application.port.out.OutboxPort;
import com.rushcrew.order_service.infrastructure.persistence.outbox.entity.OutboxEventEntity;
import com.rushcrew.order_service.infrastructure.persistence.outbox.repository.OutboxEventJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OutboxAdapter implements OutboxPort {

	private final OutboxEventJpaRepository outboxRepository;

	@Override
	public OutboxEvent save(OutboxEvent event) {
		OutboxEventEntity entity = toEntity(event);
		OutboxEventEntity savedEntity = outboxRepository.save(entity);
		return toDomain(savedEntity);
	}

	@Override
	public OutboxEvent createAndSave(String aggregateType, UUID aggregateId, String eventType, String payload) {
		OutboxEvent newEvent = OutboxEvent.builder()
			.eventId(UUID.randomUUID())
			.aggregateType(aggregateType)
			.aggregateId(aggregateId)
			.eventType(eventType)
			.payload(payload)
			.status("PENDING")
			.createdAt(Instant.now())
			.retryCount(0)
			.build();

		return save(newEvent);
	}

	private OutboxEventEntity toEntity(OutboxEvent event) {
		return OutboxEventEntity.builder()
			.eventId(event.eventId())
			.aggregateType(event.aggregateType())
			.aggregateId(event.aggregateId())
			.eventType(event.eventType())
			.payload(event.payload())
			.status(OutboxEventEntity.OutboxStatus.valueOf(event.status()))
			.createdAt(event.createdAt())
			.publishedAt(event.publishedAt())
			.failedAt(event.failedAt())
			.retryCount(event.retryCount())
			.errorMessage(event.errorMessage())
			.build();
	}

	/* Mapper */
	private OutboxEvent toDomain(OutboxEventEntity entity) {
		return OutboxEvent.builder()
			.eventId(entity.getEventId())
			.aggregateType(entity.getAggregateType())
			.aggregateId(entity.getAggregateId())
			.eventType(entity.getEventType())
			.payload(entity.getPayload())
			.status(entity.getStatus().name())
			.createdAt(entity.getCreatedAt())
			.publishedAt(entity.getPublishedAt())
			.failedAt(entity.getFailedAt())
			.retryCount(entity.getRetryCount())
			.errorMessage(entity.getErrorMessage())
			.build();
	}
}
