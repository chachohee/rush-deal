package com.rushcrew.order_service.infrastructure.adapter.cache;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.rushcrew.order_service.application.command.port.out.OrderCachePort;
import com.rushcrew.order_service.application.query.dto.OrderDetailDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 2단계 캐시 어댑터
 *
 * L1 (Caffeine): 인스턴스 로컬 캐시, TTL 5분, 최대 500건
 * L2 (Redis):    분산 캐시, TTL 1시간, 인스턴스 간 공유
 *
 * 읽기: L1 → L2 → (miss 시 호출자가 DB 조회)
 * 쓰기: L1 + L2 동시 업데이트
 * 삭제: L1 + L2 동시 제거
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class OrderQueryCacheAdapter implements OrderCachePort {

	private final RedisTemplate<String, Object> redisTemplate;
	private final ObjectMapper objectMapper;
	private final Cache<UUID, OrderDetailDto> orderLocalCache;

	private static final String ORDER_KEY_PREFIX = "order:";
	private static final long ORDER_CACHE_TTL_SECONDS = 3600; // 1시간

	/**
	 * 캐시에서 주문 조회 (L1 → L2 순서)
	 * L2 히트 시 L1에 자동 적재
	 */
	@Override
	public Optional<OrderDetailDto> getFromCache(UUID orderId) {
		// L1 확인
		OrderDetailDto l1Hit = orderLocalCache.getIfPresent(orderId);
		if (l1Hit != null) {
			log.debug("[Cache] L1 히트: orderId={}", orderId);
			return Optional.of(l1Hit);
		}

		// L2 확인
		try {
			Object raw = redisTemplate.opsForValue().get(ORDER_KEY_PREFIX + orderId);
			if (raw != null) {
				OrderDetailDto dto = objectMapper.readValue(raw.toString(), OrderDetailDto.class);
				orderLocalCache.put(orderId, dto);
				log.debug("[Cache] L2 히트, L1 적재: orderId={}", orderId);
				return Optional.of(dto);
			}
		} catch (Exception e) {
			log.warn("[Cache] L2 조회 실패: orderId={}", orderId, e);
		}

		log.debug("[Cache] 미스: orderId={}", orderId);
		return Optional.empty();
	}

	/**
	 * L1 + L2 동시 업데이트
	 */
	@Override
	public void updateOrderCache(UUID orderId, OrderDetailDto orderDetailDto) {
		orderLocalCache.put(orderId, orderDetailDto);

		try {
			redisTemplate.opsForValue().set(
				ORDER_KEY_PREFIX + orderId,
				objectMapper.writeValueAsString(orderDetailDto),
				ORDER_CACHE_TTL_SECONDS,
				TimeUnit.SECONDS
			);
			log.debug("[Cache] L1+L2 업데이트: orderId={}, status={}",
				orderId, orderDetailDto.getOrderStatus());
		} catch (JsonProcessingException e) {
			log.error("[Cache] L2 업데이트 실패 (JSON 변환): orderId={}", orderId, e);
			throw new RuntimeException("Redis 캐시 업데이트 실패", e);
		} catch (Exception e) {
			log.error("[Cache] L2 업데이트 실패 (Redis 오류): orderId={}", orderId, e);
		}
	}

	/**
	 * L1 → L2 순서로 존재 여부 확인
	 */
	@Override
	public boolean existsInCache(UUID orderId) {
		if (orderLocalCache.getIfPresent(orderId) != null) {
			return true;
		}
		try {
			Boolean exists = redisTemplate.hasKey(ORDER_KEY_PREFIX + orderId);
			return exists != null && exists;
		} catch (Exception e) {
			log.error("[Cache] 캐시 존재 여부 확인 실패: orderId={}", orderId, e);
			return false;
		}
	}

	/**
	 * L1 + L2 동시 제거
	 */
	@Override
	public void evictOrderCache(UUID orderId) {
		orderLocalCache.invalidate(orderId);

		try {
			redisTemplate.delete(ORDER_KEY_PREFIX + orderId);
			log.debug("[Cache] L1+L2 캐시 삭제: orderId={}", orderId);
		} catch (Exception e) {
			log.error("[Cache] L2 캐시 삭제 실패: orderId={}", orderId, e);
		}
	}
}
