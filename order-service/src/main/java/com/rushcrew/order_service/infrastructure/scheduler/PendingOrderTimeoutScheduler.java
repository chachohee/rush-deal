package com.rushcrew.order_service.infrastructure.scheduler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.rushcrew.order_service.application.command.dto.command.CancelOrderCommand;
import com.rushcrew.order_service.application.port.out.OrderCommandPort;
import com.rushcrew.order_service.application.command.usecase.CancelOrderUseCase;
import com.rushcrew.order_service.domain.enums.OrderStatus;
import com.rushcrew.order_service.domain.model.order.Order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PENDING 주문 타임아웃 스케줄러
 *
 * 역할:
 * - 주문 생성 후 일정 시간(30분) 내에 결제 안 한 주문 자동 취소
 * - 포인트/재고 자동 복구
 *
 * 실행 주기: 5분마다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrderTimeoutScheduler {

	private final OrderCommandPort orderCommandPort;
	private final CancelOrderUseCase cancelOrderUseCase;

	private static final int PENDING_TIMEOUT_MINUTES = 5; // 5분 (테스트용)
	private static final int BATCH_SIZE = 1000;	// (테스트용)
	// private static final int PENDING_TIMEOUT_MINUTES = 15; // 15분
	// private static final int BATCH_SIZE = 100;

	// /**
	//  * 5분마다 타임아웃된 PENDING 주문 자동 취소
	//  */
	// @Scheduled(fixedDelay = 300_000) // 5분
	/**
	 * 타임아웃된 PENDING 주문 자동 취소
	 */
	@Scheduled(fixedDelay = 60_000)
	public void cancelTimedOutPendingOrders() {
		Instant timeoutThreshold = Instant.now().minus(PENDING_TIMEOUT_MINUTES, ChronoUnit.MINUTES);

		int totalCancelled = 0;
		int page = 0;

		while (true) {
			List<Order> timedOutOrders = orderCommandPort.findTimedOutPendingOrders(
				OrderStatus.PENDING,
				timeoutThreshold,
				PageRequest.of(page++, BATCH_SIZE)
			);

			if (timedOutOrders.isEmpty()) {
				break;  // 더 이상 처리할 주문 없음
			}

			log.warn("========================================");
			log.warn("페이지 {}: 타임아웃된 PENDING 주문 {}개 발견", page, timedOutOrders.size());
			log.warn("========================================");

			int successCount = 0;
			int failCount = 0;

			for (Order order : timedOutOrders) {
				try {
					log.info(">>> 주문 취소 시도 [{}/{}]: orderId={}",
						successCount + failCount + 1,
						timedOutOrders.size(),
						order.getOrderId());

					CancelOrderCommand command = CancelOrderCommand.ofSystem(
						order.getOrderId(),
						order.getUserId(),
						"결제 미완료로 자동 취소 (5분 타임아웃)"
					);

					cancelOrderUseCase.cancelOrder(command);
					successCount++;

					log.info("<<< PENDING 주문 자동 취소 완료: orderId={}", order.getOrderId());

				} catch (Exception e) {
					log.error("<<< PENDING 주문 자동 취소 실패: orderId={}", order.getOrderId(), e);
					failCount++;
				}
			}

			totalCancelled += successCount;
			log.info("페이지 {} 완료: 성공={}, 실패={}", page, successCount, failCount);
		}

		if (totalCancelled > 0) {
			log.info("========================================");
			log.info("전체 PENDING 주문 자동 취소 완료: 총 {}개", totalCancelled);
			log.info("========================================");
		}
	}
}
