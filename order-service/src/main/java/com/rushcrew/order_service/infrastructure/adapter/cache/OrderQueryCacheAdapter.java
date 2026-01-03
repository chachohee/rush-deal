package com.rushcrew.order_service.infrastructure.adapter.cache;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.order_service.application.command.port.out.OrderCachePort;
import com.rushcrew.order_service.application.query.dto.OrderDetailDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class OrderQueryCacheAdapter implements OrderCachePort {

	private final RedisTemplate<String, Object> redisTemplate;
	private final ObjectMapper objectMapper;

	private static final String ORDER_KEY_PREFIX = "order:";
	private static final long ORDER_CACHE_TTL = 3600; // 1시간

	@Override
	public void updateOrderCache(UUID orderId, OrderDetailDto orderDetailDto) {
		try {
			String key = ORDER_KEY_PREFIX + orderId;

			redisTemplate.opsForValue().set(
				key,
				objectMapper.writeValueAsString(orderDetailDto),
				ORDER_CACHE_TTL,
				TimeUnit.SECONDS
			);

			log.debug("[Cache] 주문 캐시 업데이트: orderId={}, status={}",
				orderId, orderDetailDto.getOrderStatus());

		} catch (JsonProcessingException e) {
			log.error("[Cache] 주문 캐시 업데이트 실패 (JSON 변환): orderId={}", orderId, e);
			throw new RuntimeException("Redis 캐시 업데이트 실패", e);
		} catch (Exception e) {
			log.error("[Cache] 주문 캐시 업데이트 실패 (Redis 오류): orderId={}", orderId, e);
			// Redis 장애 시에도 비즈니스 로직은 계속 진행되도록
		}
	}

	@Override
	public boolean existsInCache(UUID orderId) {
		try {
			String key = ORDER_KEY_PREFIX + orderId;
			Boolean exists = redisTemplate.hasKey(key);

			if (exists != null && exists) {
				log.debug("[Cache] 캐시에 존재: orderId={}", orderId);
				return true;
			}

			log.debug("[Cache] 캐시에 없음: orderId={}", orderId);
			return false;

		} catch (Exception e) {
			log.error("[Cache] 캐시 존재 여부 확인 실패: orderId={}", orderId, e);
			// Redis 장애 시 false 반환 (멱등성 체크 실패해도 업데이트는 진행)
			return false;
		}
	}

	@Override
	public void evictOrderCache(UUID orderId) {
		try {
			String key = ORDER_KEY_PREFIX + orderId;
			Boolean deleted = redisTemplate.delete(key);

			if (deleted != null && deleted) {
				log.debug("[Cache] 주문 캐시 삭제 완료: orderId={}", orderId);
			} else {
				log.debug("[Cache] 삭제할 캐시 없음: orderId={}", orderId);
			}

		} catch (Exception e) {
			log.error("[Cache] 주문 캐시 삭제 실패: orderId={}", orderId, e);
			// 삭제 실패는 무시 (TTL로 자동 만료됨)
		}
	}
}
