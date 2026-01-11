package com.rushcrew.order_service.infrastructure.scheduler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.rushcrew.order_service.infrastructure.monitoring.CustomMetrics;
import com.rushcrew.order_service.infrastructure.persistence.outbox.entity.OutboxEventEntity;
import com.rushcrew.order_service.infrastructure.persistence.outbox.repository.OutboxEventJpaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventScheduler {

	private final OutboxEventJpaRepository outboxRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final CustomMetrics customMetrics;

	/**
	 * 5초마다 PENDING 이벤트를 Kafka로 발행
	 * 
	 * 동시성 제어: FOR UPDATE SKIP LOCKED를 사용하여 여러 인스턴스가
	 * 동시에 실행해도 같은 이벤트를 중복 처리하지 않도록 보장
	 */
	@Scheduled(fixedDelay = 5000)
	@Transactional
	public void publishPendingEvents() {
		// 동시성 제어를 위해 FOR UPDATE SKIP LOCKED 사용
		List<OutboxEventEntity> pendingEvents =
			outboxRepository.findPendingEventsForUpdate(100);

		if (pendingEvents.isEmpty()) {
			return;
		}

		log.info("발행 대기 중인 Outbox 이벤트 {}개 발견", pendingEvents.size());

		for (OutboxEventEntity event : pendingEvents) {
			publishEventWithTransaction(event);
		}
	}

	/**
	 * 이벤트를 별도 트랜잭션으로 발행하여 상태 업데이트 보장
	 */
	@Transactional
	public void publishEventWithTransaction(OutboxEventEntity event) {
		try {
			String topic = getTopicName(event.getEventType());

			// 동기 방식으로 발행하여 트랜잭션 내에서 상태 업데이트
			try {
				kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload())
					.get(); // Future.get()으로 동기 대기

				event.markAsPublished();
				outboxRepository.save(event);
				customMetrics.recordOutboxPublished(); // 발행 메트릭 기록
				log.debug("Outbox 이벤트 발행 성공: eventId={}, eventType={}",
					event.getEventId(), event.getEventType());

			} catch (java.util.concurrent.ExecutionException e) {
				Throwable cause = e.getCause() != null ? e.getCause() : e;
				log.error("Outbox 이벤트 발행 실패: eventId={}, eventType={}",
					event.getEventId(), event.getEventType(), cause);
				event.markAsFailed(cause.getMessage());
				outboxRepository.save(event);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.error("Outbox 이벤트 발행 중 인터럽트 발생: eventId={}, eventType={}",
					event.getEventId(), event.getEventType(), e);
				event.markAsFailed("이벤트 발행 중 인터럽트 발생");
				outboxRepository.save(event);
			} catch (Exception e) {
				log.error("Outbox 이벤트 발행 실패: eventId={}, eventType={}",
					event.getEventId(), event.getEventType(), e);
				event.markAsFailed(e.getMessage());
				outboxRepository.save(event);
			}

		} catch (Exception e) {
			log.error("Outbox 이벤트 처리 중 오류: eventId={}", event.getEventId(), e);
			// 트랜잭션 내에서 실패 처리
			event.markAsFailed(e.getMessage());
			outboxRepository.save(event);
		}
	}


	/**
	 * 10분마다 FAILED 이벤트 재시도
	 */
	@Scheduled(fixedDelay = 600000)
	public void retryFailedEvents() {
		Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

		List<OutboxEventEntity> failedEvents =
			outboxRepository.findFailedEventsForRetry(oneHourAgo, Pageable.ofSize(50));

		if (failedEvents.isEmpty()) {
			return;
		}

		log.info("재시도 대상 Outbox 이벤트 {}개 발견", failedEvents.size());

		for (OutboxEventEntity event : failedEvents) {
			if (event.canRetry()) {
				// 재시도 상태로 변경 후 즉시 발행 시도
				retryEventWithTransaction(event);
			}
		}
	}

	/**
	 * 이벤트 재시도를 별도 트랜잭션으로 처리
	 */
	@Transactional
	public void retryEventWithTransaction(OutboxEventEntity event) {
		try {
			event.retry();
			outboxRepository.save(event);

			// 즉시 발행 시도
			publishEventWithTransaction(event);

		} catch (Exception e) {
			log.error("이벤트 재시도 실패: eventId={}", event.getEventId(), e);
		}
	}


	/**
	 * 매일 자정에 7일 이상 지난 PUBLISHED 이벤트 삭제
	 */
	@Scheduled(cron = "0 0 0 * * ?")
	@Transactional
	public void cleanupOldEvents() {
		Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
		int deletedCount = outboxRepository.deletePublishedEventsBefore(sevenDaysAgo);

		if (deletedCount > 0) {
			log.info("오래된 Outbox 이벤트 {}개 삭제 완료", deletedCount);
		}
	}

	private String getTopicName(String eventType) {
		return switch (eventType) {
			// 주문 이벤트
			case "ORDER_CREATED" -> "order.created";
			case "ORDER_UPDATED" -> "order.updated";
			case "ORDER_CANCELLED" -> "order.cancelled";
			case "ORDER_PAID" -> "order.paid";
			case "ORDER_PURCHASE_CONFIRMED" -> "order.purchase.confirmed";
			case "ORDER_REFUNDED" -> "order.refunded";

			// 결제 이벤트
			case "PAYMENT_COMPLETED" -> "payment.completed";
			case "PAYMENT_CANCELLED" -> "payment.cancelled";
			case "REFUND_REQUESTED" -> "payment.refund.requested";

			// 포인트 이벤트
			case "POINT_EARN_REQUESTED" -> "point.earn.requested";
			case "POINT_USE_CANCEL_REQUESTED" -> "point.use.cancel.requested"; // 주문 생성 시 포인트 사용 취소
			case "POINT_REFUND_REQUESTED" -> "point.refund.requested"; // 주문 환불 시

			// 재고 이벤트
			case "STOCK_RESERVATION_REQUESTED" -> "stock.reservation.requested";
			case "STOCK_ROLLBACK_REQUESTED" -> "stock.restore.requested";

			// 큐 이벤트
			case "TOKEN_REMOVE_REQUESTED" -> "order-complete-token-remove";

			default -> {
				log.warn("알 수 없는 이벤트 타입: {}, 기본 토픽 사용: order.events", eventType);
				yield "order.events";
			}
		};
	}
}
