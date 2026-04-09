package com.rushcrew.order_service.infrastructure.config;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.rushcrew.order_service.application.query.dto.OrderDetailDto;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;

@Configuration
public class LocalCacheConfig {

	/**
	 * 주문 상세 L1 로컬 캐시 (Caffeine)
	 *
	 * - 최대 500건: 인스턴스당 Hot Data만 보관
	 * - TTL 5분: Redis L2(1시간)보다 짧게 설정해 데이터 정합성 보장
	 * - recordStats + CaffeineCacheMetrics: Prometheus에 히트/미스/제거 메트릭 노출
	 */
	@Bean
	public Cache<UUID, OrderDetailDto> orderLocalCache(MeterRegistry meterRegistry) {
		Cache<UUID, OrderDetailDto> cache = Caffeine.newBuilder()
			.maximumSize(500)
			.expireAfterWrite(5, TimeUnit.MINUTES)
			.recordStats()
			.build();
		CaffeineCacheMetrics.monitor(meterRegistry, cache, "order_local");
		return cache;
	}
}
