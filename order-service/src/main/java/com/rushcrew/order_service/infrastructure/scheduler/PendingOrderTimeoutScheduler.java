package com.rushcrew.order_service.infrastructure.scheduler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.rushcrew.order_service.application.command.dto.command.CancelOrderCommand;
import com.rushcrew.order_service.application.command.port.out.OrderCommandPort;
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
	// private static final int PENDING_TIMEOUT_MINUTES = 15; // 15분
	private static final int BATCH_SIZE = 100;

	// /**
	//  * 5분마다 타임아웃된 PENDING 주문 자동 취소
	//  */
	// @Scheduled(fixedDelay = 300_000) // 5분
	/**
	 * 1분마다 타임아웃된 PENDING 주문 자동 취소 (테스트용)
	 */
	@Scheduled(fixedDelay = 60_000) // 5분 → 1분으로 변경
	public void cancelTimedOutPendingOrders() {
		Instant timeoutThreshold = Instant.now().minus(PENDING_TIMEOUT_MINUTES, ChronoUnit.MINUTES);

		// PENDING 상태이고 15분 이상 경과한 주문 조회
		List<Order> timedOutOrders = orderCommandPort.findTimedOutPendingOrders(
			OrderStatus.PENDING,
			timeoutThreshold,
			PageRequest.of(0, BATCH_SIZE)
		);

		if (timedOutOrders.isEmpty()) {
			return;
		}

		log.warn("타임아웃된 PENDING 주문 {}개 발견", timedOutOrders.size());

		int successCount = 0;
		int failCount = 0;

		for (Order order : timedOutOrders) {
			try {
				// 자동 취소 (시스템 권한)
				CancelOrderCommand command = CancelOrderCommand.ofSystem(
					order.getOrderId(),
					order.getUserId(),
					"결제 미완료로 자동 취소 (15분 타임아웃)"
				);

				cancelOrderUseCase.cancelOrder(command);
				successCount++;

				log.info("PENDING 주문 자동 취소 완료: orderId={}, createdAt={}",
					order.getOrderId(), order.getOrderedAt());

			} catch (Exception e) {
				log.error("PENDING 주문 자동 취소 실패: orderId={}",
					order.getOrderId(), e);
				failCount++;
			}
		}

		log.info("PENDING 주문 자동 취소 완료: 성공={}, 실패={}", successCount, failCount);
	}
}
