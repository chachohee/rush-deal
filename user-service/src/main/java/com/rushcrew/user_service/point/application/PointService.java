package com.rushcrew.user_service.point.application;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.user_service.point.application.command.CancelOrderCommand;
import com.rushcrew.user_service.point.application.command.CreatePendingPointCommand;
import com.rushcrew.user_service.point.application.command.RefundPointCommand;
import com.rushcrew.user_service.point.application.command.UsePointCommand;
import com.rushcrew.user_service.point.domain.entity.PointHistory;
import com.rushcrew.user_service.point.domain.enums.PointType;
import com.rushcrew.user_service.point.domain.repository.PointHistoryQueryRepository;
import com.rushcrew.user_service.point.domain.repository.PointHistoryRepository;
import com.rushcrew.user_service.point.domain.service.PointDomainService;
import com.rushcrew.user_service.point.domain.vo.OrderId;
import com.rushcrew.user_service.point.domain.vo.Point;
import com.rushcrew.user_service.point.domain.vo.SagaId;
import com.rushcrew.user_service.point.domain.vo.UserId;
import com.rushcrew.user_service.point.exception.PointErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointService {

    private final RedissonClient redissonClient;
    private final PointDomainService pointDomainService;
    private final PointHistoryRepository pointHistoryRepository;
    private final PointHistoryQueryRepository pointHistoryQueryRepository;

    private final EntityManager entityManager;

    private static final int CONFIRM_PERIOD_DAYS = 7;

    // 주문 생성 시 예비 적립 포인트 이력 생성
    @Transactional
    public void createPendingPoint(CreatePendingPointCommand command) {
        executeWithLock(command.userId(), () -> {
            PointHistory history = pointDomainService.createPendingEarnHistory(
                UserId.of(command.userId()),
                OrderId.of(command.orderId()),
                SagaId.of(command.sagaId()),
                Point.of(command.amount())
            );
            pointHistoryRepository.save(history);
        });
    }

    // 주문 시 사용 포인트 대기 이력 생성
    @Transactional
    public void usePoints(UsePointCommand command) {
        executeWithLock(command.userId(), () -> {
            PointHistory history = pointDomainService.createPendingUseHistory(
                UserId.of(command.userId()),
                OrderId.of(command.orderId()),
                SagaId.of(command.sagaId()),
                Point.of(command.amount())
            );
            pointHistoryRepository.save(history);
        });
    }

    // 주문 취소 시 해당 주문의 모든 대기 포인트 이력 취소 처리
    @Transactional
    public void cancelOrder(CancelOrderCommand command) {
        executeWithLock(command.userId(), () -> {
            List<PointHistory> cancelHistories =
                pointDomainService.cancelHistoriesForOrder(
                    UserId.of(command.userId()),
                    OrderId.of(command.orderId()),
                    SagaId.of(command.sagaId())
                );
            pointHistoryRepository.saveAll(cancelHistories);
        });
    }

    // 배치: 7일 경과한 모든 예비 적립 포인트를 확정 처리
    @Transactional
    public void confirmExpiredPendingPoints() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(CONFIRM_PERIOD_DAYS);

        while (true) {
            int processed = confirmChunkPerUser(threshold);
            if (processed == 0) break;
        }
    }

    // 배치 청크 처리: 동일 유저 기준 순차 확정 처리
    protected int confirmChunkPerUser(LocalDateTime threshold) {
        List<PointHistory> pendingList =
            pointHistoryQueryRepository.findPendingHistories(
                PointType.EARN_PENDING,
                threshold,
                1000
            );

        if (pendingList.isEmpty()) return 0;

        LocalDateTime confirmTime = LocalDateTime.now();
        long sequence = 1000;

        Long userId = pendingList.getFirst().getUserId().getId();
        Point currentBalance = getCurrentBalance(userId);

        List<PointHistory> newConfirmHistories = new ArrayList<>();
        List<UUID> processedIds = new ArrayList<>();

        for (PointHistory pending : pendingList) {
            currentBalance = currentBalance.add(pending.getAmount().getAmount());
            LocalDateTime itemTime = confirmTime.plusNanos(sequence);

            PointHistory confirmHistory = PointHistory.createEarnConfirm(
                pending.getUserId(),
                pending.getOrderId(),
                pending.getAmount(),
                currentBalance,
                itemTime,
                pending.getSagaId()
            );

            newConfirmHistories.add(confirmHistory);
            processedIds.add(pending.getId());
            sequence += 1000;
        }


        pointHistoryRepository.saveAll(newConfirmHistories);

        pointHistoryQueryRepository.bulkUpdateToConfirmed(processedIds, confirmTime);

        entityManager.clear();

        return newConfirmHistories.size();
    }

    private Point getCurrentBalance(Long userId) {
        return pointHistoryQueryRepository
            .findLatestByUserId(userId)
            .map(PointHistory::getBalanceAfter)
            .orElse(Point.of(0L));
    }

    // 분산락 보일러플레이트: 유저별 포인트 변경 작업 래핑
    private void executeWithLock(
        Long userId,
        Runnable businessLogic
    ) {
        String lockKey = "point:lock:" + userId;
        RLock lock = redissonClient.getLock(lockKey);

        try {

            if (!lock.tryLock(10, 5, TimeUnit.SECONDS)) {
                throw new BusinessException(PointErrorCode.LOCK_ACQUISITION_FAILED);
            }

            businessLogic.run();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("서버 인터럽트");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

	/**
	 * 주문 취소 시 USE_PENDING 포인트 환불 처리
	 *
	 * @param command 환불 요청 정보
	 */
	@Transactional
	public void refundUsedPoints(RefundPointCommand command) {
		executeWithLock(command.userId(), () -> {
			// 1. 해당 주문의 USE_PENDING 이력 조회
			List<PointHistory> usePendingHistories =
				pointHistoryQueryRepository.findByOrderIdAndType(
					command.orderId(),
					PointType.USE_PENDING
				);

			if (usePendingHistories.isEmpty()) {
				log.warn("환불할 USE_PENDING 포인트 없음: orderId={}", command.orderId());
				return;
			}

			// 2. 현재 잔액 조회
			Point currentBalance = getCurrentBalance(command.userId());

			// 3. 각 USE_PENDING에 대해 USE_CANCEL 이력 생성
			List<PointHistory> refundHistories = new ArrayList<>();

			for (PointHistory usePending : usePendingHistories) {
				// USE_PENDING의 amount는 음수이므로, 절댓값을 취해 양수로 변환
				long refundAmount = Math.abs(usePending.getAmount().getAmount());

				// 잔액 증가
				currentBalance = currentBalance.add(refundAmount);

				// USE_CANCEL 이력 생성 (환불 = 사용 취소)
				PointHistory refund = PointHistory.createRefundConfirm(
					UserId.of(command.userId()),
					OrderId.of(command.orderId()),
					Point.of(refundAmount),  // 양수 값
					currentBalance,
					LocalDateTime.now(),
					SagaId.of(command.sagaId())
				);

				refundHistories.add(refund);
			}

			// 4. USE_CANCEL 이력 저장
			pointHistoryRepository.saveAll(refundHistories);

			log.info("포인트 환불 완료: orderId={}, refundCount={}, totalAmount={}",
				command.orderId(),
				refundHistories.size(),
				refundHistories.stream()
					.map(h -> h.getAmount().getAmount())
					.reduce(0L, Long::sum)
			);
		});
	}

}
