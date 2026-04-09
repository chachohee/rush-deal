package com.rushcrew.order_service.infrastructure.messaging.consumer;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.order_service.application.port.out.OrderCachePort;
import com.rushcrew.order_service.application.port.out.OrderQueryPort;
import com.rushcrew.order_service.infrastructure.messaging.event.OrderCreatedEvent;
import com.rushcrew.order_service.infrastructure.monitoring.CustomMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 이벤트 소비자 - 실시간 캐시 동기화
 *
 * 역할:
 * - 주문 생성/변경 이벤트 수신 → 즉시 캐시 갱신
 * - CacheWarmingScheduler와 함께 2-tier 캐싱 전략 구성
 *   1. EventConsumer: 실시간 변경사항 즉시 반영 (< 1초)
 *   2. Scheduler: 정기적 대량 갱신 (6시간 주기)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

	private final OrderCachePort orderCachePort;
	private final OrderQueryPort orderQueryPort;
	private final ObjectMapper objectMapper;
	private final CustomMetrics customMetrics;

	/**
	 * ORDER_CREATED 이벤트
	 * → DB에서 완전한 주문 정보 조회 후 캐시 생성
	 *
	 * 장점:
	 * - 사용자가 주문 직후 조회 시 캐시 Hit (빠른 응답)
	 * - 스케줄러 대비 최대 6시간 빠른 캐시 반영
	 */
	@KafkaListener(topics = "order.created", groupId = "order-cache-sync")
	public void handleOrderCreatedEvent(
		@Payload String message,
		@Header(KafkaHeaders.RECEIVED_TOPIC) String topic
	) {
		long startTime = System.currentTimeMillis();
		String orderIdStr = null;
		String eventType = deriveEventTypeFromTopic(topic);

		try {
			OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
			final UUID orderId = event.orderId();
			orderIdStr = orderId.toString();

			log.info("[EventConsumer] {} 이벤트 수신: orderId={}", eventType, orderId);

			// 멱등성 체크
			if (orderCachePort.existsInCache(orderId)) {
				log.info("[EventConsumer] 이미 캐시에 존재 (멱등성): orderId={}", orderId);
				customMetrics.recordCacheSyncSkipped(eventType);
				return;
			}

			// DB에서 완전한 주문 정보 조회
			orderQueryPort.findOrderDetail(orderId)
				.ifPresentOrElse(
					dto -> {
						orderCachePort.updateOrderCache(orderId, dto);
						long duration = System.currentTimeMillis() - startTime;

						log.info("[EventConsumer] 캐시 생성 완료: orderId={}, status={}, duration={}ms",
							orderId, dto.getOrderStatus(), duration);

						customMetrics.recordCacheSyncSuccess(eventType, duration);
					},
					() -> {
						// 트랜잭션 커밋 전이거나 DB 복제 지연
						log.warn("[EventConsumer] DB 조회 실패 (트랜잭션 대기 중?): orderId={}", orderId);
						customMetrics.recordCacheSyncFailure(eventType, "DB_NOT_FOUND");
						throw new RuntimeException("Order not found in DB: " + orderId);
					}
				);

		} catch (JsonProcessingException e) {
			log.error("[EventConsumer] 이벤트 파싱 실패 (orderId={}): message={}",
				orderIdStr != null ? orderIdStr : "unknown", message, e);
			customMetrics.recordCacheSyncFailure(eventType, "PARSE_ERROR");

		} catch (Exception e) {
			log.error("[EventConsumer] 캐시 동기화 실패: orderId={}",
				orderIdStr != null ? orderIdStr : "unknown", e);
			customMetrics.recordCacheSyncFailure(eventType, "UNKNOWN_ERROR");
			throw new RuntimeException(e);
		}
	}

	/**
	 * 주문 상태 변경 이벤트
	 * → DB 조회 후 캐시 갱신
	 *
	 * 처리 이벤트:
	 * - order.paid: 결제 완료
	 * - order.purchase.confirmed: 구매 확정
	 * - order.cancelled: 주문 취소
	 * - order.refunded: 환불 완료
	 * - order.updated: 주문 정보 수정
	 */
	@KafkaListener(
		topics = {
			"order.paid",
			"order.purchase.confirmed",
			"order.cancelled",
			"order.refunded",
			"order.updated"
		},
		groupId = "order-cache-sync"
	)
	public void handleOrderStatusChangedEvent(
		@Payload String message,
		@Header(KafkaHeaders.RECEIVED_TOPIC) String topic
	) {
		long startTime = System.currentTimeMillis();
		String orderIdStr = null;
		String eventType = deriveEventTypeFromTopic(topic);

		try {
			JsonNode rootNode = objectMapper.readTree(message);
			final UUID orderId = UUID.fromString(rootNode.get("orderId").asText());
			orderIdStr = orderId.toString();

			log.info("[EventConsumer] {} 이벤트 수신: orderId={}", eventType, orderId);

			orderQueryPort.findOrderDetail(orderId)
				.ifPresentOrElse(
					dto -> {
						orderCachePort.updateOrderCache(orderId, dto);
						long duration = System.currentTimeMillis() - startTime;

						log.info("[EventConsumer] 캐시 갱신 완료: orderId={}, status={}, eventType={}, duration={}ms",
							orderId, dto.getOrderStatus(), eventType, duration);

						customMetrics.recordCacheSyncSuccess(eventType, duration);
					},
					() -> {
						log.warn("[EventConsumer] DB 조회 실패로 캐시 갱신 생략: orderId={}, eventType={}",
							orderId, eventType);
						customMetrics.recordCacheSyncFailure(eventType, "DB_NOT_FOUND");
					}
				);

		} catch (JsonProcessingException e) {
			log.error("[EventConsumer] 이벤트 파싱 실패 (eventType={}, orderId={}): message={}",
				eventType, orderIdStr != null ? orderIdStr : "unknown", message, e);
			customMetrics.recordCacheSyncFailure(eventType, "PARSE_ERROR");

		} catch (Exception e) {
			log.error("[EventConsumer] 캐시 갱신 실패: orderId={}, eventType={}",
				orderIdStr != null ? orderIdStr : "unknown", eventType, e);
			customMetrics.recordCacheSyncFailure(eventType, "UNKNOWN_ERROR");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Topic 이름으로부터 eventType 추정
	 * - OutboxEventScheduler의 getTopicName() 매핑과 동일한 규칙
	 */
	private String deriveEventTypeFromTopic(String topic) {
		return switch (topic) {
			case "order.created" -> "ORDER_CREATED";
			case "order.paid" -> "ORDER_PAID";
			case "order.purchase.confirmed" -> "ORDER_PURCHASE_CONFIRMED";
			case "order.cancelled" -> "ORDER_CANCELLED";
			case "order.refunded" -> "ORDER_REFUNDED";
			case "order.updated" -> "ORDER_UPDATED";
			default -> "UNKNOWN";
		};
	}
}
