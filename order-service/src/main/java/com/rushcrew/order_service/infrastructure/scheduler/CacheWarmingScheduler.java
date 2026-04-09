package com.rushcrew.order_service.infrastructure.scheduler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.rushcrew.order_service.application.port.out.OrderCachePort;
import com.rushcrew.order_service.application.port.out.OrderQueryPort;
import com.rushcrew.order_service.infrastructure.monitoring.CustomMetrics;
import com.rushcrew.order_service.infrastructure.persistence.order.OrderJpaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 캐시 워밍 스케줄러
 * 전략:
 * 1. 애플리케이션 시작 시: 최근 24시간 주문 캐싱
 * 2. 6시간마다: Hot Data 재캐싱 (이벤트 누락 방어)
 * 3. 매일 새벽: Cold Data 정리 (메모리 최적화)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmingScheduler {

	private final OrderJpaRepository orderRepository;
	private final OrderCachePort orderCachePort;
	private final OrderQueryPort orderQueryPort;
	private final CustomMetrics customMetrics;

	/**
	 * 애플리케이션 시작 시 최근 주문 캐시 Warming
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void warmupCacheOnStartup() {
		log.info("Cache Warming 시작");
		long startTime = System.currentTimeMillis();
		try {
			// 최근 24시간 이내 주문 ID 조회
			Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
			List<UUID> recentOrderIds = orderRepository.findRecentOrderIds(oneDayAgo);

			log.info("[Scheduler] 캐싱 대상: {}개 주문 (최근 24시간)", recentOrderIds.size());

			int cachedCount = 0;
			int failedCount = 0;

			for (UUID orderId : recentOrderIds) {
				try {
					// 이미 이벤트로 캐시된 경우 덮어쓰기 (멱등성)
					orderQueryPort.findOrderDetail(orderId)
						.ifPresent(dto -> {
							orderCachePort.updateOrderCache(orderId, dto);
						});
					cachedCount++;
				} catch (Exception e) {
					log.warn("주문 캐싱 실패: orderId={}", orderId, e);
					failedCount++;
				}
			}

			long duration = System.currentTimeMillis() - startTime;
			log.info("[Scheduler] Cache Warming 완료: 성공={}, 실패={}, 소요시간={}ms",
				cachedCount, failedCount, duration);

			customMetrics.recordCacheWarmingCompleted(cachedCount, failedCount, duration);

		} catch (Exception e) {
			log.error("[Scheduler] Cache Warming 실패", e);
			customMetrics.recordCacheWarmingFailed();
		}
	}

	/**
	 * 매  6시간마다 Hot Data 재캐싱
	 * - OrderEventConsumer가 실시간 갱신을 담당하고
	 * - 이 스케줄러는 백업 역할
	 */
	@Scheduled(cron = "0 0 */6 * * ?") // 매 6시간 (0시, 6시, 12시, 18시)
	public void refreshHotDataCache() {
		log.info("[Scheduler] Hot Data Cache 갱신 시작");
		long startTime = System.currentTimeMillis();

		try {
			// 최근 6시간 이내 주문 ID 조회
			Instant sixHoursAgo = Instant.now().minus(6, ChronoUnit.HOURS);
			List<UUID> hotOrderIds = orderRepository.findRecentOrderIds(sixHoursAgo);

			log.info("[Scheduler] 갱신 대상: {}개 주문 (최근 6시간)", hotOrderIds.size());

			int refreshedCount = 0;
			int skippedCount = 0;
			int failedCount = 0;

			for (UUID orderId : hotOrderIds) {
				try {
					// 이미 캐시에 있어도 덮어쓰기 (최신 데이터 보장)
					orderQueryPort.findOrderDetail(orderId)
						.ifPresentOrElse(
							dto -> {
								orderCachePort.updateOrderCache(orderId, dto);
							},
							() -> {
								log.debug("[Scheduler] 주문 조회 실패 (삭제됨?): orderId={}", orderId);
							}
						);
					refreshedCount++;
				} catch (Exception e) {
					log.warn("[Scheduler] Hot Data 캐시 갱신 실패: orderId={}", orderId, e);
					failedCount++;
				}
			}

			long duration = System.currentTimeMillis() - startTime;
			log.info("[Scheduler] Hot Data Cache 갱신 완료: 성공={}, 실패={}, 소요시간={}ms",
				refreshedCount, failedCount, duration);

			customMetrics.recordHotDataRefreshCompleted(refreshedCount, failedCount, duration);

		} catch (Exception e) {
			log.error("[Scheduler] Hot Data Cache 갱신 실패", e);
			customMetrics.recordHotDataRefreshFailed();
		}
	}

	/**
	 * 7일 이상 지난 주문 캐시 삭제
	 */
	@Scheduled(cron = "0 0 2 * * ?") // 매일 새벽 2시
	public void cleanupColdDataCache() {
		log.info("[Scheduler] Cold Data Cache 정리 시작");
		long startTime = System.currentTimeMillis();

		try {
			Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
			List<UUID> oldOrderIds = orderRepository.findOrderIdsBefore(sevenDaysAgo);

			log.info("[Scheduler] 정리 대상: {}개 주문 (7일 이상 경과)", oldOrderIds.size());

			int deletedCount = 0;
			int failedCount = 0;

			for (UUID orderId : oldOrderIds) {
				try {
					orderCachePort.evictOrderCache(orderId);
					deletedCount++;
				} catch (Exception e) {
					log.warn("[Scheduler] 캐시 삭제 실패: orderId={}", orderId, e);
					failedCount++;
				}
			}

			long duration = System.currentTimeMillis() - startTime;
			log.info("[Scheduler] Cold Data Cache 정리 완료: 삭제={}, 실패={}, 소요시간={}ms",
				deletedCount, failedCount, duration);

			customMetrics.recordColdDataCleanupCompleted(deletedCount, failedCount, duration);

		} catch (Exception e) {
			log.error("[Scheduler] Cold Data Cache 정리 실패", e);
			customMetrics.recordColdDataCleanupFailed();
		}
	}

}
